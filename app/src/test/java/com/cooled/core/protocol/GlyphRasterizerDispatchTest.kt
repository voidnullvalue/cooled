package com.cooled.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the GlyphRasterizer wiring described in TokenGlyphShaper's class doc:
 * a single Arabic/Hebrew/Hindi/Thai character routes to
 * GlyphRasterizer.drawChar, a multi-character word-segment (as produced by
 * MultiLangTextTokenizer) routes to GlyphRasterizer.drawString, and anything
 * else keeps reading the plain font table - then that the resulting bytes
 * flow through the exact same rotate/trim/word-wrap/centering pipeline a
 * font-table glyph would (CoolleduxGlyphPipeline.shapeTail,
 * FontCanvasWordWrap, FontCentering), end to end through
 * CoolleduxStreamText/CoolleduxCombineText.
 *
 * This can only exercise the DISPATCH - the fake GlyphRasterizer below
 * returns fixed byte arrays instead of ever touching a real
 * android.graphics.Canvas, so it says nothing about whether
 * AndroidGlyphRasterizer's actual pixel output is correct (that needs
 * on-device / Robolectric verification, tracked as a separate gap - see
 * AndroidGlyphRasterizer's class doc and ArabicDotMatrixTest for the one
 * piece of that path that IS pure-JVM-testable). What it does prove is real:
 * that Arabic/Hebrew/Hindi/Thai text actually reaches GlyphRasterizers.active
 * instead of silently falling through to the font table (or vice versa), and
 * that whatever bytes come back are treated identically to a table-read
 * glyph by every downstream stage.
 */
class GlyphRasterizerDispatchTest {
    private class RecordingGlyphRasterizer(
        private val charBytes: Map<Char, ByteArray> = emptyMap(),
        private val stringBytes: Map<String, ByteArray> = emptyMap()
    ) : GlyphRasterizer {
        data class CharCall(val languageCode: String, val char: Char, val size: Int, val textSize: Int, val bold: Boolean)
        data class StringCall(val languageCode: String, val text: String, val width: Int, val height: Int, val textSize: Int, val bold: Boolean)

        val drawCharCalls = mutableListOf<CharCall>()
        val drawStringCalls = mutableListOf<StringCall>()

        override fun drawChar(languageCode: String, char: Char, size: Int, textSize: Int, bold: Boolean): ByteArray {
            drawCharCalls += CharCall(languageCode, char, size, textSize, bold)
            return charBytes[char] ?: error("RecordingGlyphRasterizer: no fake bytes registered for char '$char'")
        }

        override fun drawString(languageCode: String, text: String, width: Int, height: Int, textSize: Int, bold: Boolean): ByteArray {
            drawStringCalls += StringCall(languageCode, text, width, height, textSize, bold)
            return stringBytes[text] ?: error("RecordingGlyphRasterizer: no fake bytes registered for string '$text'")
        }
    }

    /** A font source that fails the test loudly if asked to read a codepoint it wasn't told to expect - proving the rasterizer path, not the font table, served a given character. */
    private class ExpectOnlyFontSource(private val byCodePoint: Map<Int, ByteArray>) : CoolleduxFontSource {
        override fun readGlyph32(codePoint: Int, bold: Boolean): ByteArray? = null
        override fun readGlyph16(codePoint: Int, bold: Boolean): ByteArray? =
            byCodePoint[codePoint] ?: error("unexpected font-table read for codepoint $codePoint ('${codePoint.toChar()}') - expected the GlyphRasterizer path")
        override fun readGlyph8(codePoint: Int): ByteArray? = null
    }

    private fun withFakes(
        rasterizer: GlyphRasterizer,
        fontSource: CoolleduxFontSource,
        block: () -> Unit
    ) {
        val previousRasterizer = GlyphRasterizers.active
        val previousFontSource = CoolleduxFontSources.active
        try {
            GlyphRasterizers.active = rasterizer
            CoolleduxFontSources.active = fontSource
            block()
        } finally {
            GlyphRasterizers.active = previousRasterizer
            CoolleduxFontSources.active = previousFontSource
        }
    }

    // ---- Unit-level: TokenGlyphShaper.shape dispatch ----

