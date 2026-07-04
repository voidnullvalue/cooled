package com.cooled.core.protocol

/**
 * Port of TextEmojiManagerCoolLEDUX.getTextEmojiItems(String text, String pattern)
 * (the instance method - the default/fallback tokenizer that
 * MultiLangTextEmojiParser.getTextEmojiItemsByLanguage(...) delegates to for
 * any language without its own special-cased branch). Splits [text] into a
 * sequence of plain-character tokens and embedded-emoji-placeholder tokens.
 *
 * The embedded placeholder formats (matched by
 * TextEmojiManagerCoolLEDUX.PATTEN_STR = "emoji_fc_\\d{2}_\\d{3}|emoji_fc_\\d{3}")
 * only ever appear if something upstream (the app's own emoji picker) wrote
 * one inline into the string - typed text will essentially never match, so
 * this degenerates to "one token per character" for ordinary input, which is
 * the common case this repo needs today.
 *
 * IMPORTANT DECOMPILE CAVEAT: jadx's `-m simple` mode (needed because this
 * class also has methods `auto` mode fails to decompile - see
 * docs/APK_REVERSE_ENGINEERING_NOTES.md) rendered this method's pseudocode
 * *without* the backward branch that closes its main loop - i.e. reading the
 * jadx output naively makes this method look like it only ever processes
 * index 0 and returns. The raw smali
 * (reverse/apktool/smali_classes3/.../TextEmojiManagerCoolLEDUX.smali) has an
 * unambiguous `goto/16 :goto_0` closing the loop; this is the same class of
 * jadx `-m simple` rendering bug as the label-reuse issue documented for
 * FontUtils.getFontByteDataCoolleduxForEmoji, just manifesting as a dropped
 * edge instead of a relabeled one. Always check the smali for backward
 * branches before trusting `-m simple` control flow on a method this shape.
 */
object TextEmojiTokenizer {
    private val defaultPattern = Regex("emoji_fc_\\d{2}_\\d{3}|emoji_fc_\\d{3}", RegexOption.IGNORE_CASE)

    data class Token(
        val isText: Boolean,
        val text: String,
        val imageNameBySize: Map<Int, String> = emptyMap()
    )

    fun tokenize(text: String, pattern: Regex = defaultPattern): List<Token> {
        val out = mutableListOf<Token>()
        var i = 0
        if (text.length < 12) {
            while (i < text.length) {
                out += Token(isText = true, text = text[i].toString())
                i++
            }
            return out
        }
        while (i < text.length) {
            if (i <= text.length - 15) {
                val candidate = text.substring(i, i + 15)
                val match = pattern.find(candidate)
                if (match != null && match.value == candidate) {
                    val parts = candidate.split("_")
                    val emojiType = parts[2].toInt()
                    val emojiNumber = parts[3].toInt()
                    out += longFormatEmojiToken(candidate, emojiType, emojiNumber)
                    i += 14
                    i += 1
                    continue
                }
            }
            if (i <= text.length - 12) {
                val candidate = text.substring(i, i + 12)
                val match = pattern.find(candidate)
                if (match != null && match.value == candidate) {
                    val emojiNumber = candidate.substring(9).toInt()
                    out += shortFormatEmojiToken(candidate, emojiNumber)
                    i += 11
                    i += 1
                    continue
                }
            }
            out += Token(isText = true, text = text[i].toString())
            i += 1
        }
        return out
    }

    /** Port of TextEmojiManagerCoolLEDUX.createEmojiItem(String, int, int) - "long format" placeholder. */
    private fun longFormatEmojiToken(raw: String, emojiType: Int, emojiNumber: Int): Token = Token(
        isText = false,
        text = raw,
        imageNameBySize = mapOf(
            12 to "emoji_fc_12x12_${emojiType}_${emojiNumber}_data",
            14 to "emoji_fc_14x14_${emojiType}_${emojiNumber}_data",
            16 to "emoji_fc_16x16_${emojiType}_${emojiNumber}_data",
            20 to "emoji_fc_20x20_${emojiType}_${emojiNumber}_data",
            24 to "emoji_fc_24x24_${emojiType}_${emojiNumber}_data",
            32 to "emoji_fc_32x32_${emojiType}_${emojiNumber}_data"
        )
    )

    /** Port of TextEmojiManagerCoolLEDUX.createEmojiItem(String, int) - "short format" placeholder. */
    private fun shortFormatEmojiToken(raw: String, emojiNumber: Int): Token = Token(
        isText = false,
        text = raw,
        imageNameBySize = mapOf(
            12 to "emoji_fc_12x12_$emojiNumber",
            14 to "emoji_fc_14x14_$emojiNumber",
            16 to "emoji_fc_16x16_$emojiNumber",
            20 to "emoji_fc_20x20_$emojiNumber",
            24 to "emoji_fc_24x24_$emojiNumber",
            32 to "emoji_fc_32x32_$emojiNumber"
        )
    )
}
