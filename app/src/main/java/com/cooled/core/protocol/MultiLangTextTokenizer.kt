package com.cooled.core.protocol

import com.ibm.icu.text.BreakIterator
import java.util.Locale

/**
 * Port of the dispatch/segmentation half of
 * com.jtkj.led1248.light.utils.MultiLangTextEmojiParser - the piece
 * FontUtils.getFontByteDataCoolleduxForEmoji actually calls
 * (`MultiLangTextEmojiParser.getTextEmojiItems(languageCode, visualText,
 * TextEmojiManagerCoolLEDUX.PATTEN_STR)`, FontUtils.java:11012) before it
 * ever reaches `readFontDataFromDraw`/`GlyphRasterizer`.
 *
 * `getTextEmojiItems` (MultiLangTextEmojiParser.java:239-253) only
 * special-cases languageCode ar/iw/hi/th; every other language code
 * (including "vi"/"zh-CN"/blank, which have their own separate paths) falls
 * straight through to `TextEmojiManagerCoolLEDUX.getInstance().getTextEmojiItems(...)`
 * - already ported here as [TextEmojiTokenizer].
 *
 * For the four special-cased languages, `getTextEmojiItemsByLanguage`
 * (MultiLangTextEmojiParser.java:346-538) does, in order:
 *  1. Splits [text] into script-homogeneous runs via `getScriptType`/
 *     `splitByScriptType` (lines 259-340) - ARABIC/IW/HI/TH/LATIN_DIGIT/
 *     WHITESPACE/PUNCTUATION/OTHER, one ScriptType per run.
 *  2. For a run whose ScriptType matches the outer languageCode's own script
 *     (e.g. an ARABIC run when languageCode=="ar"), further splits it into
 *     words using an ICU `BreakIterator.getWordInstance(Locale(languageCode))`
 *     (the exact same icu4j 72.1 the APK bundles - see ScriptVisualText). A
 *     non-blank word becomes ONE multi-character token (so
 *     `GlyphRasterizer.drawString` can shape/join the whole word's letterforms
 *     together); a blank/whitespace "word" is instead split into individual
 *     single-character tokens (matching lines 384-399 / 419-430 / 450-461 /
 *     480-495 exactly - a whitespace run is never itself letter-shaped).
 *  3. A run whose ScriptType does NOT match the outer language (stray digits/
 *     Latin/punctuation/other characters embedded in ar/iw/hi/th content) is
 *     split into individual single-character tokens directly, with no
 *     BreakIterator involved (lines 400-407 / 431-438 / 462-469 / 496-503).
 *
 * NOT ported (documented gap, not guessed at): the "emoji_fc_" embedded
 * placeholder special-case inside `getTextEmojiItemsByLanguage`
 * (MultiLangTextEmojiParser.java:369-521) and the trailing
 * `reverseConsecutiveEmojis` reordering pass for ar/iw (lines 523-528, a
 * no-op here since no non-text tokens are ever produced by this port). Both
 * belong to the separate, already-documented emoji/image-token gap
 * (docs/APK_PORT_STATUS.md); the reference implementation's own handling of
 * the "long format" placeholder inside this method looks broken besides
 * (`r62.substring(9)` unconditionally assumes the short format), so this is
 * left unhandled rather than transcribing questionable behavior.
 */
object MultiLangTextTokenizer {
    private val wordSegmentedLanguages = setOf("ar", "iw", "hi", "th")

    fun tokenize(languageCode: String, text: String): List<TextEmojiTokenizer.Token> {
        val lang = languageCode.lowercase(Locale.ROOT)
        if (lang !in wordSegmentedLanguages) return TextEmojiTokenizer.tokenize(text)
        return tokenizeByLanguage(lang, text)
    }

    /** Port of getScriptType(char) (MultiLangTextEmojiParser.java:259-285). */
    private enum class ScriptType { ARABIC, IW, HI, TH, LATIN_DIGIT, WHITESPACE, PUNCTUATION, OTHER }

    private fun getScriptType(c: Char): ScriptType = when {
        c.isWhitespace() -> ScriptType.WHITESPACE
        c.isDigit() -> ScriptType.LATIN_DIGIT
        c.isLetter() && c.code < 1536 -> ScriptType.LATIN_DIGIT
        ScriptDetection.isArabic(c) -> ScriptType.ARABIC
        ScriptDetection.isHebrew(c) -> ScriptType.IW
        ScriptDetection.isHindi(c) -> ScriptType.HI
        ScriptDetection.isThai(c) -> ScriptType.TH
        isPunctuation(c) -> ScriptType.PUNCTUATION
        else -> ScriptType.OTHER
    }

    /** Port of isPunctuation(char) (MultiLangTextEmojiParser.java:287-311): the Unicode general-category buckets the APK treats as punctuation. */
    private fun isPunctuation(c: Char): Boolean = when (Character.getType(c)) {
        Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(),
        Character.END_PUNCTUATION.toInt(),
        Character.CONNECTOR_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
        Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt() -> true
        else -> false
    }

    /** Port of splitByScriptType(String) (MultiLangTextEmojiParser.java:313-340): groups consecutive same-ScriptType characters into runs. */
    private fun splitByScriptType(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        val builder = StringBuilder()
        var currentType = getScriptType(text[0])
        builder.append(text[0])
        for (i in 1 until text.length) {
            val c = text[i]
            val type = getScriptType(c)
            if (type == currentType) {
                builder.append(c)
            } else {
                out += builder.toString()
                builder.setLength(0)
                builder.append(c)
                currentType = type
            }
        }
        out += builder.toString()
        return out
    }

    private fun scriptMatches(lang: String, run: String): Boolean = when (lang) {
        "ar" -> ScriptDetection.isArabic(run[0])
        "iw" -> ScriptDetection.isHebrew(run[0])
        "hi" -> ScriptDetection.isHindi(run[0])
        "th" -> ScriptDetection.isThai(run[0])
        else -> false
    }

    private fun tokenizeByLanguage(lang: String, text: String): List<TextEmojiTokenizer.Token> {
        val out = mutableListOf<TextEmojiTokenizer.Token>()
        for (run in splitByScriptType(text)) {
            if (run.isEmpty()) continue
            if (scriptMatches(lang, run)) {
                segmentByWords(lang, run, out)
            } else {
                run.forEach { addTextSegment(out, it.toString()) }
            }
        }
        return out
    }

    /**
     * Port of the per-language BreakIterator word-walk shared by the ar/iw/hi/th
     * branches (MultiLangTextEmojiParser.java:375-503 - identical shape in all
     * four, differing only in the locale and the outer `if` guarding it): each
     * `[boundary, nextBoundary)` word is added whole if non-blank, or split into
     * single-character tokens if it's a blank/whitespace "word".
     */
    private fun segmentByWords(lang: String, run: String, out: MutableList<TextEmojiTokenizer.Token>) {
        val iterator = BreakIterator.getWordInstance(Locale(lang))
        iterator.setText(run)
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val word = run.substring(start, end)
            if (word.isNotBlank()) {
                addTextSegment(out, word)
            } else {
                word.forEach { addTextSegment(out, it.toString()) }
            }
            start = end
            end = iterator.next()
        }
    }

    /** Port of addTextSegment(List, String) (MultiLangTextEmojiParser.java:566-573). */
    private fun addTextSegment(out: MutableList<TextEmojiTokenizer.Token>, text: String) {
        out += TextEmojiTokenizer.Token(isText = true, text = text)
    }
}
