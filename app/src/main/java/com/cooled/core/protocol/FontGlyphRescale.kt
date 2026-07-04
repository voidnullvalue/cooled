package com.cooled.core.protocol

/**
 * Port of FontUtils.transfer<N>FontTo<M>(byte[]) - the 15 functions that
 * rescale a raw font-table glyph read at its native size (8/12/14/16/20/24)
 * up to whatever larger size the actual `showHeight` needs.
 *
 * All 15 do the same three-step transform, verified by tracing every distinct
 * bytes-per-column transition (2->2, 2->3, 3->3, 3->4) in
 * reverse/apktool/smali_classes3/.../FontUtils.smali - not just one example
 * (see docs/APK_REVERSE_ENGINEERING_NOTES.md):
 *
 *  1. If the source and target sizes need a different bytes-per-column count
 *     (ceil(size/8)), each column is widened by appending zero bytes at the
 *     END of the column (extra blank rows at the bottom of the container).
 *  2. Every column's big-endian bit value is then shifted right by
 *     (toSize - fromSize) / 2 bits, zero-filled at the top (this is a
 *     multi-byte shift: the low bits shifted out of one byte carry into the
 *     high bits of the next byte in the same column).
 *  3. The glyph is padded with (toSize - fromSize) / 2 blank columns on each
 *     side (horizontal centering to the wider target advance width).
 *
 * The APK's decompiled bytecode masks more bits than strictly necessary in
 * some of the 15 variants (e.g. the 20->24 pair masks a 4-bit nibble even
 * though only a 2-bit shift is meaningful), but the extra masked bits are
 * always discarded by the subsequent byte-truncating store, so the general
 * shift-by-(delta/2)-bits formula below is exactly equivalent for every
 * variant, not just the ones it was directly checked against.
 */
object FontGlyphRescale {
    fun transfer(bytes: ByteArray, fromSize: Int, toSize: Int): ByteArray {
        val fromBytesPerColumn = (fromSize + 7) / 8
        val toBytesPerColumn = (toSize + 7) / 8
        val delta = toSize - fromSize
        require(delta >= 0) { "transfer only upsizes: fromSize=$fromSize toSize=$toSize" }
        require(bytes.size % fromBytesPerColumn == 0) {
            "expected a multiple of $fromBytesPerColumn bytes for fromSize=$fromSize, got ${bytes.size}"
        }
        if (delta == 0 && fromBytesPerColumn == toBytesPerColumn) return bytes.copyOf()

        val columns = bytes.size / fromBytesPerColumn
        var widened = ByteArray(columns * toBytesPerColumn)
        for (col in 0 until columns) {
            System.arraycopy(bytes, col * fromBytesPerColumn, widened, col * toBytesPerColumn, fromBytesPerColumn)
        }

        val shiftBits = delta / 2
        if (shiftBits > 0) {
            for (col in 0 until columns) {
                val base = col * toBytesPerColumn
                var carry = 0
                for (i in 0 until toBytesPerColumn) {
                    val value = widened[base + i].toInt() and 0xFF
                    val shifted = (value ushr shiftBits) or carry
                    carry = (value shl (8 - shiftBits)) and 0xFF
                    widened[base + i] = shifted.toByte()
                }
            }
        }

        val padColumns = delta / 2
        return FontCanvasWordWrap.addEmptyColumnsToTheLeft(
            FontCanvasWordWrap.addEmptyColumns(widened, padColumns, toBytesPerColumn),
            padColumns,
            toBytesPerColumn
        )
    }
}
