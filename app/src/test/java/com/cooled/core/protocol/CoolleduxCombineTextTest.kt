package com.cooled.core.protocol

import com.cooled.core.assets.OriginalLedAssetBytes
import com.cooled.core.assets.OriginalLedAssetByteSources
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
    fun goldenVectorForASingleImageTokenNeedingRowCentering() {
        // "emoji_fc_007" tokenizes as one whole image token (see
        // CoolleduxStreamTextTest's equivalent test for why). Fake decoder
        // returns a 16x16 grid, opaque black except column 5 row 0 (pure
        // red) - the same fixture as the stream-mode golden vector, so the
        // shaped glyph's monochrome trims down to a single column [0x80,0x00]
        // and its RGB444 payload to [0x0F,0x00] + 15*[0x00,0x00].
        //
        // showWidth=16 with only one item on the canvas: checkSegment just
        // returns that single column verbatim (canvas was null), but
        // getCenteredDataBytes then centers this 1-column "row" within
        // showWidth=16: padTotal=15, left=7 (floor), right=8 (odd extra
        // goes right per FontCentering's row-centering bias) - so the
        // realignment search finds the glyph at column offset 7 and pads
        // both representations to match: 7 leading + 1 real + 8 trailing =
        // 16 columns total.
        val red = 0xFFFF0000.toInt()
        val black = 0xFF000000.toInt()
        val previousBytes = OriginalLedAssetByteSources.active
        val previousDecoder = PixelGridDecoders.active
        try {
            OriginalLedAssetByteSources.active = object : OriginalLedAssetBytes {
                override fun read(path: String): ByteArray? =
                    if (path.contains("emoji_fc_16x16_7")) byteArrayOf(1, 2, 3) else null
            }
            PixelGridDecoders.active = object : PixelGridDecoder {
                override fun decode(bytes: ByteArray): PixelGrid = PixelGrid(16, 16) { x, y ->
                    if (x == 5 && y == 0) red else black
                }
            }

            val encoded = CoolleduxCombineText.encode(
                CoolLedUxTextContentProgramContent(
                    text = "emoji_fc_007",
                    showWidth = 16,
                    showHeight = 16,
                    mode = 1,
                    textSize = 16,
                    textSpacing = 1,
                    textRotate = 0
                )
            )

            val blackColumnRgb444 = ByteArray(32) // 16 rows * 2 bytes, all zero (opaque black -> 0,0,0 channels)
            val realColumnRgb444 = byteArrayOf(0x0F, 0x00) + ByteArray(30) // row0 red, rows1-15 opaque black
            val expectedPayload = mutableListOf<Byte>()
            repeat(7) { expectedPayload += blackColumnRgb444.toList() }
            expectedPayload += realColumnRgb444.toList()
            repeat(8) { expectedPayload += blackColumnRgb444.toList() }

            val expected = byteArrayOf(
                0x00, 0x01, // 1 non-space glyph placed
                0x00, 0x00, 0x00, 0x10, // running total columns = 16 (full centered row)
                0x10, 0x01 // colCount=16, itemType=1 (image)
            ) + expectedPayload.toByteArray()
            assertArrayEquals(expected, encoded)
        } finally {
            OriginalLedAssetByteSources.active = previousBytes
            PixelGridDecoders.active = previousDecoder
        }
    }

    @Test
    fun missingEmojiAssetThrowsClearlyInsteadOfSilentlySkipping() {
        val previousBytes = OriginalLedAssetByteSources.active
        val previousDecoder = PixelGridDecoders.active
        try {
            OriginalLedAssetByteSources.active = object : OriginalLedAssetBytes {
                override fun read(path: String): ByteArray? = null
            }
            PixelGridDecoders.active = UnavailablePixelGridDecoder
            assertThrows(IllegalStateException::class.java) {
                CoolleduxCombineText.encode(
                    CoolLedUxTextContentProgramContent(text = "emoji_fc_007", showHeight = 16, textSize = 16, mode = 1)
                )
            }
        } finally {
            OriginalLedAssetByteSources.active = previousBytes
            PixelGridDecoders.active = previousDecoder
        }
    }
}
