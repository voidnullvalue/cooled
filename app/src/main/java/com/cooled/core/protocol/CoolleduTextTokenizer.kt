package com.cooled.core.protocol

/**
 * Port of TextEmojiManagerCoolLEDU.getTextEmojiItems(text, pattern) - CoolLEDU's
 * own tokenizer, a genuinely different algorithm from CoolLEDUX's
 * (TextEmojiTokenizer), not just a different regex constant:
 *
 *  - If the text is under 4 characters, every character becomes its own
 *    text token unconditionally - no pattern matching is attempted at all.
 *  - Otherwise, it slides a fixed 4-character window ("i" + 3 digits, e.g.
 *    "i012") across the string. On a match it parses the 3 digits as an
 *    emoji index and looks it up by *reverse position* in one of several
 *    preloaded per-size emoji lists (`listEmojiItems12/14/16/20/24/32`,
 *    `list.get(list.size() - index)`) - a completely different resource
 *    mechanism from CoolLEDUX's asset-file-path-by-name lookup
 *    (EmojiTokenGlyphs), and not yet reverse-engineered here (that list's
 *    origin/contents haven't been traced).
 *
 * SCOPE: this port only handles the text-only case. A real 4-character
 * emoji-pattern match throws rather than silently mishandling it - the
 * emoji resource lookup is a separate, unported piece of work.
 */
object CoolleduTextTokenizer {
    val emojiPattern = Regex("i0[0-9]{2}|i10[0-9]|i11[0-9]", RegexOption.IGNORE_CASE)

    fun tokenize(text: String): List<String> {
        if (text.length < 4) return text.map { it.toString() }

        val tokens = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            if (i <= text.length - 4) {
                val candidate = text.substring(i, i + 4)
                if (emojiPattern.matches(candidate)) {
                    error("CoolLEDU emoji tokens (matched '$candidate') are not yet ported - see CoolleduTextTokenizer's class doc")
                }
            }
            tokens += text[i].toString()
            i++
        }
        return tokens
    }
}
