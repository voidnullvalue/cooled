package com.cooled.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class FontCanvasWordWrapTest {
    @Test
    fun firstGlyphBecomesTheCanvasVerbatim() {
        val glyph = byteArrayOf(1, 2, 3)
        assertArrayEquals(glyph, FontCanvasWordWrap.checkSegment(null, glyph, showWidth = 16, textSpacing = 1, bytesPerColumn = 1))
    }

    @Test
    fun exactRowBoundaryAppendsWithNoGap() {
        // 8px (1 byte/col): canvas already has exactly showWidth=4 columns -> posInRow == 0.
        val canvas = byteArrayOf(1, 2, 3, 4)
        val glyph = byteArrayOf(9, 9)
        val result = FontCanvasWordWrap.checkSegment(canvas, glyph, showWidth = 4, textSpacing = 2, bytesPerColumn = 1)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 9, 9), result)
    }

    @Test
    fun glyphFittingInRemainingRowGetsExactlyTextSpacingGap() {
        // showWidth=8, canvas has 3 columns used (posInRow=3), remaining=5.
        // glyph has 2 columns + textSpacing(1) = 3 <= remaining(5) -> fits, insert exactly textSpacing blank columns.
        val canvas = byteArrayOf(1, 2, 3)
        val glyph = byteArrayOf(9, 9)
        val result = FontCanvasWordWrap.checkSegment(canvas, glyph, showWidth = 8, textSpacing = 1, bytesPerColumn = 1)
        assertArrayEquals(byteArrayOf(1, 2, 3, 0, 9, 9), result)
    }

    @Test
    fun glyphNotFittingInRemainingRowPadsToNextRowBoundary() {
        // showWidth=8, canvas has 6 columns used (posInRow=6), remaining=2.
        // glyph has 3 columns + textSpacing(1) = 4 > remaining(2) -> doesn't fit, pad exactly `remaining` (2) blank columns.
        val canvas = byteArrayOf(1, 2, 3, 4, 5, 6)
        val glyph = byteArrayOf(9, 9, 9)
        val result = FontCanvasWordWrap.checkSegment(canvas, glyph, showWidth = 8, textSpacing = 1, bytesPerColumn = 1)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 0, 0, 9, 9, 9), result)
    }

    @Test
    fun multiByteColumnsForThirtyTwoPixelGlyphsWrapOnColumnCountNotByteCount() {
        // 32px (4 bytes/col): showWidth=4 columns, canvas has 3 columns (12 bytes) -> posInRow=3, remaining=1.
        // glyph is 1 column (4 bytes) + textSpacing(0) = 1 <= remaining(1) -> fits, 0 spacing columns inserted.
        val canvas = ByteArray(12) { (it + 1).toByte() }
        val glyph = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        val result = FontCanvasWordWrap.checkSegment(canvas, glyph, showWidth = 4, textSpacing = 0, bytesPerColumn = 4)
        assertArrayEquals(canvas + glyph, result)
    }

    @Test
    fun addEmptyColumnsAppendsZeroBytesPerColumn() {
        assertArrayEquals(byteArrayOf(1, 2, 0, 0, 0, 0), FontCanvasWordWrap.addEmptyColumns(byteArrayOf(1, 2), count = 2, bytesPerColumn = 2))
        assertArrayEquals(byteArrayOf(0, 0, 1, 2), FontCanvasWordWrap.addEmptyColumnsToTheLeft(byteArrayOf(1, 2), count = 1, bytesPerColumn = 2))
    }
}
