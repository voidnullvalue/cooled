package com.cooled.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ArabicDotMatrixTest {
    @Test
    fun bitmapToDotMatrixLitsPixelsDarkerThanThreshold() {
        // 2x2 grid: top-left black (lit, gray=0 < 128), top-right white (unlit,
        // gray=255), bottom-left gray=127 (lit, < 128), bottom-right gray=128 (unlit, not < 128).
        val grid = PixelGrid(2, 2) { x, y ->
            when (x to y) {
                0 to 0 -> 0xFF000000.toInt()
                1 to 0 -> 0xFFFFFFFF.toInt()
                0 to 1 -> 0xFF7F7F7F.toInt() // 127,127,127 -> gray 127
                else -> 0xFF808080.toInt() // 128,128,128 -> gray 128
            }
        }
        val matrix = ArabicDotMatrix.bitmapToDotMatrix(grid, threshold = 128)
        assertArrayEquals(byteArrayOf(1, 0), matrix[0])
        assertArrayEquals(byteArrayOf(1, 0), matrix[1])
    }

    @Test
    fun bitmapToDotMatrixViLitsAnyNonBlackPixelRegardlessOfThreshold() {
        val grid = PixelGrid(2, 1) { x, _ ->
            if (x == 0) 0xFF000000.toInt() else 0xFF010000.toInt() // pure black vs. barely non-black
        }
        val matrix = ArabicDotMatrix.bitmapToDotMatrixVi(grid)
        assertArrayEquals(byteArrayOf(0, 1), matrix[0])
    }

    @Test
    fun dotMatrixToBytesPacksColumnMajorMsbFirstWithCeilingBytesPerColumn() {
        // height=9 -> bytesPerColumn=2. Row 8 is the FIRST row of the second
        // byte-group (byteIndex=1, bit=0), which maps to that byte's MSB
        // (shift 7-bit), not its LSB - each byte restarts at bit7 for its own
        // 8-row window.
        val height = 9
        val width = 1
        val matrix = Array(height) { row -> ByteArray(width) { if (row == 0 || row == 8) 1 else 0 } }
        val bytes = ArabicDotMatrix.dotMatrixToBytes(matrix)
        assertArrayEquals(byteArrayOf(0b10000000.toByte(), 0b10000000.toByte()), bytes)
    }

    @Test
    fun rasterizePixelsDispatchesToTheViOrNormalSamplingRule() {
        val darkGrid = PixelGrid(1, 8) { _, y -> if (y == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val normal = ArabicDotMatrix.rasterizePixels(darkGrid, vietnamese = false, threshold = 128)
        assertArrayEquals(byteArrayOf(0b10000000.toByte()), normal)

        val viGrid = PixelGrid(1, 8) { _, y -> if (y == 0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
        val vi = ArabicDotMatrix.rasterizePixels(viGrid, vietnamese = true)
        assertArrayEquals(byteArrayOf(0b10000000.toByte()), vi)
    }
}
