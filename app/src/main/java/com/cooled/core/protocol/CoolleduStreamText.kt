package com.cooled.core.protocol

/**
 * Port of the "stream" branch of FontUtils.getFontByteDataCoolleduForEmoji(...)
 * (mode NOT in {1,4,5,6,7,8,9,10,11,12,13} - the same combine-vs-stream mode
 * split as CoolLEDUX, confirmed by direct read of the dispatch). Each glyph
 * except the last gets `textSpacing` trailing blank columns, then all
 * glyphs are simply concatenated - no per-glyph [colCount][itemType]
 * framing at all, unlike CoolLEDUX. The caller
 * (CoolledUUtils.getDataWithTextContentProgramContent) wraps the result in
 * just a 4-byte length prefix.
 *
 * SCOPE: text-only (see CoolleduTextTokenizer), textSize == showHeight (see
 * CoolleduGlyphPipeline), isMirror == false (the real mirror step differs
 * meaningfully between stream and combine mode and isn't ported yet).
 */
object CoolleduStreamText {
    fun encode(content: CoolleduTextContentProgramContent): ByteArray {
        require(content.showHeight in CoolleduGlyphPipeline.supportedShowHeights) { "Unsupported CoolLEDU showHeight: ${content.showHeight}" }
        require(!content.isMirror) { "CoolLEDU isMirror is not yet ported for the stream-mode text path" }
        val bytesPerColumn = CoolleduGlyphPipeline.bytesPerColumnFor(content.showHeight)

        val tokens = CoolleduTextTokenizer.tokenize(content.text)
        val chunks = mutableListOf<Byte>()

        tokens.forEachIndexed { index, token ->
            require(token.length == 1) { "CoolLEDU multi-character tokens are not expected from the text-only tokenizer" }
            var glyph = CoolleduGlyphPipeline.readAndShapeGlyph(token[0].code, content)
            if (index != tokens.lastIndex) {
                glyph = FontCanvasWordWrap.addEmptyColumns(glyph, content.textSpacing, bytesPerColumn)
            }
            chunks += glyph.toList()
        }
        return chunks.toByteArray()
    }
}
