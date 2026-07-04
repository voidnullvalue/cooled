package com.cooled.core.protocol

/**
 * Port of FontUtils.checkSegment{8,12,14,16,24,32}(canvas, glyph, showWidth, textSpacing)
 * and FontUtils.addEmptyColumnForData{8,12,14,16,24,32}ToThe{Left,Right}(bytes[, count]).
 *
 * These lay successive glyphs onto a single column-major canvas byte buffer
 * that wraps at `showWidth` columns - i.e. this is the APK's word-wrap logic
 * for the "combine canvas" text/mode branch (modes 1, 4-13; see
 * docs/APK_REVERSE_ENGINEERING_NOTES.md), not simple concatenation:
 *   - If the canvas currently ends exactly on a row boundary, the next glyph
 *     is appended with no gap.
 *   - Otherwise, if the next glyph (plus one `textSpacing` gap) does NOT fit
 *     in the remaining columns of the current row, the current row is padded
 *     out with blank columns first, so the glyph starts at the next row.
 *   - Otherwise, exactly `textSpacing` blank columns are inserted before the
 *     glyph (inter-glyph spacing within a row).
 *
 * All six size variants in the APK are byte-identical modulo the
 * size-specific "bytes per column" stride (ceil(size/8)), hand-verified
 * against reverse/apktool/smali_classes3/.../FontUtils.smali (these
 * decompiled cleanly in jadx's default mode - unlike
 * getFontByteDataCoolleduxForEmoji itself).
 */
object FontCanvasWordWrap {
    /**
     * @param canvas existing column-major canvas bytes, or null for the first glyph on the canvas.
     * @param glyph column-major glyph bytes to append.
     * @param showWidth row width in columns that the canvas wraps at.
     * @param textSpacing blank columns to insert between glyphs that share a row.
     * @param bytesPerColumn ceil(textSize/8): 1 for 8px, 2 for 12/14/16px, 3 for 20/24px, 4 for 32px.
     */
    fun checkSegment(canvas: ByteArray?, glyph: ByteArray, showWidth: Int, textSpacing: Int, bytesPerColumn: Int): ByteArray {
        if (canvas == null) return glyph
        require(canvas.size % bytesPerColumn == 0) { "canvas size ${canvas.size} not a multiple of $bytesPerColumn" }
        require(glyph.size % bytesPerColumn == 0) { "glyph size ${glyph.size} not a multiple of $bytesPerColumn" }

        val glyphColumns = glyph.size / bytesPerColumn
        val posInRow = (canvas.size / bytesPerColumn) % showWidth
        if (posInRow == 0) {
            return canvas + glyph
        }
        val remainingInRow = showWidth - posInRow
        val padded = if (remainingInRow < glyphColumns + textSpacing) {
            addEmptyColumns(canvas, remainingInRow, bytesPerColumn)
        } else {
            addEmptyColumns(canvas, textSpacing, bytesPerColumn)
        }
        return padded + glyph
    }

    fun addEmptyColumns(bytes: ByteArray, count: Int, bytesPerColumn: Int): ByteArray =
        bytes + ByteArray(count * bytesPerColumn)

    fun addEmptyColumnsToTheLeft(bytes: ByteArray, count: Int, bytesPerColumn: Int): ByteArray =
        ByteArray(count * bytesPerColumn) + bytes
}
