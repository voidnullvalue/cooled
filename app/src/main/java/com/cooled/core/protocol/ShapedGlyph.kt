package com.cooled.core.protocol

/**
 * A single shaped (read/rescaled/rotated/trimmed) token, ready to be laid
 * onto a stream or combine-canvas. Every padding/word-wrap/realignment
 * decision in FontUtils.getFontByteDataCoolleduxForEmoji is made using
 * [monochrome] alone (the same column-major/bytesPerColumn convention for
 * both text and image tokens - confirmed by tracing FontUtils.smali's
 * combine-mode canvas search, which stores and matches against monochrome
 * bytes regardless of item type). [payload] is what actually gets emitted:
 * for text it's the same bytes as [monochrome]; for images it's the richer
 * RGB444 encoding, padded in lockstep with [monochrome] using its own byte
 * stride (`addEmptyColumnForImageDataToThe{Left,Right}` in the APK, vs.
 * `addEmptyColumnForData<N>ToThe{Left,Right}` for text/monochrome).
 */
sealed class ShapedGlyph {
    abstract val monochrome: ByteArray
    abstract val itemType: Int
    abstract val payload: ByteArray

    /** Returns a copy of this glyph with [monochrome] and [payload] each padded by [count] blank columns on the given side ([monochromeBytesPerColumn] applies to [monochrome]; each variant knows its own payload stride). */
    abstract fun withPadding(count: Int, toLeft: Boolean, monochromeBytesPerColumn: Int): ShapedGlyph

    data class Text(override val monochrome: ByteArray) : ShapedGlyph() {
        override val itemType: Int = 0
        override val payload: ByteArray get() = monochrome

        override fun withPadding(count: Int, toLeft: Boolean, monochromeBytesPerColumn: Int): ShapedGlyph {
            if (count <= 0) return this
            val padded = if (toLeft) {
                FontCanvasWordWrap.addEmptyColumnsToTheLeft(monochrome, count, monochromeBytesPerColumn)
            } else {
                FontCanvasWordWrap.addEmptyColumns(monochrome, count, monochromeBytesPerColumn)
            }
            return Text(padded)
        }
    }

    data class Image(override val monochrome: ByteArray, val rgb444: ByteArray, val showHeight: Int) : ShapedGlyph() {
        override val itemType: Int = 1
        override val payload: ByteArray get() = rgb444
        private val payloadBytesPerColumn: Int get() = showHeight * 2

        override fun withPadding(count: Int, toLeft: Boolean, monochromeBytesPerColumn: Int): ShapedGlyph {
            if (count <= 0) return this
            val newMonochrome = if (toLeft) {
                FontCanvasWordWrap.addEmptyColumnsToTheLeft(monochrome, count, monochromeBytesPerColumn)
            } else {
                FontCanvasWordWrap.addEmptyColumns(monochrome, count, monochromeBytesPerColumn)
            }
            val newRgb444 = if (toLeft) {
                FontCanvasWordWrap.addEmptyColumnsToTheLeft(rgb444, count, payloadBytesPerColumn)
            } else {
                FontCanvasWordWrap.addEmptyColumns(rgb444, count, payloadBytesPerColumn)
            }
            return Image(newMonochrome, newRgb444, showHeight)
        }
    }
}

/**
 * Shapes a single token (text or image) for [content], matching the shared
 * front half of FontUtils.getFontByteDataCoolleduxForEmoji's per-token
 * pipeline before it diverges into the stream/combine-specific tail. See
 * CoolleduxGlyphPipeline for the text-only read/rescale/rotate/trim
 * sequence this wraps, and docs/APK_REVERSE_ENGINEERING_NOTES.md
 * ("image-token integration") for the image path's provenance:
 *
 *  - Text: CoolleduxGlyphPipeline.readAndShapeGlyph (includes the
 *    rotate-90/270 double-trim pass, which the smali confirms is
 *    text-only - `if-nez v10, :cond_51` skips it entirely for images).
 *  - Image: decode+center the named emoji asset, rotate the *pixel grid*
 *    (FontUtils.rotate(angle, List&lt;DrawItem&gt;, size) - a different
 *    rotation path than text's byte-array rotate, though the same
 *    clockwise-90-degree formula), trim the monochrome silhouette once
 *    (no double-trim for images), and separately trim+RGB444-encode the
 *    same rotated grid via toTrimmedRgb444Columns (a different, narrower
 *    blank-pixel test than the monochrome trim - see EmojiGlyphEncoder).
 */
object TokenGlyphShaper {
    fun shape(token: TextEmojiTokenizer.Token, content: CoolLedUxTextContentProgramContent): ShapedGlyph {
        if (token.isText) {
            require(token.text.length == 1) {
                "CoolLEDUX multi-character tokens (RTL/CJK draw path) are not yet ported"
            }
            return ShapedGlyph.Text(CoolleduxGlyphPipeline.readAndShapeGlyph(token.text.codePointAt(0), content))
        }

        val showHeight = content.showHeight
        val imageName = token.imageNameBySize[content.textSize]
            ?: error("No CoolLEDUX emoji asset name for textSize=${content.textSize} (token text='${token.text}')")
        val decoded = EmojiTokenGlyphs.decodeAndCenter(imageName, showHeight)
            ?: error("Missing or unreadable CoolLEDUX emoji asset: $imageName")
        val rotated = EmojiGlyphEncoder.rotateImage(content.textRotate, decoded)
        val monochrome = FontColumnTrimming.deleteEmptyColumns(EmojiGlyphEncoder.toMonochromeColumns(rotated), content.textSize)
        val rgb444 = EmojiGlyphEncoder.toTrimmedRgb444Columns(rotated)
        return ShapedGlyph.Image(monochrome, rgb444, showHeight)
    }
}
