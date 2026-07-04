package com.cooled.core.protocol

/**
 * Port of FontUtils.getCenteredDataBytes/splitArray/dealDataWithCenteredN/
 * dealSegmentResultN (row centering within the combine-canvas "mode" branch)
 * and FontUtils.processBytesCenteredN (per-glyph centering used only for the
 * 90/270-degree text-rotation case). Hand-traced against
 * reverse/apktool/smali_classes3/.../FontUtils.smali; see
 * docs/APK_REVERSE_ENGINEERING_NOTES.md.
 *
 * Both mechanisms center content by padding with blank columns, but with
 * *opposite* rounding bias for an odd remainder, and processBytesCenteredN
 * has a genuine APK quirk: the 12px and 20px variants trim using the 14px
 * and 24px column-trim functions respectively (same byte-per-column stride,
 * but a different blank-glyph fallback width) rather than their own - this
 * looks like a copy/paste artifact in the original app, not a size family
 * that was deliberately shared, and is preserved here for exactness.
 */
object FontCentering {
    private fun bytesPerColumnFor(textSize: Int): Int = (textSize + 7) / 8

    /** Pad [bytes] with blank columns until it has [squareSize] columns, centered (extra column, if any, goes right). */
    fun padToSquareCentered(bytes: ByteArray, squareSize: Int, bytesPerColumn: Int): ByteArray {
        val currentColumns = bytes.size / bytesPerColumn
        val padTotal = squareSize - currentColumns
        if (padTotal <= 0) return bytes
        val padLeft = padTotal / 2
        val padRight = padTotal - padLeft
        return FontCanvasWordWrap.addEmptyColumnsToTheLeft(
            FontCanvasWordWrap.addEmptyColumns(bytes, padRight, bytesPerColumn),
            padLeft,
            bytesPerColumn
        )
    }

    /**
     * Port of FontUtils.processBytesCentered{8,12,14,16(unnamed),20,24,32}(byte[]).
     * [trimTextSize] is the textSize argument the APK actually passes to its
     * deleteEmptyColumnForN call after each rotation - equal to [squareSize]
     * except for the 12->14 and 20->24 quirk described above.
     */
    fun processBytesCentered(bytes: ByteArray, squareSize: Int, trimTextSize: Int = squareSize): ByteArray {
        val bytesPerColumn = bytesPerColumnFor(squareSize)
        val squared = padToSquareCentered(bytes, squareSize, bytesPerColumn)
        val rotated90 = FontBitmapRotation.rotate(90, squared, squareSize)
        val trimmed90 = FontColumnTrimming.deleteEmptyColumns(rotated90, trimTextSize)
        val reSquared = padToSquareCentered(trimmed90, squareSize, bytesPerColumn)
        val rotated270 = FontBitmapRotation.rotate(270, reSquared, squareSize)
        return FontColumnTrimming.deleteEmptyColumns(rotated270, trimTextSize)
    }

    /** Port of FontUtils.splitArray(byte[], int): chunk into groups of [chunkSize] bytes; the last chunk may be shorter. */
    fun splitArray(bytes: ByteArray, chunkSize: Int): List<ByteArray> = bytes.toList().chunked(chunkSize).map { it.toByteArray() }

    /**
     * Port of FontUtils.dealSegmentResultN(byte[], showWidth): center [bytes]
     * within row(s) of [showWidth] columns.
     *  - If [bytes] is already an exact multiple of one row width, it is
     *    returned unchanged (no centering needed).
     *  - If it's shorter than one row, it's centered within a single row
     *    with the odd leftover column (if any) going to the RIGHT.
     *  - If it spans one or more full rows plus a shorter final row, the
     *    full rows are left untouched and only the trailing partial row is
     *    centered, with the odd leftover column (if any) going to the LEFT.
     */
    fun centerWithinRow(bytes: ByteArray, showWidth: Int, bytesPerColumn: Int): ByteArray {
        val totalColumns = bytes.size / bytesPerColumn
        val remainder = totalColumns % showWidth
        if (remainder == 0) return bytes
        val fullRows = totalColumns / showWidth
        if (fullRows == 0) {
            val padTotal = showWidth - totalColumns
            val padLeft = padTotal / 2
            val padRight = padTotal - padLeft
            return FontCanvasWordWrap.addEmptyColumnsToTheLeft(
                FontCanvasWordWrap.addEmptyColumns(bytes, padRight, bytesPerColumn),
                padLeft,
                bytesPerColumn
            )
        }
        val headColumns = fullRows * showWidth
        val head = bytes.copyOfRange(0, headColumns * bytesPerColumn)
        val tail = bytes.copyOfRange(headColumns * bytesPerColumn, bytes.size)
        val padTotal = showWidth - remainder
        val padRight = padTotal / 2
        val padLeft = padTotal - padRight
        val centeredTail = FontCanvasWordWrap.addEmptyColumnsToTheLeft(
            FontCanvasWordWrap.addEmptyColumns(tail, padRight, bytesPerColumn),
            padLeft,
            bytesPerColumn
        )
        return head + centeredTail
    }

    /**
     * Port of FontUtils.dealDataWithCenteredN(byte[], textSize, showWidth):
     * trim leading/trailing blank columns from one row (no blank-glyph
     * fallback width here, unlike FontColumnTrimming.deleteEmptyColumns -
     * the APK leaves a fully-blank row as null/absent), then center what's
     * left within [showWidth] columns.
     */
    fun dealWithCenteredRow(row: ByteArray, showWidth: Int, bytesPerColumn: Int): ByteArray {
        val columnCount = row.size / bytesPerColumn
        fun isBlankColumn(col: Int): Boolean {
            val base = col * bytesPerColumn
            for (i in 0 until bytesPerColumn) if (row[base + i] != 0.toByte()) return false
            return true
        }
        var first = 0
        while (first < columnCount && isBlankColumn(first)) first++
        if (first >= columnCount) return ByteArray(0)
        var last = columnCount - 1
        while (last > first && isBlankColumn(last)) last--
        val trimmed = row.copyOfRange(first * bytesPerColumn, (last + 1) * bytesPerColumn)
        return centerWithinRow(trimmed, showWidth, bytesPerColumn)
    }

    /**
     * Port of FontUtils.getCenteredDataBytes(byte[] canvas, int textSize, int showWidth):
     * split the assembled multi-row canvas into rows of [showWidth] columns
     * and center each row independently (see [dealWithCenteredRow]).
     */
    fun getCenteredDataBytes(canvas: ByteArray, textSize: Int, showWidth: Int): ByteArray {
        val bytesPerColumn = bytesPerColumnFor(textSize)
        val rows = splitArray(canvas, showWidth * bytesPerColumn)
        return rows.fold(ByteArray(0)) { acc, row -> acc + dealWithCenteredRow(row, showWidth, bytesPerColumn) }
    }
}
