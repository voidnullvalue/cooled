package com.cooled.core.protocol

/**
 * Per-glyph read/rescale/rotate/trim pipeline for
 * FontUtils.getFontByteDataCoolleduForEmoji (the CoolLEDU family's own text
 * renderer - distinct from, but structurally close to,
 * getFontByteDataCoolleduxForEmoji already ported as CoolleduxGlyphPipeline).
 * Confirmed by direct read of FontUtils.java that readFontData,
 * rotate(int,byte[],int), FontColumnTrimming's deleteEmptyColumnFor<N>, and
 * processBytesCentered are *single, shared* definitions in FontUtils.java used
 * by both the CoolLEDU and CoolLEDUX pipelines - not duplicated per family -
 * so this reuses CoolleduxFontSources/FontBitmapRotation/FontColumnTrimming/
 * FontCentering directly rather than re-porting them.
 *
 * RESCALE (now ported): the APK reads each glyph's font table at
 * `textSize` (startReadFontData/readFontData select the font-lib file purely
 * by textSize+isBold), and when `textSize != showHeight` upsizes the raw bytes
 * with FontUtils.transfer<textSize>FontTo<showHeight> *before* rotate/trim.
 * Those 15 transfer functions are the already-verified FontGlyphRescale.transfer
 * (see its class doc). The bold flag only chooses the font table read; the
 * rescale/rotate/trim tail is bit-identical for bold and non-bold (verified
 * against the L49/L117 and L83/L154 mirror-image branches of
 * getFontByteDataCoolleduForEmoji).
 *
 * SCOPE: showHeight in {16, 32} (both natively readable from the recovered
 * font assets and fully supported by the size-generic FontColumnTrimming/
 * FontCentering/FontCanvasWordWrap helpers). textSize must be a size the
 * recovered font source can actually read (32/16/12, plus 14 only when bold -
 * the recovered assets have no 14px-regular / 20px / 24px tables), and
 * textSize <= showHeight (transfer only ever upsizes). Emoji/image tokens
 * (the isText==false branch, whose glyph bytes come from per-size emojiData
 * lists loaded at runtime from separate binary assets, NOT the id/code/name
 * emotions_*.xml) remain unported - see CoolleduTextTokenizer.
 */
object CoolleduGlyphPipeline {
    /** showHeight values with a verified end-to-end shaping path in this port. */
    val supportedShowHeights = setOf(16, 32)

    fun bytesPerColumnFor(size: Int): Int = (size + 7) / 8

    /**
     * Reads, rescales, rotates, and trims a single-character glyph for [content].
     * Requires content.textSize be readable and <= content.showHeight (see class doc).
     */
    fun readAndShapeGlyph(codePoint: Int, content: CoolleduTextContentProgramContent): ByteArray {
        val showHeight = content.showHeight
        val textSize = content.textSize
        require(showHeight in supportedShowHeights) {
            "Unsupported CoolLEDU showHeight=$showHeight - see CoolleduGlyphPipeline's class doc"
        }
        require(textSize <= showHeight) {
            "CoolLEDU font rescale only upsizes (textSize=$textSize must be <= showHeight=$showHeight)"
        }
        require(isReadableTextSize(textSize, content.isTextBold)) {
            "CoolLEDU textSize=$textSize (bold=${content.isTextBold}) has no recovered font table - see CoolleduGlyphPipeline's class doc"
        }

        // FontUtils.readFontData(char, textSize) always returns a buffer sized
        // for textSize (it zero-fills when the font stream is unavailable), so
        // the fallback here is a blank glyph at *textSize* dimensions, exactly
        // like the APK - the transfer step below then widens it to showHeight.
        var glyph = readNativeGlyph(codePoint, textSize, content.isTextBold)
            ?: ByteArray(textSize * bytesPerColumnFor(textSize))

        if (textSize != showHeight) {
            glyph = FontGlyphRescale.transfer(glyph, textSize, showHeight)
        }

        glyph = FontBitmapRotation.rotate(content.textRotate, glyph, showHeight)
        glyph = FontColumnTrimming.deleteEmptyColumns(glyph, showHeight)
        if (content.textRotate == 90 || content.textRotate == 270) {
            val centered = FontCentering.processBytesCentered(glyph, squareSize = showHeight)
            glyph = FontColumnTrimming.deleteEmptyColumns(centered, showHeight)
        }
        return glyph
    }

    /**
     * Which (textSize, isBold) pairs the recovered CoolLEDU/CoolLEDUX font
     * assets can supply. Mirrors FontUtils.getFontLibName's dispatch, minus
     * the 14px-regular / 20px / 24px tables that this repo's asset extraction
     * doesn't (yet) provide a reader for.
     */
    private fun isReadableTextSize(textSize: Int, isBold: Boolean): Boolean = when (textSize) {
        32, 16, 12, 8 -> true
        14 -> isBold
        else -> false
    }

    private fun readNativeGlyph(codePoint: Int, textSize: Int, isBold: Boolean): ByteArray? {
        val source = CoolleduxFontSources.active
        // Exact-by-size table selection matching FontUtils.getFontLibName -
        // NOT a ">=" cascade, because rescale reads the *source* textSize table
        // (e.g. a 12px glyph for textSize=12), not the showHeight table.
        return when (textSize) {
            32 -> source.readGlyph32(codePoint, isBold)
            16 -> source.readGlyph16(codePoint, isBold)
            14 -> source.readGlyph14Bold(codePoint)
            12 -> source.readGlyph12(codePoint, isBold)
            else -> source.readGlyph8(codePoint)
        }
    }
}