    @Test
    fun singleArabicCharacterTokenDispatchesToDrawChar() {
        val rasterizer = RecordingGlyphRasterizer(charBytes = mapOf('ء' to byteArrayOf(0x11, 0x00)))
        withFakes(rasterizer, ExpectOnlyFontSource(emptyMap())) {
            val content = CoolLedUxTextContentProgramContent(text = "ء", languageCode = "ar", showHeight = 16, textSize = 16)
            val shaped = TokenGlyphShaper.shape(TextEmojiTokenizer.Token(isText = true, text = "ء"), content)

            assertEquals(1, rasterizer.drawCharCalls.size)
            val call = rasterizer.drawCharCalls[0]
            assertEquals("ar", call.languageCode)
            assertEquals('ء', call.char)
            assertEquals(16, call.size) // size == showHeight
            assertEquals(16, call.textSize)
            assertFalse(call.bold)
            assertTrue(rasterizer.drawStringCalls.isEmpty())
            assertArrayEquals(byteArrayOf(0x11, 0x00), shaped.monochrome) // rotate(0)=identity, non-blank column survives trim
        }
    }

    @Test
    fun multiCharacterWordTokenDispatchesToDrawStringWithWidthEqualToLengthTimesShowHeight() {
        val word = "كتاب"
        val rasterizer = RecordingGlyphRasterizer(stringBytes = mapOf(word to byteArrayOf(0x11, 0x00, 0x22, 0x00)))
        withFakes(rasterizer, ExpectOnlyFontSource(emptyMap())) {
            val content = CoolLedUxTextContentProgramContent(text = word, languageCode = "ar", showHeight = 16, textSize = 16, isTextBold = true)
            val shaped = TokenGlyphShaper.shape(TextEmojiTokenizer.Token(isText = true, text = word), content)

            assertEquals(1, rasterizer.drawStringCalls.size)
            val call = rasterizer.drawStringCalls[0]
            assertEquals("ar", call.languageCode)
            assertEquals(word, call.text)
            assertEquals(word.length * 16, call.width) // FontUtils.java:11590-11592
            assertEquals(16, call.height)
            assertEquals(16, call.textSize)
            assertTrue(call.bold)
            assertTrue(rasterizer.drawCharCalls.isEmpty())
            assertArrayEquals(byteArrayOf(0x11, 0x00, 0x22, 0x00), shaped.monochrome)
        }
    }

    @Test
    fun nonScriptCharacterStillReadsThePlainFontTableNotTheRasterizer() {
        val rasterizer = RecordingGlyphRasterizer()
        withFakes(rasterizer, ExpectOnlyFontSource(mapOf('A'.code to byteArrayOf(0x33, 0x00)))) {
            val content = CoolLedUxTextContentProgramContent(text = "A", languageCode = "ar", showHeight = 16, textSize = 16)
            val shaped = TokenGlyphShaper.shape(TextEmojiTokenizer.Token(isText = true, text = "A"), content)

            assertTrue(rasterizer.drawCharCalls.isEmpty())
            assertTrue(rasterizer.drawStringCalls.isEmpty())
            assertArrayEquals(byteArrayOf(0x33, 0x00), shaped.monochrome)
        }
    }

    @Test
    fun hebrewHindiThaiSingleCharactersAlsoDispatchToDrawChar() {
        for ((lang, char) in listOf("iw" to 'א', "hi" to 'क', "th" to 'ก')) {
            val rasterizer = RecordingGlyphRasterizer(charBytes = mapOf(char to byteArrayOf(0x01, 0x00)))
            withFakes(rasterizer, ExpectOnlyFontSource(emptyMap())) {
                val content = CoolLedUxTextContentProgramContent(text = char.toString(), languageCode = lang, showHeight = 16, textSize = 16)
                TokenGlyphShaper.shape(TextEmojiTokenizer.Token(isText = true, text = char.toString()), content)
                assertEquals("lang=$lang", 1, rasterizer.drawCharCalls.size)
            }
        }
    }

    @Test
    fun rotatedMultiCharacterWordThrowsAClearNotYetPortedErrorInsteadOfMisrenderingANonSquareBitmap() {
        val word = "كتاب"
        val rasterizer = RecordingGlyphRasterizer(stringBytes = mapOf(word to ByteArray(word.length * 2)))
        withFakes(rasterizer, ExpectOnlyFontSource(emptyMap())) {
            val content = CoolLedUxTextContentProgramContent(text = word, languageCode = "ar", showHeight = 16, textSize = 16, textRotate = 90)
            assertThrows(IllegalArgumentException::class.java) {
                TokenGlyphShaper.shape(TextEmojiTokenizer.Token(isText = true, text = word), content)
            }
        }
    }

    // ---- Integration-level: full CoolleduxStreamText/CoolleduxCombineText pipeline ----

