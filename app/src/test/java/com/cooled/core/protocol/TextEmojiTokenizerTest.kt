package com.cooled.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEmojiTokenizerTest {
    @Test
    fun shortTextBelowTwelveCharsIsOneTokenPerCharacter() {
        val tokens = TextEmojiTokenizer.tokenize("HELLO")
        assertEquals(listOf("H", "E", "L", "L", "O"), tokens.map { it.text })
        assertTrue(tokens.all { it.isText })
    }

    @Test
    fun longPlainTextWithNoEmbeddedEmojiIsStillOneTokenPerCharacter() {
        val text = "THIS IS PLAIN TEXT WITH NO EMOJI IN IT"
        val tokens = TextEmojiTokenizer.tokenize(text)
        assertEquals(text.length, tokens.size)
        assertEquals(text.toList().map { it.toString() }, tokens.map { it.text })
        assertTrue(tokens.all { it.isText })
    }

    @Test
    fun embeddedShortFormatEmojiPlaceholderBecomesOneNonTextToken() {
        val tokens = TextEmojiTokenizer.tokenize("AB emoji_fc_007 CD")
        val texts = tokens.map { it.text }
        assertEquals(listOf("A", "B", " ", "emoji_fc_007", " ", "C", "D"), texts)
        val emojiToken = tokens.first { !it.isText }
        assertEquals("emoji_fc_16x16_7", emojiToken.imageNameBySize[16])
        assertEquals("emoji_fc_32x32_7", emojiToken.imageNameBySize[32])
    }

    @Test
    fun embeddedLongFormatEmojiPlaceholderBecomesOneNonTextTokenWithDataSuffix() {
        val tokens = TextEmojiTokenizer.tokenize("X emoji_fc_01_002 Y")
        val texts = tokens.map { it.text }
        assertEquals(listOf("X", " ", "emoji_fc_01_002", " ", "Y"), texts)
        val emojiToken = tokens.first { !it.isText }
        assertEquals("emoji_fc_16x16_1_2_data", emojiToken.imageNameBySize[16])
    }

    @Test
    fun exactlyTwelveCharacterShortFormatMatchAtStartOfString() {
        val tokens = TextEmojiTokenizer.tokenize("emoji_fc_123")
        assertEquals(1, tokens.size)
        assertFalse(tokens[0].isText)
        assertEquals("emoji_fc_16x16_123", tokens[0].imageNameBySize[16])
    }

    @Test
    fun nearMissThatIsNotExactlyTheEmojiPatternStaysAsPlainCharacters() {
        // "emoji_fc_XY07" (13 chars) isn't a valid 12 or 15-char match at any
        // offset, so it should fall back to one text token per character.
        val text = "emoji_fc_XY07AAAA"
        val tokens = TextEmojiTokenizer.tokenize(text)
        assertEquals(text.length, tokens.size)
        assertTrue(tokens.all { it.isText })
    }
}
