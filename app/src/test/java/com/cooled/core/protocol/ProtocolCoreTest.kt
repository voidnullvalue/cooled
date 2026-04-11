package com.cooled.core.protocol

import com.cooled.core.crc.CoolLedCrc
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
    fun chunkPacket_hasMessageType() {
        val frame = CommandBuilders.buildDataChunk(0x03, 2000, 1, byteArrayOf(1, 2, 3))
        val payload = FrameCodec.decode(frame)
        assertEquals(0x03, payload[0].toInt() and 0xFF)
    }

    @Test
    fun parser_ack() {
        val frame = FrameCodec.encode(byteArrayOf(0x1F))
        val parsed = ProtocolParsers.parseFrame(frame)
        assertTrue(parsed is ParsedPayload.Ack)
    }
}
