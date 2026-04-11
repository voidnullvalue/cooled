package com.cooled.core.protocol

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class TransferState {
    data object Idle : TransferState()
    data class AwaitingStartAck(val retriesLeft: Int) : TransferState()
    data class SendingChunk(val index: Int, val total: Int, val retriesLeft: Int) : TransferState()
    data class Completed(val skipped: Boolean) : TransferState()
    data class Failed(val reason: String) : TransferState()
    data object Cancelled : TransferState()
}

class TransferStateMachine(
    private val maxChunkRetries: Int = 3,
    private val maxStartRetries: Int = 3
) {
    private val _state = MutableStateFlow<TransferState>(TransferState.Idle)
    val state: StateFlow<TransferState> = _state.asStateFlow()

    private var chunkCount: Int = 0
    private var currentChunk: Int = 0
    private var startRetriesLeft: Int = maxStartRetries
    private var chunkRetriesLeft: Int = maxChunkRetries

    fun startSession(chunks: Int) {
        chunkCount = chunks
        currentChunk = 0
        startRetriesLeft = maxStartRetries
        chunkRetriesLeft = maxChunkRetries
        _state.value = TransferState.AwaitingStartAck(startRetriesLeft)
    }

    fun cancel() {
        reset()
        _state.value = TransferState.Cancelled
    }

    fun onTimeout() {
        when (val s = _state.value) {
            is TransferState.AwaitingStartAck -> {
                startRetriesLeft--
                if (startRetriesLeft < 0) _state.value = TransferState.Failed("Start ack timeout")
                else _state.value = TransferState.AwaitingStartAck(startRetriesLeft)
            }

            is TransferState.SendingChunk -> {
                chunkRetriesLeft--
                if (chunkRetriesLeft < 0) _state.value = TransferState.Failed("Chunk ${s.index} timeout")
                else _state.value = TransferState.SendingChunk(s.index, s.total, chunkRetriesLeft)
            }

            else -> Unit
        }
    }

    fun onParsed(payload: ParsedPayload) {
        when (payload) {
            is ParsedPayload.TransferStartResponse -> onStartAck(payload.status)
            is ParsedPayload.TransferChunkResponse -> onChunkAck(payload.chunkIndex, payload.status)
            else -> Unit
        }
    }

    private fun onStartAck(status: Int) {
        when (status) {
            0x00 -> {
                if (chunkCount == 0) _state.value = TransferState.Completed(skipped = false)
                else _state.value = TransferState.SendingChunk(0, chunkCount, maxChunkRetries)
            }

            0x01 -> _state.value = TransferState.Completed(skipped = true)
            0x02, 0x03 -> _state.value = TransferState.Failed("Start rejected status=$status")
            else -> _state.value = TransferState.Failed("Start unknown status=$status")
        }
    }

    private fun onChunkAck(chunkIndex: Int, status: Int) {
        when (status) {
            0x00 -> {
                currentChunk = chunkIndex + 1
                chunkRetriesLeft = maxChunkRetries
                if (currentChunk >= chunkCount) _state.value = TransferState.Completed(skipped = false)
                else _state.value = TransferState.SendingChunk(currentChunk, chunkCount, chunkRetriesLeft)
            }

            0x01 -> {
                chunkRetriesLeft--
                if (chunkRetriesLeft < 0) _state.value = TransferState.Failed("Chunk $chunkIndex retry exhausted")
                else _state.value = TransferState.SendingChunk(chunkIndex, chunkCount, chunkRetriesLeft)
            }

            0x02, 0x03 -> _state.value = TransferState.Failed("Chunk $chunkIndex rejected status=$status")
            else -> _state.value = TransferState.Failed("Chunk $chunkIndex unknown status=$status")
        }
    }

    private fun reset() {
        chunkCount = 0
        currentChunk = 0
        startRetriesLeft = maxStartRetries
        chunkRetriesLeft = maxChunkRetries
    }
}
