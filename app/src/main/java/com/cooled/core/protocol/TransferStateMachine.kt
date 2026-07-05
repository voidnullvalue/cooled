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

    // Traced the APK's checkCoolLEDMMessages ack dispatcher and its
    // EventAgent subscribers: a chunk-ack status of 1/2/3 posts
    // ResponseEvent(5)/(6)/(4) respectively, and no subscriber anywhere
    // resends the chunk for any of those codes - they are all terminal
    // failure signals. The original's only automatic-resend mechanism is a
    // ~5000ms no-ack-of-any-kind timeout (checkRetryTimesSendProgramData*),
    // which never inspects a NACK's status byte because a NACK never
    // reaches that path. This port used to treat status 0x01 as "retry the
    // same chunk," inventing a NACK-triggers-retry behavior the real
    // protocol doesn't have - only onTimeout() should ever trigger a retry.
    private fun onChunkAck(chunkIndex: Int, status: Int) {
        when (status) {
            0x00 -> {
                currentChunk = chunkIndex + 1
                chunkRetriesLeft = maxChunkRetries
                if (currentChunk >= chunkCount) _state.value = TransferState.Completed(skipped = false)
                else _state.value = TransferState.SendingChunk(currentChunk, chunkCount, chunkRetriesLeft)
            }

            0x01, 0x02, 0x03 -> _state.value = TransferState.Failed("Chunk $chunkIndex rejected status=$status")
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
