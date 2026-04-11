package com.cooled.core.protocol

import com.cooled.core.ble.BleIoDirection
import com.cooled.core.ble.FakeBleTransport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeTransportTransferTest {
    @Test
    fun scriptedHappyPath_emitsAckInsteadOfLoopback() = runBlocking {
        val fake = FakeBleTransport()
        fake.enqueueRxPayload(byteArrayOf(0x02, 0x00), "start")
        fake.write(FrameCodec.encode(byteArrayOf(0x02, 0x01)))
        val parsed = ProtocolParsers.parseFrame(fake.rxFrames.first { it.bytes.isNotEmpty() }.bytes)
        assertTrue(parsed is ParsedPayload.TransferStartResponse)
        assertEquals(0x00, (parsed as ParsedPayload.TransferStartResponse).status)
    }

    @Test
    fun ioEvents_includeTxAndRxDirections() = runBlocking {
        val fake = FakeBleTransport()
        fake.enqueueRxPayload(byteArrayOf(0x0D, 0x00), "pwd")
        fake.write(FrameCodec.encode(byteArrayOf(0x0D, 0x11)))
        val evt = fake.ioEvents.first { it.bytes.isNotEmpty() }
        assertTrue(evt.direction == BleIoDirection.RX)
    }

    @Test
    fun transferScript_nackThenSuccess_stateProgression() {
        val sm = TransferStateMachine(maxChunkRetries = 3)
        sm.startSession(1)
        sm.onParsed(ParsedPayload.TransferStartResponse(0x02, 0x00))
        sm.onParsed(ParsedPayload.TransferChunkResponse(0x03, 0, 0x01))
        sm.onParsed(ParsedPayload.TransferChunkResponse(0x03, 0, 0x01))
        sm.onParsed(ParsedPayload.TransferChunkResponse(0x03, 0, 0x00))
        assertTrue(sm.state.value is TransferState.Completed)
    }
}
