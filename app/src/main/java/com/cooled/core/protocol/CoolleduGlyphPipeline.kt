package com.cooled.core.protocol

/**
 * Per-glyph read/rotate/trim pipeline for FontUtils.getFontByteDataCoolleduForEmoji
 * (the CoolLEDU family's own text renderer - distinct from, but structurally
 * close to, getFontByteDataCoolleduxForEmoji already ported as
 * CoolleduxGlyphPipeline). Confirmed by direct read of FontUtils.java that
 * readFontData, rotate(int,byte[],int), FontColumnTrimming's
 * deleteEmptyColumnFor<N>, and processBytesCentered are *single, shared*
 * definitions in FontUtils.java used by both the CoolLEDU and CoolLEDUX
 * pipelines - not duplicated per family - so this reuses
 * CoolleduxFontSources/FontBitmapRotation/FontColumnTrimming/FontCentering
 * directly rather than re-porting them.
 *
 * SCOPE: only the `textSize == showHeight` case (no rescale) is ported.
 * CoolLEDU's rescale step is a genuinely different, larger mechanism than
 * CoolLEDUX's (a dozen-plus explicit `transfer<N>FontTo<M>` functions for
 * every sixe pair among {12,14,16,20,24,32}, not a single "pick nearest
 * table and scale up" rule) - reverse-engineering all of them is its own
 * sizable task, deliberately deferred rather than guessed at. Call sites
 * MUST reject textSize != showHeight until that's done (see
 * CoolleduStreamText/CoolleduCombineText).
 */
object CoolleduGlyphPipeline {
    /** Sizes with a verified single-glyph read path in this port; textSize must be one of these until FontGlyphRescale gains CoolLEDU's own transfer functions. */
    val supportedShowHeights = setOf(16)

    fun bytesPerColumnFor(size: Int): Int = (size + 7) / 8

    /**
     * Reads, rotates, and trims a single-character glyph for [content].
     * Requires content.textSize == content.showHeight (see class doc).
     */
    fun readAndShapeGlyph(codePoint: Int, content: CoolleduTextContentProgramContent): ByteArray {
        require(content.textSize == content.showHeight) {
            "CoolLEDU rescale (textSize=${content.textSize} != showHeight=${content.showHeight}) is not yet ported - see CoolleduGlyphPipeline's class doc"
        }
        val showHeight = content.showHeight
        var glyph = readNativeGlyph(codePoint, showHeight, content.isTextBold)
            ?: ByteArray(showHeight * bytesPerColumnFor(showHeight))

        glyph = FontBitmapRotation.rotate(content.textRotate, glyph, showHeight)
        glyph = FontColumnTrimming.deleteEmptyColumns(glyph, showHeight)
        if (content.textRotate == 90 || content.textRotate == 270) {
            val centered = FontCentering.processBytesCentered(glyph, squareSize = showHeight)
            glyph = FontColumnTrimming.deleteEmptyColumns(centered, showHeight)
        }
        return glyph
    }

    private fun readNativeGlyph(codePoint: Int, textSize: Int, isBold: Boolean): ByteArray? {
        val source = CoolleduxFontSources.active
        return when {
            textSize >= 32 -> source.readGlyph32(codePoint, isBold)
            textSize >= 16 -> source.readGlyph16(codePoint, isBold)
            textSize >= 14 && isBold -> source.readGlyph14Bold(codePoint)
            textSize >= 12 -> source.readGlyph12(codePoint, isBold)
            else -> source.readGlyph8(codePoint)
        }
    }
}
