package com.cooled.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CoolleduxStreamTextTest {
    @Test
    fun goldenVectorForTwoCharactersNoRotationWithSpacing() {
        // 16px (2 bytes/column), textSize == showHeight so no rescale, no
        // rotation (rotate() is an identity), 1-column inter-glyph spacing.
        val previous = CoolleduxFontSources.active
        try {
            CoolleduxFontSources.active = object : CoolleduxFontSource {
                override fun readGlyph32(codePoint: Int, bold: Boolean): ByteArray? = null
                override fun readGlyph16(codePoint: Int, bold: Boolean): ByteArray? = when (codePoint.toChar()) {
                    'A' -> byteArrayOf(0x11, 0x00, 0x22, 0x00, 0x33, 0x00) // 3 columns, no blank column
                    'B' -> byteArrayOf(0x44, 0x00, 0x55, 0x00) // 2 columns
                    else -> null
                }
                override fun readGlyph8(codePoint: Int): ByteArray? = null
            }

            val encoded = CoolleduxStreamText.encode(
                CoolLedUxTextContentProgramContent(
                    text = "AB",
                    showWidth = 64,
                    showHeight = 16,
                    mode = 2,
                    textSize = 16,
                    textSpacing = 1,
                    textRotate = 0
                )
            )

            val expected = byteArrayOf(
                0x00, 0x02, // 2 tokens (including none-spaces here, just A and B)
                0x00, 0x00, 0x00, 0x06, // running total columns = 4 (A+spacing) + 2 (B) = 6
                0x04, 0x00, 0x11, 0x00, 0x22, 0x00, 0x33, 0x00, 0x00, 0x00, // A: 4 cols (3 + 1 spacing), type 0
                0x02, 0x00, 0x44, 0x00, 0x55, 0x00 // B: 2 cols, type 0, no trailing spacing (last token)
            )
            assertArrayEquals(expected, encoded)
        } finally {
            CoolleduxFontSources.active = previous
        }
    }

    @Test
    fun tokenCountHeaderIncludesSpaces() {
        val previous = CoolleduxFontSources.active
        try {
            CoolleduxFontSources.active = object : CoolleduxFontSource {
                override fun readGlyph32(codePoint: Int, bold: Boolean): ByteArray? = null
                override fun readGlyph16(codePoint: Int, bold: Boolean): ByteArray? = byteArrayOf(0x01, 0x00)
                override fun readGlyph8(codePoint: Int): ByteArray? = null
            }
            val encoded = CoolleduxStreamText.encode(
                CoolLedUxTextContentProgramContent(text = "A B", showHeight = 16, textSize = 16, textSpacing = 0)
            )
            // "A B" is 3 tokens (A, space, B) - the 2-byte header counts all of them, spaces included.
            assertArrayEquals(byteArrayOf(0x00, 0x03), encoded.copyOfRange(0, 2))
        } finally {
            CoolleduxFontSources.active = previous
        }
    }

    @Test
    fun missingGlyphFallsBackToZeroFilledBytesLikeTheApkInsteadOfThrowing() {
        val previous = CoolleduxFontSources.active
        try {
            CoolleduxFontSources.active = MissingCoolleduxFontSource
            val encoded = CoolleduxStreamText.encode(
                CoolLedUxTextContentProgramContent(text = "A", showHeight = 32, textSize = 32, textSpacing = 0)
            )
            // A single blank 32px glyph: deleteEmptyColumns of an all-zero
            // 128-byte glyph falls back to textSize/2 = 16 blank columns (64 bytes).
            assertArrayEquals(byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x00, 0x10), encoded.copyOfRange(0, 6))
            assertArrayEquals(byteArrayOf(0x10, 0x00), encoded.copyOfRange(6, 8))
            assertArrayEquals(ByteArray(64), encoded.copyOfRange(8, 72))
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
                CoolleduxStreamText.encode(
                    CoolLedUxTextContentProgramContent(text = "AB emoji_fc_007 CD", showHeight = 16, textSize = 16)
                )
            }
        } finally {
            CoolleduxFontSources.active = previous
        }
    }
}
