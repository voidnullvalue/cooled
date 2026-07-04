package com.cooled.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CoolleduxCombineTextTest {
    @Test
    fun goldenVectorForTwoCharactersFillingExactlyOneRow() {
        // 8px (1 byte/column), textSize == showHeight so no rescale, no
        // rotation. showWidth=6 with textSpacing=1: "A" (3 cols) + 1 spacing
        // column + "B" (2 cols) = exactly 6 columns = one full canvas row,
        // so getCenteredDataBytes needs no centering here (hand-traced
        // separately against reverse/apktool/.../FontUtils.smali; see
        // CoolleduxCombineText's class doc).
        val previous = CoolleduxFontSources.active
        try {
            CoolleduxFontSources.active = object : CoolleduxFontSource {
                override fun readGlyph32(codePoint: Int, bold: Boolean): ByteArray? = null
                override fun readGlyph8(codePoint: Int): ByteArray? = when (codePoint.toChar()) {
                    'A' -> byteArrayOf(0x11, 0x22, 0x33)
                    'B' -> byteArrayOf(0x44, 0x55)
                    else -> null
                }
            }

            val encoded = CoolleduxCombineText.encode(
                CoolLedUxTextContentProgramContent(
                    text = "AB",
                    showWidth = 6,
                    showHeight = 8,
                    mode = 1,
                    textSize = 8,
                    textSpacing = 1,
                    textRotate = 0
                )
            )

            val expected = byteArrayOf(
                0x00, 0x02, // 2 non-space glyphs placed
                0x00, 0x00, 0x00, 0x06, // running total columns = 4 (A + 1 trailing gap) + 2 (B) = 6
                0x04, 0x00, 0x11, 0x22, 0x33, 0x00, // A: 3 cols + 1 trailing gap column, type 0
                0x02, 0x00, 0x44, 0x55 // B: 2 cols, no gap (end of canvas)
            )
            assertArrayEquals(expected, encoded)
        } finally {
            CoolleduxFontSources.active = previous
        }
    }

    @Test
    fun reassemblingEmittedChunksReconstructsTheCenteredCanvasExactly() {
        // This is the structural invariant the whole realignment search is
        // built on: cursor -> leadingPad -> match -> trailingPad always
        // covers the canvas with no gaps or overlaps, so concatenating every
        // emitted (leadingPad+glyph+trailingPad) chunk in order must
        // reconstruct FontCentering.getCenteredDataBytes's output byte for
        // byte. Checked here with a case forcing word-wrap across two rows.
        val previous = CoolleduxFontSources.active
        try {
            CoolleduxFontSources.active = object : CoolleduxFontSource {
                override fun readGlyph32(codePoint: Int, bold: Boolean): ByteArray? = null
                override fun readGlyph8(codePoint: Int): ByteArray? = when (codePoint.toChar()) {
                    'A' -> byteArrayOf(0x11, 0x22, 0x33)
                    'B' -> byteArrayOf(0x44, 0x55)
                    'C' -> byteArrayOf(0x66)
                    else -> null
                }
            }
            val content = CoolLedUxTextContentProgramContent(
                text = "ABC",
                showWidth = 4,
                showHeight = 8,
                mode = 1,
                textSize = 8,
                textSpacing = 1,
                textRotate = 0
            )

            // Recompute the expected centered canvas independently, using
            // the same public primitives CoolleduxCombineText itself uses.
            var canvas: ByteArray? = null
            for (ch in content.text) {
                val glyph = when (ch) {
                    'A' -> byteArrayOf(0x11, 0x22, 0x33)
                    'B' -> byteArrayOf(0x44, 0x55)
                    'C' -> byteArrayOf(0x66)
                    else -> error("unexpected")
                }
                canvas = FontCanvasWordWrap.checkSegment(canvas, glyph, content.showWidth, content.textSpacing, 1)
            }
            val expectedCanvas = FontCentering.getCenteredDataBytes(canvas!!, content.showHeight, content.showWidth)

            val encoded = CoolleduxCombineText.encode(content)
            var pos = 6
            val reassembled = mutableListOf<Byte>()
            while (pos < encoded.size) {
                val colCount = encoded[pos].toInt() and 0xFF
                pos += 2
                repeat(colCount) { reassembled += encoded[pos + it] }
                pos += colCount
            }
            assertArrayEquals(expectedCanvas, reassembled.toByteArray())
        } finally {
            CoolleduxFontSources.active = previous
        }
    }

    @Test
    fun rejectsEmojiTokens() {
        val previous = CoolleduxFontSources.active
        try {
            CoolleduxFontSources.active = object : CoolleduxFontSource {
                override fun readGlyph32(codePoint: Int, bold: Boolean): ByteArray? = byteArrayOf(1)
                override fun readGlyph8(codePoint: Int): ByteArray? = byteArrayOf(1)
            }
            assertThrows(IllegalArgumentException::class.java) {
                CoolleduxCombineText.encode(
                    CoolLedUxTextContentProgramContent(text = "AB emoji_fc_007 CD", showHeight = 8, textSize = 8, mode = 1)
                )
            }
        } finally {
            CoolleduxFontSources.active = previous
        }
    }
}
