package com.cooled.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests MultiLangTextTokenizer's port of
 * MultiLangTextEmojiParser.getTextEmojiItems/getTextEmojiItemsByLanguage -
 * fully JVM-testable since it's pure Kotlin + real icu4j (the same BreakIterator
 * dependency ScriptVisualText already uses), no Android/Canvas involved. This
 * is the piece that decides WHICH characters become multi-character
 * (word-shaped, GlyphRasterizer.drawString) tokens vs single-character ones -
 * see GlyphRasterizerDispatchTest for what happens to those tokens next.
 */
class MultiLangTextTokenizerTest {
    // Sample words chosen to have no internal whitespace/digits/punctuation,
    // so ICU's UAX#29 word-break (non-dictionary for these scripts) reliably
    // yields exactly one word spanning the whole run.
    private val arabicWord = "كتاب" // "كتاب" (book)
    private val hebrewWord = "שלום" // "שלום" (peace)
    private val hindiWord = "कल" // "कल" (yesterday/tomorrow)
    private val thaiWord = "กา" // "กา" (crow) - plain consonant+vowel, no dictionary ambiguity

    @Test
    fun unsupportedLanguageCodeFallsBackToThePlainPerCharacterTokenizer() {
        for (lang in listOf("en", "vi", "zh-CN", "")) {
            val expected = TextEmojiTokenizer.tokenize("AB")
            val actual = MultiLangTextTokenizer.tokenize(lang, "AB")
            assertEquals("lang=$lang", expected, actual)
        }
    }

    @Test
    fun aSingleArabicWordBecomesOneMultiCharacterToken() {
        val tokens = MultiLangTextTokenizer.tokenize("ar", arabicWord)
        assertEquals(listOf(TextEmojiTokenizer.Token(isText = true, text = arabicWord)), tokens)
    }

    @Test
    fun twoArabicWordsSeparatedBySpaceProduceWordSpaceWordTokens() {
        val text = "$arabicWord $arabicWord"
        val tokens = MultiLangTextTokenizer.tokenize("ar", text)
        assertEquals(
            listOf(
                TextEmojiTokenizer.Token(isText = true, text = arabicWord),
                TextEmojiTokenizer.Token(isText = true, text = " "),
                TextEmojiTokenizer.Token(isText = true, text = arabicWord)
            ),
            tokens
        )
    }

    @Test
    fun digitsEmbeddedInArabicTextAreSplitPerCharacterNotWordSegmented() {
        // "123" + arabicWord, no separator: splitByScriptType still tells
        // the LATIN_DIGIT run and the ARABIC run apart with no whitespace
        // needed, exactly like FontUtils/MultiLangTextEmojiParser's own
        // ScriptType-based run splitting.
        val tokens = MultiLangTextTokenizer.tokenize("ar", "123$arabicWord")
        assertEquals(
            listOf(
                TextEmojiTokenizer.Token(isText = true, text = "1"),
                TextEmojiTokenizer.Token(isText = true, text = "2"),
                TextEmojiTokenizer.Token(isText = true, text = "3"),
                TextEmojiTokenizer.Token(isText = true, text = arabicWord)
            ),
            tokens
        )
    }

    @Test
    fun aSingleHebrewWordBecomesOneMultiCharacterToken() {
        val tokens = MultiLangTextTokenizer.tokenize("iw", hebrewWord)
        assertEquals(listOf(TextEmojiTokenizer.Token(isText = true, text = hebrewWord)), tokens)
    }

    @Test
    fun aSingleHindiWordBecomesOneMultiCharacterToken() {
        val tokens = MultiLangTextTokenizer.tokenize("hi", hindiWord)
        assertEquals(listOf(TextEmojiTokenizer.Token(isText = true, text = hindiWord)), tokens)
    }

    @Test
    fun aSingleThaiWordBecomesOneMultiCharacterToken() {
        val tokens = MultiLangTextTokenizer.tokenize("th", thaiWord)
        assertEquals(listOf(TextEmojiTokenizer.Token(isText = true, text = thaiWord)), tokens)
    }

    @Test
    fun languageCodeMatchingIsCaseInsensitive() {
        val tokens = MultiLangTextTokenizer.tokenize("AR", arabicWord)
        assertEquals(listOf(TextEmojiTokenizer.Token(isText = true, text = arabicWord)), tokens)
    }

    @Test
    fun allTokensProducedAreTextTokensNeverImageTokens() {
        // This port deliberately doesn't handle the "emoji_fc_" placeholder
        // special-case inside getTextEmojiItemsByLanguage (documented gap in
        // MultiLangTextTokenizer's class doc) - confirm every token stays isText=true.
        val tokens = MultiLangTextTokenizer.tokenize("ar", "$arabicWord emoji_fc_007")
        assertTrue(tokens.all { it.isText })
    }
}
