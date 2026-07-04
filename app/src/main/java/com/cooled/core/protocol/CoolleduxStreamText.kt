package com.cooled.core.protocol

/**
 * Port of the "stream" branch of FontUtils.getFontByteDataCoolleduxForEmoji(...)
 * (the case used when `mode` is NOT in {1,4,5,6,7,8,9,10,11,12,13} - chiefly
 * scrolling text, modes 2/3). Plain (non-emoji, script-supported-by-the-font-
 * table) text only; see docs/APK_REVERSE_ENGINEERING_NOTES.md for the
 * "combine canvas" mode (1, 4-13) and for the emoji/Arabic/CJK branches this
 * does not cover yet.
 *
 * Per-glyph pipeline (hand-traced against
 * reverse/apktool/smali_classes3/.../FontUtils.smali, not just jadx
 * pseudocode - see the notes file for two control-flow rendering bugs found
 * and corrected while doing so):
 *  1. Read the raw glyph at its native font-table size.
 *  2. Rescale up to `showHeight` via FontGlyphRescale if the native size is smaller.
 *  3. Rotate by `textRotate` via FontBitmapRotation.
 *  4. Trim blank leading/trailing columns via FontColumnTrimming, unconditionally.
 *  5. If `textRotate` is 90 or 270, ADDITIONALLY run FontCentering.processBytesCentered
 *     followed by a second FontColumnTrimming pass (this is a genuine APK
 *     double-pass, verified against smali, not a bug in this port).
 *  6. Every glyph except the last gets `textSpacing` blank columns appended
 *     on the right (inter-glyph spacing - there's no trailing gap after the
 *     final glyph).
 *  7. Emit `[1-byte column count][1-byte item type = 0][glyph bytes as hex]`.
 *
 * Final framing (verified against smali - an earlier draft of the reverse-
 * engineering notes, based on jadx pseudocode with a dropped-loop-closing-edge
 * bug, wrongly concluded stream mode's tail differs structurally from combine
 * mode's): `[2-byte token count including spaces][4-byte running column
 * total][accumulated per-glyph chunks]`.
 */
object CoolleduxStreamText {
    private val supportedShowHeights = setOf(8, 12, 14, 16, 20, 24, 32)

    fun encode(content: CoolLedUxTextContentProgramContent): ByteArray {
        val showHeight = content.showHeight
        require(showHeight in supportedShowHeights) { "Unsupported CoolLEDUX showHeight: $showHeight" }
        val bytesPerColumn = (showHeight + 7) / 8

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
            val codePoint = token.text.codePointAt(0)
            val nativeSize = readNativeGlyphSize(content.textSize, content.isTextBold)
            // Matches FontUtils.readFontData(...): a missing/unreadable glyph
            // is a zero-filled array, not an error - see
            // docs/APK_REVERSE_ENGINEERING_NOTES.md, "readFontData ... blank-glyph fallback".
            var glyph = readNativeGlyph(codePoint, content.textSize, content.isTextBold)
                ?: ByteArray(nativeSize * bytesPerColumnFor(nativeSize))

            if (nativeSize < showHeight) {
                glyph = FontGlyphRescale.transfer(glyph, fromSize = nativeSize, toSize = showHeight)
            }
            glyph = FontBitmapRotation.rotate(content.textRotate, glyph, showHeight)
            glyph = FontColumnTrimming.deleteEmptyColumns(glyph, content.textSize)
            if (content.textRotate == 90 || content.textRotate == 270) {
                val centered = FontCentering.processBytesCentered(
                    glyph,
                    squareSize = showHeight,
                    trimTextSize = processBytesCenteredTrimQuirk(showHeight)
                )
                glyph = FontColumnTrimming.deleteEmptyColumns(centered, content.textSize)
            }

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

    /** FontUtils.processBytesCentered12/20 internally quirk-trim using the 14px/24px family; see FontCentering.kt. */
    private fun processBytesCenteredTrimQuirk(showHeight: Int): Int = when (showHeight) {
        12 -> 14
        20 -> 24
        else -> showHeight
    }

    private fun bytesPerColumnFor(size: Int): Int = (size + 7) / 8

    private fun readNativeGlyphSize(requestedTextSize: Int, isBold: Boolean): Int = when {
        requestedTextSize >= 32 -> 32
        requestedTextSize >= 16 -> 16
        requestedTextSize >= 14 && isBold -> 14
        requestedTextSize >= 12 -> 12
        else -> 8
    }

    private fun readNativeGlyph(codePoint: Int, requestedTextSize: Int, isBold: Boolean): ByteArray? {
        val source = CoolleduxFontSources.active
        return when {
            requestedTextSize >= 32 -> source.readGlyph32(codePoint, isBold)
            requestedTextSize >= 16 -> source.readGlyph16(codePoint, isBold)
            requestedTextSize >= 14 && isBold -> source.readGlyph14Bold(codePoint)
            requestedTextSize >= 12 -> source.readGlyph12(codePoint, isBold)
            else -> source.readGlyph8(codePoint)
        }
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
