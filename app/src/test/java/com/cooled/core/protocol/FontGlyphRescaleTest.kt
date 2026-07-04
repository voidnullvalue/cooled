package com.cooled.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class FontGlyphRescaleTest {
    @Test
    fun sameByteStrideShiftsBitsAndPadsOneColumnEachSide() {
        // 14->16: same 2-bytes/column stride, delta=2 -> shift 1 bit, pad 1 column (2 bytes) each side.
        // Cross-checked with an independent Python re-implementation of the same
        // widen/shift/pad steps traced from the smali.
        val source = byteArrayOf(0xB4.toByte(), 0x66)
        val result = FontGlyphRescale.transfer(source, fromSize = 14, toSize = 16)
        assertArrayEquals(
            byteArrayOf(0x00, 0x00, 0x5A, 0x33, 0x00, 0x00),
            result
        )
    }

    @Test
    fun differentByteStrideWidensColumnThenShiftsAndPads() {
        // 24->32: 3-bytes/column widened to 4, delta=8 -> shift 4 bits, pad 4 columns (16 bytes) each side.
        val source = byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte())
        val result = FontGlyphRescale.transfer(source, fromSize = 24, toSize = 32)
        val expectedMiddle = byteArrayOf(0x0A, 0xBC.toByte(), 0xDE.toByte(), 0xF0.toByte())
        assertArrayEquals(ByteArray(16), result.copyOfRange(0, 16))
        assertArrayEquals(expectedMiddle, result.copyOfRange(16, 20))
        assertArrayEquals(ByteArray(16), result.copyOfRange(20, 36))
    }

    @Test
    fun noOpWhenSizesAreEqual() {
        val source = byteArrayOf(1, 2, 3, 4)
        assertArrayEquals(source, FontGlyphRescale.transfer(source, fromSize = 16, toSize = 16))
    }

    @Test
    fun preservesColumnCountModuloPadding() {
        // 3 columns of 12px (2 bytes/col) -> 16px (2 bytes/col): same column count, just widened by padding.
        val source = ByteArray(6) { (it + 1).toByte() }
        val result = FontGlyphRescale.transfer(source, fromSize = 12, toSize = 16)
        val bytesPerColumn = 2
        val padColumns = (16 - 12) / 2
        assertEquals(3 + 2 * padColumns, result.size / bytesPerColumn)
    }

    private fun assertEquals(expected: Int, actual: Int) = org.junit.Assert.assertEquals(expected, actual)
}