    @Test
    fun streamModeEndToEndMixesRasterizedHindiWordWithFontTableLatinCharacterInOneEncodedStream() {
        // "कलA": splitByScriptType alone (no whitespace needed) tells the HI
        // run ("कल") and the LATIN_DIGIT run ("A") apart, so this exercises
        // both the tokenizer dispatch (MultiLangTextTokenizer) and the
        // per-token shaping dispatch (TokenGlyphShaper) in one pass, then
        // confirms both shaped glyphs are framed correctly by
        // CoolleduxStreamText - i.e. the rasterized word's bytes flow through
        // the same per-glyph column-count/item-type framing a font-table
        // glyph does.
        val hindiWord = "कल"
        val rasterizer = RecordingGlyphRasterizer(
            stringBytes = mapOf(hindiWord to byteArrayOf(0x11, 0x00, 0x22, 0x00)) // 2 columns @ 16px
        )
        withFakes(rasterizer, ExpectOnlyFontSource(mapOf('A'.code to byteArrayOf(0x33, 0x00)))) {
            val content = CoolLedUxTextContentProgramContent(
                text = "$hindiWord" + "A",
                languageCode = "hi",
                showWidth = 64,
                showHeight = 16,
                mode = 2,
                textSize = 16,
                textSpacing = 0,
                textRotate = 0
            )
            val encoded = CoolleduxStreamText.encode(content)

            assertEquals(1, rasterizer.drawStringCalls.size)
            assertEquals(hindiWord, rasterizer.drawStringCalls[0].text)

            val expected = byteArrayOf(
                0x00, 0x02, // 2 tokens: the Hindi word, then 'A'
                0x00, 0x00, 0x00, 0x03, // running total columns = 2 (word) + 1 (A)
                0x02, 0x00, 0x11, 0x00, 0x22, 0x00, // word: 2 cols, type 0 (text), rasterized payload verbatim
                0x01, 0x00, 0x33, 0x00 // A: 1 col, type 0, font-table payload verbatim
            )
            assertArrayEquals(expected, encoded)
        }
    }

    @Test
    fun combineModeEndToEndRoutesARasterizedHindiWordThroughWordWrapAndCentering() {
        // Hindi (like Thai) only gets NFC-normalized by ScriptVisualText.getVisualText
        // (no bidi reorder/shaping, unlike ar/iw), so the word reaching
        // GlyphRasterizer.drawString is exactly the literal input - keeping
        // this combine-mode integration test focused on the word-wrap/
        // centering wiring rather than re-deriving ICU Arabic shaping's exact
        // output (already covered independently by ScriptVisualTextTest and,
        // for Arabic specifically, by this file's unit-level dispatch tests
        // above, which call TokenGlyphShaper directly and so don't go through
        // getVisualText at all). Devanagari word-breaking is plain UAX#29
        // (letter+extend), not dictionary-based like Thai/Lao/Khmer, so this
        // word's one-word-per-run outcome is deterministic.
        val hindiWord = "यह"
        val rasterizer = RecordingGlyphRasterizer(
            stringBytes = mapOf(hindiWord to byteArrayOf(0x11, 0x22, 0x33, 0x44)) // 4 columns @ 8px (1 byte/col)
        )
        withFakes(rasterizer, ExpectOnlyFontSourceForCombine) {
            val content = CoolLedUxTextContentProgramContent(
                text = hindiWord,
                languageCode = "hi",
                showWidth = 4, // exactly matches the word's column count - centering is then a no-op
                showHeight = 8,
                mode = 1,
                textSize = 8,
                textSpacing = 1,
                textRotate = 0
            )
            val encoded = CoolleduxCombineText.encode(content)

            assertEquals(1, rasterizer.drawStringCalls.size)
            val call = rasterizer.drawStringCalls[0]
            assertEquals(hindiWord, call.text)
            assertEquals(hindiWord.length * 8, call.width)

            val expected = byteArrayOf(
                0x00, 0x01, // 1 non-space glyph placed
                0x00, 0x00, 0x00, 0x04, // running total columns = 4 (exactly fills the canvas row)
                0x04, 0x00, 0x11, 0x22, 0x33, 0x44 // colCount=4, type 0, rasterized payload verbatim
            )
            assertArrayEquals(expected, encoded)
        }
    }

    private object ExpectOnlyFontSourceForCombine : CoolleduxFontSource {
        override fun readGlyph32(codePoint: Int, bold: Boolean): ByteArray? = null
        override fun readGlyph16(codePoint: Int, bold: Boolean): ByteArray? = null
        override fun readGlyph8(codePoint: Int): ByteArray? =
            error("unexpected font-table read for codepoint - expected the GlyphRasterizer path")
    }
}
