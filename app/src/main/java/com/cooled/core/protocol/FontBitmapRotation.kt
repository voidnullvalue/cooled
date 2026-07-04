package com.cooled.core.protocol

/**
 * Port of FontUtils.rotate90Degree(byte[]) / rotate90Degree(byte[], int) /
 * rotate(int, byte[]) / rotate(int, byte[], int) from the original APK
 * (com.jtkj.led1248.light.utils.FontUtils).
 *
 * The APK stores a monochrome NxN glyph/frame as column-major bytes: each
 * column occupies ceil(size/8) bytes, MSB-first from row 0 downward. These
 * helpers rotate that bitmap 90 degrees clockwise (repeated for 180/270) by
 * unpacking to a row/col bit grid, applying the standard clockwise transpose
 * new[row][col] = old[size-1-col][row], and repacking in the same
 * column-major MSB-first layout.
 *
 * Recovered by hand-tracing reverse/apktool/smali_classes3/.../FontUtils.smali.
 * jadx failed to decompile these two methods under the default "auto" pass;
 * forcing "-m simple" on a single-class extract succeeded and its arithmetic
 * matches the smali control flow exactly. See docs/APK_REVERSE_ENGINEERING_NOTES.md.
 */
object FontBitmapRotation {
    fun rotate90Clockwise(bytes: ByteArray, size: Int): ByteArray {
        val bytesPerColumn = (size + 7) / 8
        require(bytes.size == size * bytesPerColumn) {
            "expected ${size * bytesPerColumn} bytes for a ${size}x$size bitmap, got ${bytes.size}"
        }

        val grid = Array(size) { BooleanArray(size) }
        for (col in 0 until size) {
            for (byteIndex in 0 until bytesPerColumn) {
                val value = bytes[col * bytesPerColumn + byteIndex].toInt() and 0xFF
                val rowBase = byteIndex * 8
                val bitsInByte = minOf(8, size - rowBase)
                for (bit in 0 until bitsInByte) {
                    if (((value shl bit) and 0x80) == 0x80) {
                        grid[rowBase + bit][col] = true
                    }
                }
            }
        }

        val rotated = Array(size) { row -> BooleanArray(size) { col -> grid[size - 1 - col][row] } }

        val out = ByteArray(size * bytesPerColumn)
        for (col in 0 until size) {
            for (byteIndex in 0 until bytesPerColumn) {
                val rowBase = byteIndex * 8
                val bitsInByte = minOf(8, size - rowBase)
                var value = 0
                for (bit in 0 until bitsInByte) {
                    if (rotated[rowBase + bit][col]) {
                        value = value or (0x80 ushr bit)
                    }
                }
                out[col * bytesPerColumn + byteIndex] = value.toByte()
            }
        }
        return out
    }

    /** Port of FontUtils.rotate(int, byte[], int) (and the fixed-16 rotate(int, byte[]) overload). */
    fun rotate(angleDegrees: Int, bytes: ByteArray, size: Int): ByteArray {
        return when (angleDegrees) {
            0, 360 -> bytes
            90 -> rotate90Clockwise(bytes, size)
            180 -> rotate90Clockwise(rotate90Clockwise(bytes, size), size)
            270 -> rotate90Clockwise(rotate90Clockwise(rotate90Clockwise(bytes, size), size), size)
            else -> error("Unsupported CoolLEDUX font rotation angle: $angleDegrees")
        }
    }
}
