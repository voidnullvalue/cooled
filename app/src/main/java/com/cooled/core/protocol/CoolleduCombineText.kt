package com.cooled.core.protocol

/**
 * Port of the "combine canvas" branch of FontUtils.getFontByteDataCoolleduForEmoji(...)
 * (mode in {1,4,5,6,7,8,9,10,11,12,13}, the same set CoolLEDUX uses).
 *
 * Simpler than CoolleduxCombineText: since CoolLEDU's output has no
 * per-glyph [colCount][itemType] framing at all (just a flat byte blob),
 * there's no need for CoolLEDUX's byte-pattern-matching search step to
 * re-derive per-glyph output chunks from the finished canvas - the
 * word-wrapped, row-centered canvas *is* the final output, returned as-is.
 * Confirmed by direct read of FontUtils.getFontByteDataCoolleduForEmoji:
 * after getCenteredDataBytes, the only remaining step (skipped here - see
 * class scope) is the isMirror post-processing.
 *
 * SCOPE: text-only (see CoolleduTextTokenizer), textSize == showHeight (see
 * CoolleduGlyphPipeline), isMirror == false.
 */
object CoolleduCombineText {
    fun encode(content: CoolleduTextContentProgramContent): ByteArray {
        require(content.showHeight in CoolleduGlyphPipeline.supportedShowHeights) { "Unsupported CoolLEDU showHeight: ${content.showHeight}" }
        require(!content.isMirror) { "CoolLEDU isMirror is not yet ported for the combine-canvas text path" }
        val bytesPerColumn = CoolleduGlyphPipeline.bytesPerColumnFor(content.showHeight)

        val tokens = CoolleduTextTokenizer.tokenize(content.text)
        var canvas: ByteArray? = null
        for (token in tokens) {
            require(token.length == 1) { "CoolLEDU multi-character tokens are not expected from the text-only tokenizer" }
            val glyph = CoolleduGlyphPipeline.readAndShapeGlyph(token[0].code, content)
            canvas = FontCanvasWordWrap.checkSegment(canvas, glyph, content.showWidth, content.textSpacing, bytesPerColumn)
        }

        return FontCentering.getCenteredDataBytes(canvas ?: ByteArray(0), content.showHeight, content.showWidth)
    }
}
