package com.cooled.core.protocol

import com.cooled.core.compression.LzssCodec
import com.cooled.core.crc.CoolLedCrc
import com.cooled.core.model.DeviceFamily
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolCoreTest {
    @Test
    fun frameEncodeDecode_roundTrip() {
        val payload = byteArrayOf(0x1F, 0x01, 0x02, 0x03)
        val frame = FrameCodec.encode(payload)
        val out = FrameCodec.decode(frame)
        assertArrayEquals(payload, out)
    }

    @Test
    fun crc_isDeterministic() {
        val a = CoolLedCrc.crc32Like(byteArrayOf(1, 2, 3, 4))
        val b = CoolLedCrc.crc32Like(byteArrayOf(1, 2, 3, 4))
        assertEquals(a, b)
    }

    @Test
    fun splitChunks_works() {
        val data = ByteArray(2050) { it.toByte() }
        val chunks = CommandBuilders.splitChunks(data, 1024)
        assertEquals(3, chunks.size)
        assertEquals(1024, chunks[0].size)
        assertEquals(2, chunks[2].size)
    }

    @Test
    fun chunkPacket_hasMessageTypeAndXor() {
        val frame = CommandBuilders.buildDataChunk(0x03, 2000, 1, byteArrayOf(1, 2, 3))
        val payload = FrameCodec.decode(frame)
        assertEquals(0x03, payload[0].toInt() and 0xFF)
        var xor = 0
        for (i in 0 until payload.lastIndex) xor = xor xor (payload[i].toInt() and 0xFF)
        assertEquals(xor and 0xFF, payload.last().toInt() and 0xFF)
    }

    @Test
    fun parser_passwordAndDeviceInfoAndTransfer() {
        val p = ProtocolParsers.parseFrame(FrameCodec.encode(byteArrayOf(0x0D, 0x00)))
        assertTrue(p is ParsedPayload.PasswordCheckResult)

        val info = ProtocolParsers.parseFrame(
            FrameCodec.encode(
                byteArrayOf(
                    0x1F, 0x12, 0x01, 0x09, 0x10, 0x20, 0x04,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0x04, 0x00
                )
            )
        )
        assertTrue(info is ParsedPayload.DeviceInfo)
        assertEquals(1024, (info as ParsedPayload.DeviceInfo).packageSize)

        val transfer = ProtocolParsers.parseFrame(FrameCodec.encode(byteArrayOf(0x03, 0x00, 0x00, 0x02, 0x00)))
        assertTrue(transfer is ParsedPayload.TransferChunkResponse)
        assertEquals(2, (transfer as ParsedPayload.TransferChunkResponse).chunkIndex)
    }

    @Test
    fun parser_clockVectors() {
        val t = ProtocolParsers.parseFrame(FrameCodec.encode(byteArrayOf(0x19, 0x01, 0x01, 0x95.toByte(), 55)))
        assertTrue(t is ParsedPayload.TemperatureHumidity)

        val alarms = ProtocolParsers.parseFrame(
            FrameCodec.encode(
                byteArrayOf(
                    0x16, 0x02, 0x01,
                    0x01, 0x07, 0x30, 0x7E, 0x02, 0x58, 0x05
                )
            )
        )
        assertTrue(alarms is ParsedPayload.AlarmList)
        assertEquals(1, (alarms as ParsedPayload.AlarmList).alarms.size)

        val reminder = ProtocolParsers.parseFrame(FrameCodec.encode(byteArrayOf(0x1A, 0x01, 0x02, 0x03, 0x07)))
        assertTrue(reminder is ParsedPayload.ReminderList)
        assertEquals(listOf(3, 7), (reminder as ParsedPayload.ReminderList).ids)
    }

    @Test
    fun lzss_roundTrip() {
        val src = "HELLO HELLO HELLO COOLLED".encodeToByteArray()
        val compressed = LzssCodec.compress(src)
        val decompressed = LzssCodec.decompress(compressed)
        assertArrayEquals(src, decompressed)
    }

    @Test
    fun lzss_knownLiteralVector() {
        val compressed = byteArrayOf(0x07, 'A'.code.toByte(), 'B'.code.toByte(), 'C'.code.toByte())
        val decompressed = LzssCodec.decompress(compressed)
        assertArrayEquals("ABC".encodeToByteArray(), decompressed)
    }

    @Test
    fun familySpecificProgramHeader_hasTypedTrailer() {
        val req = ProgramStartRequest(compressed = ByteArray(20) { it.toByte() }, index = 1, count = 2, showCount = 3, programType = 14, extraTypeByte = 9)
        val frame = CommandBuilders.buildProgramStartHeader(DeviceFamily.ILEDCLOCK, req)
        val payload = FrameCodec.decode(frame)
        assertEquals(0x02, payload[0].toInt() and 0xFF)
        assertEquals(0x05, payload[payload.lastIndex - 1].toInt() and 0xFF)
        assertEquals(0x09, payload[payload.lastIndex].toInt() and 0xFF)
    }

    @Test
    fun transferStateMachine_transitions() {
        val sm = TransferStateMachine()
        sm.startSession(chunks = 2)
        sm.onParsed(ParsedPayload.TransferStartResponse(0x02, 0x00))
        assertTrue(sm.state.value is TransferState.SendingChunk)
        sm.onParsed(ParsedPayload.TransferChunkResponse(0x03, 0, 0x00))
        assertTrue(sm.state.value is TransferState.SendingChunk)
        sm.onParsed(ParsedPayload.TransferChunkResponse(0x03, 1, 0x00))
        assertTrue(sm.state.value is TransferState.Completed)
    }

    @Test
    fun passwordAndClockBuilders_emitExpectedOpcodes() {
        assertEquals(0x16, FrameCodec.decode(CommandBuilders.setAlarmList(listOf(AlarmCommand(true, 6, 30, 0x7F, 120, 2))))[0].toInt() and 0xFF)
        assertEquals(0x14, FrameCodec.decode(CommandBuilders.setNightMode(true, 22, 0, 6, 0))[0].toInt() and 0xFF)
        assertEquals(0x15, FrameCodec.decode(CommandBuilders.queryTomato())[0].toInt() and 0xFF)
        assertEquals(0x19, FrameCodec.decode(CommandBuilders.queryTemperatureHumidity())[0].toInt() and 0xFF)
    }
}
