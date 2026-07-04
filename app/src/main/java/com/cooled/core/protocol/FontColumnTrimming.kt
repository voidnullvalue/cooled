package com.cooled.core.protocol

/**
 * Port of FontUtils.deleteEmptyColumnFor{8,12,14,16,20,24,32}(byte[]) and the
 * corresponding (byte[], int textSize) overloads.
 *
 * Every one of these size-specific methods in the APK does the exact same
 * thing with a different "bytes per column" stride (ceil(size/8): 1 for 8px,
 * 2 for 12/14/16px, 3 for 20/24px, 4 for 32px) and a different "blank glyph"
 * fallback width (always textSize/2 columns of zero bytes): trim leading and
 * trailing all-zero columns from a column-major glyph byte array, or return
 * textSize/2 blank columns if every column is blank. Recovered and verified
 * by hand-tracing all size variants in
 * reverse/apktool/smali_classes3/.../FontUtils.smali (all of them decompiled
 * cleanly in jadx's default mode; only getFontByteDataCoolleduxForEmoji and
 * the two rotate90Degree overloads needed -m simple - see
 * docs/APK_REVERSE_ENGINEERING_NOTES.md).
 */
object FontColumnTrimming {
    /** [textSize] is the original APK's font point size (8/12/14/16/20/24/32), not just row count. */
    fun deleteEmptyColumns(bytes: ByteArray, textSize: Int): ByteArray {
        val bytesPerColumn = (textSize + 7) / 8
        require(bytes.size % bytesPerColumn == 0) {
            "expected a multiple of $bytesPerColumn bytes for textSize=$textSize, got ${bytes.size}"
        }
        val columnCount = bytes.size / bytesPerColumn

        fun isBlankColumn(col: Int): Boolean {
            val base = col * bytesPerColumn
            for (i in 0 until bytesPerColumn) if (bytes[base + i] != 0.toByte()) return false
            return true
        }

        var first = 0
        while (first < columnCount && isBlankColumn(first)) first++
        if (first >= columnCount) {
            return ByteArray((textSize / 2) * bytesPerColumn)
        }
        var last = columnCount - 1
        while (last > first && isBlankColumn(last)) last--

        return bytes.copyOfRange(first * bytesPerColumn, (last + 1) * bytesPerColumn)
    }
}
