package com.cooled.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FontCenteringTest {
    @Test
    fun padToSquareCenteredPutsOddLeftoverColumnOnTheRight() {
        val bytes = byteArrayOf(1, 2, 3) // 3 columns, 1 byte/col
        val padded = FontCentering.padToSquareCentered(bytes, squareSize = 8, bytesPerColumn = 1)
        // padTotal=5 -> left=2, right=3
        assertArrayEquals(byteArrayOf(0, 0, 1, 2, 3, 0, 0, 0), padded)
    }

    @Test
    fun padToSquareCenteredNoOpWhenAlreadyAtOrAboveSize() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        assertArrayEquals(bytes, FontCentering.padToSquareCentered(bytes, squareSize = 8, bytesPerColumn = 1))
    }

    @Test
    fun processBytesCenteredIsRotate90ThenRotate270ForAnAllOnesSquare() {
        // An all-1-bits square can never produce a blank column under any
        // rotation, so padding/trimming are guaranteed no-ops here and the
        // whole pipeline reduces to rotate90 followed by rotate270 - identity.
        val size = 8
        val bytesPerColumn = 1
        val source = ByteArray(size * bytesPerColumn) { 0xFF.toByte() }
        val result = FontCentering.processBytesCentered(source, size)
        assertArrayEquals(source, result)
    }

    @Test
    fun splitArrayChunksWithShortFinalChunk() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7)
        val chunks = FontCentering.splitArray(bytes, chunkSize = 3)
        assertEquals(3, chunks.size)
        assertArrayEquals(byteArrayOf(1, 2, 3), chunks[0])
        assertArrayEquals(byteArrayOf(4, 5, 6), chunks[1])
        assertArrayEquals(byteArrayOf(7), chunks[2])
    }

    @Test
    fun centerWithinRowReturnsUnchangedWhenExactMultipleOfRowWidth() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        assertArrayEquals(bytes, FontCentering.centerWithinRow(bytes, showWidth = 2, bytesPerColumn = 1))
    }

    @Test
    fun centerWithinRowShorterThanOneRowPutsOddLeftoverOnTheRight() {
        // showWidth=8, content=3 columns -> padTotal=5, left=2, right=3.
        val bytes = byteArrayOf(1, 2, 3)
        val result = FontCentering.centerWithinRow(bytes, showWidth = 8, bytesPerColumn = 1)
        assertArrayEquals(byteArrayOf(0, 0, 1, 2, 3, 0, 0, 0), result)
    }

    @Test
    fun centerWithinRowKeepsFullRowsAndCentersOnlyTrailingRemainderWithOddLeftoverOnTheLeft() {
        // showWidth=4, content=6 columns -> 1 full row (untouched) + 2-column remainder.
        // remainder padTotal=2 -> left=1, right=1 (even, no asymmetry to observe here directly)
        val bytes = byteArrayOf(1, 2, 3, 4, 5, 6)
        val result = FontCentering.centerWithinRow(bytes, showWidth = 4, bytesPerColumn = 1)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 0, 5, 6, 0), result)
    }

    @Test
    fun centerWithinRowRemainderOddLeftoverGoesLeftUnlikeTheShorterThanOneRowCase() {
        // showWidth=5, content=7 columns -> 1 full row (5) untouched + 2-column remainder.
        // remainder padTotal = 5-2 = 3 -> right=1, left=2 (odd extra goes LEFT here).
        val bytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7)
        val result = FontCentering.centerWithinRow(bytes, showWidth = 5, bytesPerColumn = 1)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 0, 0, 6, 7, 0), result)
    }

    @Test
    fun dealWithCenteredRowTrimsBlankColumnsBeforeCentering() {
        val row = byteArrayOf(0, 0, 9, 0, 0) // 1 real column surrounded by blanks, 5 total
        val result = FontCentering.dealWithCenteredRow(row, showWidth = 3, bytesPerColumn = 1)
        // trimmed to [9] (1 column), then centered in showWidth=3 -> padTotal=2, left=1, right=1
        assertArrayEquals(byteArrayOf(0, 9, 0), result)
    }

    @Test
    fun getCenteredDataBytesSplitsIntoRowsAndCentersEach() {
        // textSize=8 -> bytesPerColumn=1, showWidth=3.
        // canvas has 4 columns: one full row of 3, plus a 1-column remainder row.
        val canvas = byteArrayOf(1, 2, 3, 9)
        val result = FontCentering.getCenteredDataBytes(canvas, textSize = 8, showWidth = 3)
        // row1 [1,2,3] is an exact multiple of showWidth -> unchanged.
        // row2 [9] (1 col) centered in width 3 -> padTotal=2, left=1, right=1 -> [0,9,0]
        assertArrayEquals(byteArrayOf(1, 2, 3, 0, 9, 0), result)
    }
}
