package com.cooled.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class FontColumnTrimmingTest {
    @Test
    fun trimsLeadingAndTrailingBlankColumnsButKeepsInteriorGaps() {
        // 8px (1 byte/column): blank, blank, 0x01, blank(interior), 0x02, blank, blank
        val source = byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x02, 0x00, 0x00)
        val trimmed = FontColumnTrimming.deleteEmptyColumns(source, textSize = 8)
        assertArrayEquals(byteArrayOf(0x01, 0x00, 0x02), trimmed)
    }

    @Test
    fun entirelyBlankGlyphReturnsTextSizeOverTwoBlankColumns() {
        for (textSize in listOf(8, 12, 14, 16, 20, 24, 32)) {
            val bytesPerColumn = (textSize + 7) / 8
            val source = ByteArray(bytesPerColumn * 5) // 5 blank columns of source
            val trimmed = FontColumnTrimming.deleteEmptyColumns(source, textSize)
            assertArrayEquals("textSize=$textSize", ByteArray((textSize / 2) * bytesPerColumn), trimmed)
        }
    }

    @Test
    fun singleNonBlankColumnSurvives() {
        val source = byteArrayOf(0x00, 0x00, 0x05, 0x06, 0x00, 0x00) // 16px, 2 bytes/col, 3 cols
        val trimmed = FontColumnTrimming.deleteEmptyColumns(source, textSize = 16)
        assertArrayEquals(byteArrayOf(0x05, 0x06), trimmed)
    }

    @Test
    fun multiByteColumnsForThirtyTwoPixelGlyphs() {
        // 32px (4 bytes/column): blank column, then a non-blank column, blank column
        val source = byteArrayOf(0, 0, 0, 0, 0x11, 0x22, 0x33, 0x44, 0, 0, 0, 0)
        val trimmed = FontColumnTrimming.deleteEmptyColumns(source, textSize = 32)
        assertArrayEquals(byteArrayOf(0x11, 0x22, 0x33, 0x44), trimmed)
    }

    @Test
    fun noBlankColumnsReturnsInputUnchanged() {
        val source = byteArrayOf(0x01, 0x02, 0x03)
        val trimmed = FontColumnTrimming.deleteEmptyColumns(source, textSize = 8)
        assertArrayEquals(source, trimmed)
    }
}
