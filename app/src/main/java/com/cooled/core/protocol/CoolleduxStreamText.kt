package com.cooled.core.protocol

/**
 * Port of the "stream" branch of FontUtils.getFontByteDataCoolleduxForEmoji(...)
 * (the case used when `mode` is NOT in {1,4,5,6,7,8,9,10,11,12,13} - chiefly
 * scrolling text, modes 2/3). See docs/APK_REVERSE_ENGINEERING_NOTES.md for
 * the "combine canvas" mode (`CoolleduxCombineText`).
 *
 * Tokenizing goes through `ScriptVisualText.getVisualText` (bidi-reorder/shape,
 * a no-op for languageCodes other than ar/iw/vi/th/hi) then
 * `MultiLangTextTokenizer`, matching FontUtils.java:11009-11012 exactly -
 * languageCode ar/iw/hi/th gets ICU word-segmented tokens that may be
 * multi-character (drawn via `GlyphRasterizer.drawString`); everything else
 * falls back to the plain per-character `TextEmojiTokenizer`.
 *
 * Per-token shaping (read/rescale/rotate/trim for text; decode/center/rotate/
 * trim for images) is shared with combine mode - see `TokenGlyphShaper`. This
 * file only covers what's specific to streaming: every glyph except the last
 * gets `textSpacing` blank columns appended on the right (inter-glyph spacing
 * - no trailing gap after the final glyph), then each is emitted as
 * `[1-byte column count][1-byte item type][payload bytes]` (payload is the
 * monochrome bytes for text, RGB444 bytes for images - see `ShapedGlyph`).
 *
 * Final framing (verified against smali - an earlier draft of the reverse-
 * engineering notes, based on jadx pseudocode with a dropped-loop-closing-edge
 * bug, wrongly concluded stream mode's tail differs structurally from combine
 * mode's): `[2-byte token count including spaces][4-byte running column
 * total][accumulated per-glyph chunks]`.
 */
object CoolleduxStreamText {
    fun encode(content: CoolLedUxTextContentProgramContent): ByteArray {
        val showHeight = content.showHeight
        require(showHeight in CoolleduxGlyphPipeline.supportedShowHeights) { "Unsupported CoolLEDUX showHeight: $showHeight" }
        val bytesPerColumn = CoolleduxGlyphPipeline.bytesPerColumnFor(showHeight)

        val visualText = ScriptVisualText.getVisualText(content.languageCode, content.text)
        val tokens = MultiLangTextTokenizer.tokenize(content.languageCode, visualText)

        var runningTotalColumns = 0
        val chunks = mutableListOf<Byte>()

        tokens.forEachIndexed { index, token ->
            var shaped = TokenGlyphShaper.shape(token, content)

            if (index != tokens.lastIndex) {
                shaped = shaped.withPadding(content.textSpacing, toLeft = false, monochromeBytesPerColumn = bytesPerColumn)
            }

            val columnCount = shaped.monochrome.size / bytesPerColumn
            runningTotalColumns += columnCount
            chunks += oneByteHex(columnCount)
            chunks += oneByteHex(shaped.itemType)
            chunks += shaped.payload.toList()
        }

        val out = mutableListOf<Byte>()
        out += twoByteBE(tokens.size)
        out += fourByteBE(runningTotalColumns)
        out += chunks
        return out.toByteArray()
    }

    private fun oneByteHex(value: Int): List<Byte> = listOf((value and 0xFF).toByte())
    private fun twoByteBE(value: Int): List<Byte> = listOf(((value ushr 8) and 0xFF).toByte(), (value and 0xFF).toByte())
    private fun fourByteBE(value: Int): List<Byte> = listOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )
}
