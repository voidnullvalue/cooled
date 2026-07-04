package com.cooled.core.protocol

/**
 * Port of the "combine canvas" branch of FontUtils.getFontByteDataCoolleduxForEmoji(...)
 * (`mode` in {1,4,5,6,7,8,9,10,11,12,13} - a shared word-wrapped, row-centered
 * canvas rather than a plain glyph stream; see `CoolleduxStreamText` for the
 * other branch and docs/APK_REVERSE_ENGINEERING_NOTES.md for how the two were
 * told apart).
 *
 * Per-token shaping (read/rescale/rotate/trim for text; decode/center/rotate/
 * trim for images) is shared with stream mode - see `TokenGlyphShaper`. What's
 * specific to combine mode, hand-traced against
 * reverse/apktool/smali_classes3/.../FontUtils.smali:
 *
 *  1. Every shaped glyph (space tokens included) is laid onto one shared
 *     column-major canvas via `FontCanvasWordWrap.checkSegment(...)`, which
 *     word-wraps at `showWidth` columns. The canvas only ever holds the
 *     *monochrome* representation - confirmed by tracing the smali, the
 *     combine-mode search/realignment below operates on monochrome bytes
 *     for both text and image tokens alike, which is exactly why
 *     `ShapedGlyph` keeps a `monochrome` field even for images.
 *  2. Once every token has been laid down, the whole canvas is split into
 *     `showWidth`-column rows and each row is independently centered via
 *     `FontCentering.getCenteredDataBytes(...)`.
 *  3. The APK then re-derives per-glyph *output* chunks from that finished
 *     canvas by literally searching for each glyph's original (pre-checkSegment)
 *     monochrome bytes within it, walking a single cursor forward across the
 *     whole canvas in original token order: search for the next
 *     `bytesPerColumn`-aligned position where the glyph's bytes match
 *     exactly; anything between the cursor and that match becomes
 *     left-padding on the output chunk (checkSegment's word-wrap gap or
 *     getCenteredDataBytes's row-centering margin); anything zero-valued
 *     immediately after the match becomes right-padding. The same padding
 *     column counts are then applied to the *payload* representation too
 *     (monochrome bytes for text, RGB444 bytes for images - see
 *     `ShapedGlyph.withPadding`). Space/empty tokens (never added to the
 *     canvas-glyph list in the first place) contribute canvas width but no
 *     output chunk. A failed search (which should never happen for a
 *     well-formed canvas - it would mean this port's canvas construction
 *     diverged from the APK's) silently skips the item with the cursor left
 *     unmoved, matching the APK's own defensive fallback exactly rather than
 *     throwing.
 *
 * Final framing is the same shape as stream mode
 * (`[2-byte count][4-byte running column total][chunks]`); combine mode's
 * 2-byte count is the number of non-space glyphs actually placed, not the
 * total token count.
 */
object CoolleduxCombineText {
    fun encode(content: CoolLedUxTextContentProgramContent): ByteArray {
        val showHeight = content.showHeight
        require(showHeight in CoolleduxGlyphPipeline.supportedShowHeights) { "Unsupported CoolLEDUX showHeight: $showHeight" }
        val bytesPerColumn = CoolleduxGlyphPipeline.bytesPerColumnFor(showHeight)

        val tokens = TextEmojiTokenizer.tokenize(content.text)

        var canvas: ByteArray? = null
        val placedGlyphs = mutableListOf<ShapedGlyph>() // space/empty text tokens excluded

        for (token in tokens) {
            val shaped = TokenGlyphShaper.shape(token, content)
            if (token.text != " " && token.text.isNotEmpty()) {
                placedGlyphs += shaped
            }
            canvas = FontCanvasWordWrap.checkSegment(canvas, shaped.monochrome, content.showWidth, content.textSpacing, bytesPerColumn)
        }

        val centeredCanvas = FontCentering.getCenteredDataBytes(canvas ?: ByteArray(0), showHeight, content.showWidth)

        var runningTotalColumns = 0
        val chunks = mutableListOf<Byte>()
        var cursor = 0

        for (shaped in placedGlyphs) {
            val matchStart = findAlignedMatch(centeredCanvas, shaped.monochrome, cursor, bytesPerColumn) ?: continue

            val leadingPadColumns = (matchStart - cursor) / bytesPerColumn
            var output = shaped.withPadding(leadingPadColumns, toLeft = true, monochromeBytesPerColumn = bytesPerColumn)

            var trailingScan = matchStart + shaped.monochrome.size
            var trailingPadColumns = 0
            while (trailingScan <= centeredCanvas.size - bytesPerColumn && isBlankColumn(centeredCanvas, trailingScan, bytesPerColumn)) {
                trailingPadColumns++
                trailingScan += bytesPerColumn
            }
            output = output.withPadding(trailingPadColumns, toLeft = false, monochromeBytesPerColumn = bytesPerColumn)
            cursor = trailingScan

            val columnCount = output.monochrome.size / bytesPerColumn
            runningTotalColumns += columnCount
            chunks += oneByteHex(columnCount)
            chunks += oneByteHex(output.itemType)
            chunks += output.payload.toList()
        }

        val out = mutableListOf<Byte>()
        out += twoByteBE(placedGlyphs.size)
        out += fourByteBE(runningTotalColumns)
        out += chunks
        return out.toByteArray()
    }

    /** Brute-force, [bytesPerColumn]-aligned substring search - matches FontUtils's own inline search exactly (see class doc). */
    private fun findAlignedMatch(haystack: ByteArray, needle: ByteArray, fromIndex: Int, bytesPerColumn: Int): Int? {
        var pos = fromIndex
        while (pos <= haystack.size - bytesPerColumn) {
            var offset = 0
            var matched = true
            while (offset <= needle.size - bytesPerColumn) {
                if (!regionEquals(haystack, pos + offset, needle, offset, bytesPerColumn)) {
                    matched = false
                    break
                }
                offset += bytesPerColumn
            }
            if (matched) return pos
            pos += bytesPerColumn
        }
        return null
    }

    private fun regionEquals(a: ByteArray, aOffset: Int, b: ByteArray, bOffset: Int, length: Int): Boolean {
        for (i in 0 until length) if (a[aOffset + i] != b[bOffset + i]) return false
        return true
    }

    private fun isBlankColumn(bytes: ByteArray, offset: Int, bytesPerColumn: Int): Boolean {
        for (i in 0 until bytesPerColumn) if (bytes[offset + i] != 0.toByte()) return false
        return true
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
