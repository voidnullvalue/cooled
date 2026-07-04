package com.cooled.core.protocol

/**
 * Port of the "stream" branch of FontUtils.getFontByteDataCoolleduxForEmoji(...)
 * (the case used when `mode` is NOT in {1,4,5,6,7,8,9,10,11,12,13} - chiefly
 * scrolling text, modes 2/3). Plain (non-emoji, script-supported-by-the-font-
 * table) text only; see docs/APK_REVERSE_ENGINEERING_NOTES.md for the
 * "combine canvas" mode (`CoolleduxCombineText`) and for the emoji/Arabic/CJK
 * branches this does not cover yet.
 *
 * Per-glyph shaping (read/rescale/rotate/trim) is shared with combine mode -
 * see `CoolleduxGlyphPipeline`. This file only covers what's specific to
 * streaming: every glyph except the last gets `textSpacing` blank columns
 * appended on the right (inter-glyph spacing - no trailing gap after the
 * final glyph), then each is emitted as
 * `[1-byte column count][1-byte item type = 0][glyph bytes]`.
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

        val tokens = TextEmojiTokenizer.tokenize(content.text)
        require(tokens.all { it.isText }) {
            "CoolLEDUX emoji tokens are not yet ported for the stream-mode text path"
        }

        var runningTotalColumns = 0
        val chunks = mutableListOf<Byte>()

        tokens.forEachIndexed { index, token ->
            require(token.text.length == 1) {
                "CoolLEDUX multi-character tokens (RTL/CJK draw path) are not yet ported"
            }
            var glyph = CoolleduxGlyphPipeline.readAndShapeGlyph(token.text.codePointAt(0), content)

            if (index != tokens.lastIndex) {
                glyph = FontCanvasWordWrap.addEmptyColumns(glyph, content.textSpacing, bytesPerColumn)
            }

            val columnCount = glyph.size / bytesPerColumn
            runningTotalColumns += columnCount
            chunks += oneByteHex(columnCount)
            chunks += oneByteHex(0) // item type 0 = text
            chunks += glyph.toList()
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
