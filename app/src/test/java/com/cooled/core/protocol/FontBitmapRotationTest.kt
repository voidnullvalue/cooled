package com.cooled.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class FontBitmapRotationTest {
    @Test
    fun rotate90ClockwiseOnEightByEightArrowMatchesHandComputedGrid() {
        // Column-major, MSB-first 8x8 bitmap of an arrow pointing up:
        // column x has bit y set (row y, from the top) per this picture:
        //   ..#.....
        //   .###....
        //   #.#.#...
        //   ..#.....
        //   ..#.....
        //   ..#.....
        //   ..#.....
        //   ........
        // i.e. rows (top to bottom) as bit patterns, MSB = leftmost column.
        val rows = listOf(
            "00100000",
            "01110000",
            "10101000",
            "00100000",
            "00100000",
            "00100000",
            "00100000",
            "00000000"
        )
        val source = packColumnMajor(rows)

        val rotated = FontBitmapRotation.rotate90Clockwise(source, 8)
        val rotatedRows = unpackColumnMajor(rotated, 8)

        // Rotating the "up" arrow 90 degrees clockwise should point it right.
        // Cross-checked with an independent Python re-implementation of the
        // same unpack/rotate/repack arithmetic recovered from the smali.
        val expected = listOf(
            "00000100",
            "00000010",
            "01111111",
            "00000010",
            "00000100",
            "00000000",
            "00000000",
            "00000000"
        )
        assertEquals(expected, rotatedRows)
    }

    @Test
    fun rotate90FourTimesIsIdentityForNonSquareByteMultipleSizes() {
        // Unused padding bits in a partial last byte-per-column (size not a
        // multiple of 8) are always zeroed on output, so the fixture must be
        // built via pack/unpack (which never sets those bits) rather than
        // arbitrary byte values, or a full 360-degree round trip will not
        // reproduce garbage that was never meaningful in the first place.
        for (size in listOf(8, 12, 14, 16, 20, 24, 32)) {
            val rows = (0 until size).map { row ->
                (0 until size).joinToString("") { col -> if ((row * 31 + col * 17) % 5 == 0) "1" else "0" }
            }
            val source = packColumnMajor(rows)
            var current = source
            repeat(4) { current = FontBitmapRotation.rotate90Clockwise(current, size) }
            assertArrayEquals("size=$size", source, current)
        }
    }

    @Test
    fun rotateAngleWrapperMatchesRepeatedRotate90() {
        val size = 16
        val bytesPerColumn = (size + 7) / 8
        val source = ByteArray(size * bytesPerColumn) { (it * 13 + 3).toByte() }

        assertArrayEquals(source, FontBitmapRotation.rotate(0, source, size))
        assertArrayEquals(FontBitmapRotation.rotate90Clockwise(source, size), FontBitmapRotation.rotate(90, source, size))
        assertArrayEquals(
            FontBitmapRotation.rotate90Clockwise(FontBitmapRotation.rotate90Clockwise(source, size), size),
            FontBitmapRotation.rotate(180, source, size)
        )
    }

    private fun packColumnMajor(rowsTopToBottom: List<String>): ByteArray {
        val size = rowsTopToBottom.size
        val bytesPerColumn = (size + 7) / 8
        val out = ByteArray(size * bytesPerColumn)
        for (col in 0 until size) {
            for (byteIndex in 0 until bytesPerColumn) {
                val rowBase = byteIndex * 8
                val bitsInByte = minOf(8, size - rowBase)
                var value = 0
                for (bit in 0 until bitsInByte) {
                    val row = rowBase + bit
                    if (rowsTopToBottom[row][col] == '1') {
                        value = value or (0x80 ushr bit)
                    }
                }
                out[col * bytesPerColumn + byteIndex] = value.toByte()
            }
        }
        return out
    }

    private fun unpackColumnMajor(bytes: ByteArray, size: Int): List<String> {
        val bytesPerColumn = (size + 7) / 8
        val grid = Array(size) { CharArray(size) { '0' } }
        for (col in 0 until size) {
            for (byteIndex in 0 until bytesPerColumn) {
                val value = bytes[col * bytesPerColumn + byteIndex].toInt() and 0xFF
                val rowBase = byteIndex * 8
                val bitsInByte = minOf(8, size - rowBase)
                for (bit in 0 until bitsInByte) {
                    if (((value shl bit) and 0x80) == 0x80) {
                        grid[rowBase + bit][col] = '1'
                    }
                }
            }
        }
        return grid.map { String(it) }
    }

    private fun assertEquals(expected: List<String>, actual: List<String>) {
        org.junit.Assert.assertEquals(expected.joinToString("\n"), actual.joinToString("\n"))
    }
}
