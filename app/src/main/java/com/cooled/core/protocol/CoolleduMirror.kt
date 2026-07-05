package com.cooled.core.protocol

/**
 * Port of the isMirror post-processing at the tail of
 * FontUtils.getFontByteDataCoolleduForEmoji(...) (labels L355/L375/L379):
 *
 *  - Stream mode (mode NOT in {1,4-13}): `mirror(rendered, bytesPerColumn)`.
 *  - Combine mode (mode in {1,4-13}): after getCenteredDataBytes,
 *    `addAllSpiltedBytes(splitBytes(rendered, showWidth, bytesPerColumn), bytesPerColumn)`.
 *
 * Both reduce to the same primitive, FontUtils.mirror(byte[], int): reverse
 * the order of the fixed-width (bytesPerColumn-byte) columns of a column-major
 * glyph buffer, keeping the byte order *within* each column intact - a
 * horizontal flip of the rendered bitmap. Combine mode differs only in that it
 * first splits the canvas into whole display rows of `showWidth` columns
 * (zero-padding the final partial row up to a full row) and mirrors each row
 * independently, so the mirror is per-frame rather than across the whole blob.
 *
 * The r7==2 branch of FontUtils.mirror(byte[],int) is a confirmed jadx
 * control-flow decompile bug (the loop back-edge `goto :goto_0` was dropped,
 * making it look like it copies only the last column); verified against
 * reverse/apktool/smali_classes3/.../FontUtils.smali that all three of r7==2,
 * r7==3, r7==4 are real loops. The APK only implements r7 in {2,3,4} (it
 * returns an all-zero buffer for any other stride); our supported showHeights
 * (16 -> stride 2, 32 -> stride 4, and the 12/14/20/24 rescale intermediates)
 * only ever hit {2,3,4}.
 */
object CoolleduMirror {
    /** FontUtils.mirror(byte[], int): reverse the order of [bytesPerColumn]-byte columns. */
    fun mirror(bytes: ByteArray, bytesPerColumn: Int): ByteArray {
        require(bytesPerColumn in 2..4) {
            "CoolLEDU mirror is only defined for bytesPerColumn in 2..4 (matching FontUtils.mirror); got $bytesPerColumn"
        }
        val out = ByteArray(bytes.size)
        var i = 0
        // Matches the APK's `while (i <= bytes.length - bytesPerColumn)` loop
        // exactly, including its column-reversed source indexing.
        while (i <= bytes.size - bytesPerColumn) {
            for (b in 0 until bytesPerColumn) {
                out[i + b] = bytes[bytes.size - bytesPerColumn - i + b]
            }
            i += bytesPerColumn
        }
        return out
    }

    /**
     * Port of FontUtils.splitBytes(byte[], int showWidth, int bytesPerColumn):
     * zero-pad [bytes] up to a whole multiple of `showWidth*bytesPerColumn`
     * (a full display frame), then split into that-sized blocks.
     */
    fun splitIntoRows(bytes: ByteArray, showWidth: Int, bytesPerColumn: Int): List<ByteArray> {
        val rowBytes = showWidth * bytesPerColumn
        var padded = bytes
        if (padded.size % rowBytes != 0) {
            val padCount = rowBytes - (padded.size % rowBytes)
            padded = padded + ByteArray(padCount)
        }
        if (padded.isEmpty()) return listOf(ByteArray(0))
        val blocks = padded.size / rowBytes
        if (blocks == 0) return listOf(padded)
        return (0 until blocks).map { padded.copyOfRange(it * rowBytes, (it + 1) * rowBytes) }
    }

    /**
     * Port of FontUtils.addAllSpiltedBytes(List<byte[]>, int bytesPerColumn):
     * mirror each split row independently and concatenate.
     */
    fun mirrorRows(rows: List<ByteArray>, bytesPerColumn: Int): ByteArray {
        val out = mutableListOf<Byte>()
        rows.forEach { out += mirror(it, bytesPerColumn).toList() }
        return out.toByteArray()
    }

    /** Combine-mode mirror: split into display frames, mirror each. */
    fun mirrorCombine(bytes: ByteArray, showWidth: Int, bytesPerColumn: Int): ByteArray =
        mirrorRows(splitIntoRows(bytes, showWidth, bytesPerColumn), bytesPerColumn)
}
