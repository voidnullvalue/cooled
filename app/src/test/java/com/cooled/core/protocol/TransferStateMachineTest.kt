package com.cooled.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferStateMachineTest {
    @Test
    fun startAckMovesToFirstChunk() {
        val machine = TransferStateMachine(maxChunkRetries = 3, maxStartRetries = 3)

        machine.startSession(2)
        machine.onParsed(ParsedPayload.TransferStartResponse(opcode = 0x02, status = 0x00))

        assertEquals(TransferState.SendingChunk(index = 0, total = 2, retriesLeft = 3), machine.state.value)
    }

    @Test
    fun successfulChunksCompleteSession() {
        val machine = TransferStateMachine(maxChunkRetries = 3, maxStartRetries = 3)

        machine.startSession(2)
        machine.onParsed(ParsedPayload.TransferStartResponse(opcode = 0x02, status = 0x00))
        machine.onParsed(ParsedPayload.TransferChunkResponse(opcode = 0x03, chunkIndex = 0, status = 0x00))
        assertEquals(TransferState.SendingChunk(index = 1, total = 2, retriesLeft = 3), machine.state.value)
        machine.onParsed(ParsedPayload.TransferChunkResponse(opcode = 0x03, chunkIndex = 1, status = 0x00))

        assertEquals(TransferState.Completed(skipped = false), machine.state.value)
    }

    @Test
    fun startSkipStatusCompletesAsSkipped() {
        val machine = TransferStateMachine(maxChunkRetries = 3, maxStartRetries = 3)

        machine.startSession(4)
        machine.onParsed(ParsedPayload.TransferStartResponse(opcode = 0x02, status = 0x01))

        assertEquals(TransferState.Completed(skipped = true), machine.state.value)
    }

    @Test
    fun chunkNackIsTerminalNotARetryTrigger() {
        // Traced the APK's ack dispatcher: a chunk NACK (status 1) is a
        // dead-end failure signal with no subscriber anywhere that resends
        // the chunk. This port used to treat a NACK as a retry trigger,
        // inventing behavior the real protocol doesn't have - only a
        // no-ack-at-all timeout (onTimeout()) legitimately retries.
        val machine = TransferStateMachine(maxChunkRetries = 3, maxStartRetries = 3)

        machine.startSession(1)
        machine.onParsed(ParsedPayload.TransferStartResponse(opcode = 0x02, status = 0x00))
        machine.onParsed(ParsedPayload.TransferChunkResponse(opcode = 0x03, chunkIndex = 0, status = 0x01))

        val failed = machine.state.value
        assertTrue(failed is TransferState.Failed)
        assertEquals("Chunk 0 rejected status=1", (failed as TransferState.Failed).reason)
    }

    @Test
    fun chunkTimeoutRetriesThenEventuallyFails() {
        val machine = TransferStateMachine(maxChunkRetries = 1, maxStartRetries = 3)

        machine.startSession(1)
        machine.onParsed(ParsedPayload.TransferStartResponse(opcode = 0x02, status = 0x00))
        machine.onTimeout()
        assertEquals(TransferState.SendingChunk(index = 0, total = 1, retriesLeft = 0), machine.state.value)
        machine.onTimeout()

        val failed = machine.state.value
        assertTrue(failed is TransferState.Failed)
        assertEquals("Chunk 0 timeout", (failed as TransferState.Failed).reason)
    }

    @Test
    fun timeoutExhaustionFails() {
        val machine = TransferStateMachine(maxChunkRetries = 3, maxStartRetries = 0)

        machine.startSession(1)
        machine.onTimeout()

        val failed = machine.state.value
        assertTrue(failed is TransferState.Failed)
        assertEquals("Start ack timeout", (failed as TransferState.Failed).reason)
    }
}
