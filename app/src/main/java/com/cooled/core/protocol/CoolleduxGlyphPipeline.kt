package com.cooled.core.protocol

/**
 * Shared per-glyph read/rescale/rotate/trim pipeline used by both the
 * stream-mode (`CoolleduxStreamText`) and combine-canvas-mode
 * (`CoolleduxCombineText`) branches of FontUtils.getFontByteDataCoolleduxForEmoji(...).
 * Both branches apply this identical sequence before diverging on how the
 * result gets laid out/emitted - see docs/APK_REVERSE_ENGINEERING_NOTES.md.
 */
object CoolleduxGlyphPipeline {
    val supportedShowHeights = setOf(8, 12, 14, 16, 20, 24, 32)

    fun bytesPerColumnFor(size: Int): Int = (size + 7) / 8

    /**
     * Reads, rescales, rotates, and trims a single-character glyph for
     * [content], matching FontUtils's per-glyph pipeline exactly:
     *  1. Read the raw glyph at its native font-table size (a missing font
     *     table returns a zero-filled glyph, matching FontUtils.readFontData's
     *     blank-glyph fallback - see the reverse-engineering notes).
     *  2. Rescale up to `showHeight` via FontGlyphRescale if the native size
     *     is smaller (there is no APK downscale path).
     *  3. Rotate by `content.textRotate` via FontBitmapRotation.
     *  4. Trim blank leading/trailing columns via FontColumnTrimming,
     *     unconditionally.
     *  5. If `content.textRotate` is 90 or 270, ADDITIONALLY run
     *     FontCentering.processBytesCentered followed by a second
     *     FontColumnTrimming pass - a genuine APK double-pass, verified
     *     against smali, not a bug in this port.
     */
    fun readAndShapeGlyph(codePoint: Int, content: CoolLedUxTextContentProgramContent): ByteArray {
        val showHeight = content.showHeight
        val nativeSize = readNativeGlyphSize(content.textSize, content.isTextBold)
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
        return glyph
    }

    /** FontUtils.processBytesCentered12/20 internally quirk-trim using the 14px/24px family; see FontCentering.kt. */
    private fun processBytesCenteredTrimQuirk(showHeight: Int): Int = when (showHeight) {
        12 -> 14
        20 -> 24
        else -> showHeight
    }

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
}
