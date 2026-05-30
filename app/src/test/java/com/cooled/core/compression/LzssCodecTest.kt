package com.cooled.core.compression

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LzssCodecTest {
    @Test
    fun emptyInputRoundTrips() {
        assertArrayEquals(byteArrayOf(), LzssCodec.compress(byteArrayOf()))
        assertArrayEquals(byteArrayOf(), LzssCodec.decompress(byteArrayOf()))
    }

    @Test
    fun literalHeavyPayloadRoundTrips() {
        val input = ByteArray(257) { ((it * 37 + 11) and 0xFF).toByte() }

        val compressed = LzssCodec.compress(input)
        val decompressed = LzssCodec.decompress(compressed)

        assertArrayEquals(input, decompressed)
    }

    @Test
    fun repeatedPayloadRoundTripsAndCompresses() {
        val input = "HELLO HELLO HELLO HELLO HELLO HELLO HELLO".encodeToByteArray()

        val compressed = LzssCodec.compress(input)
        val decompressed = LzssCodec.decompress(compressed)

        assertArrayEquals(input, decompressed)
        assertTrue("expected repeated payload to compress", compressed.size < input.size)
    }

    @Test
    fun longProgramLikePayloadRoundTrips() {
        val input = ByteArray(4096) { idx ->
            when {
                idx % 97 == 0 -> 0x7E.toByte()
                idx % 31 == 0 -> 0x03.toByte()
                idx % 7 == 0 -> 0x00.toByte()
                else -> (idx and 0x3F).toByte()
            }
        }

        val compressed = LzssCodec.compress(input)
        val decompressed = LzssCodec.decompress(compressed)

        assertArrayEquals(input, decompressed)
    }

    @Test
    fun truncatedBackReferenceDoesNotThrow() {
        val malformed = byteArrayOf(0x00, 0x10)

        val decompressed = LzssCodec.decompress(malformed)

        assertTrue(decompressed.isEmpty())
    }
}
