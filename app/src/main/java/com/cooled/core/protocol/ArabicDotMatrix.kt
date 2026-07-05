package com.cooled.core.protocol

/**
 * The pure, Android-free half of
 * com.jtkj.led1248.light.utils.ArabicCharDotMatrixGenerator: everything that
 * happens *after* a character has been rasterized to a pixel buffer by
 * Canvas/Paint/Typeface. Reading rasterized pixels back into a dot-matrix and
 * packing that into the column-major/MSB-first byte format the rest of this
 * repo already uses is plain integer arithmetic with no android.graphics
 * dependency, so - unlike the Canvas draw itself (see AndroidGlyphRasterizer)
 * - it is fully JVM-unit-testable against a hand-constructed [PixelGrid], and
 * has a golden vector (ArabicDotMatrixTest) exactly like the other byte-level
 * primitives ported in this package.
 *
 * Ported from ArabicCharDotMatrixGenerator.java:
 *  - bitmapToDotMatrix(Bitmap, int)      -> [bitmapToDotMatrix] (lines 682-709)
 *  - bitmapToDotMatrixVi(Bitmap, int)    -> [bitmapToDotMatrixVi] (lines 711-738)
 *  - dotMatrixToBytes(byte[][])          -> [dotMatrixToBytes] (lines 740-770)
 */
object ArabicDotMatrix {
    /**
     * Port of ArabicCharDotMatrixGenerator.bitmapToDotMatrix(Bitmap, int)
     * (lines 682-709). Builds a `[height][width]` matrix of 0/1: a cell is 1
     * ("lit") when the pixel's grayscale average `(R+G+B)/3` is strictly less
     * than [threshold] (the APK always passes 128). Because the non-Vietnamese
     * draw path paints black text on a white canvas, "dark enough" pixels
     * become lit dots.
     */
    fun bitmapToDotMatrix(grid: PixelGrid, threshold: Int): Array<ByteArray> {
        val width = grid.width
        val height = grid.height
        val matrix = Array(height) { ByteArray(width) }
        for (row in 0 until height) {
            for (col in 0 until width) {
                val argb = grid[col, row]
                val red = (argb ushr 16) and 0xFF
                val green = (argb ushr 8) and 0xFF
                val blue = argb and 0xFF
                val gray = (red + green + blue) / 3
                matrix[row][col] = if (gray < threshold) 1 else 0
            }
        }
        return matrix
    }

    /**
     * Port of ArabicCharDotMatrixGenerator.bitmapToDotMatrixVi(Bitmap, int)
     * (lines 711-738). The Vietnamese draw path instead paints *white* text on
     * a *black* canvas, so the test is inverted: a cell is 1 when `R+G+B > 0`
     * (any non-black pixel). The APK's `threshold` argument is accepted but
     * unused here, exactly as in the original (it only reads `R+G+B <= 0`).
     */
    fun bitmapToDotMatrixVi(grid: PixelGrid): Array<ByteArray> {
        val width = grid.width
        val height = grid.height
        val matrix = Array(height) { ByteArray(width) }
        for (row in 0 until height) {
            for (col in 0 until width) {
                val argb = grid[col, row]
                val red = (argb ushr 16) and 0xFF
                val green = (argb ushr 8) and 0xFF
                val blue = argb and 0xFF
                val sum = red + green + blue
                matrix[row][col] = if (sum > 0) 1 else 0
            }
        }
        return matrix
    }

    /**
     * Port of ArabicCharDotMatrixGenerator.dotMatrixToBytes(byte[][])
     * (lines 740-770). Packs the `[height][width]` matrix column-major,
     * MSB-first, with `bytesPerColumn = ceil(height/8)` - the identical
     * convention FontColumnTrimming/FontBitmapRotation/FontCanvasWordWrap
     * already use for font-table glyphs, which is why a runtime-drawn glyph
     * drops straight into the same rotate/trim/word-wrap pipeline as a
     * table-read one (see CoolleduxGlyphPipeline.shapeTail).
     */
    fun dotMatrixToBytes(matrix: Array<ByteArray>): ByteArray {
        val height = matrix.size
        val width = matrix[0].size
        val bytesPerColumn = (height + 7) / 8
        val out = ByteArray(width * bytesPerColumn)
        for (col in 0 until width) {
            for (byteIndex in 0 until bytesPerColumn) {
                var value = 0
                for (bit in 0 until 8) {
                    val row = byteIndex * 8 + bit
                    if (row >= height) break
                    if (matrix[row][col].toInt() == 1) {
                        value = value or (1 shl (7 - bit))
                    }
                }
                out[col * bytesPerColumn + byteIndex] = value.toByte()
            }
        }
        return out
    }

    /**
     * Convenience wrapper matching ArabicCharDotMatrixGenerator.generateDotMatrix's
     * tail: pick the Vietnamese vs. normal sampling rule by [vietnamese]
     * (`isVi(languageCode)` at the call site), then pack to bytes. Kept pure so
     * AndroidGlyphRasterizer only has to produce the [PixelGrid].
     */
    fun rasterizePixels(grid: PixelGrid, vietnamese: Boolean, threshold: Int = 128): ByteArray {
        val matrix = if (vietnamese) bitmapToDotMatrixVi(grid) else bitmapToDotMatrix(grid, threshold)
        return dotMatrixToBytes(matrix)
    }
}
