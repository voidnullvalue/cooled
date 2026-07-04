package com.cooled.core.protocol

/**
 * Platform-agnostic pixel grid: [argb] returns a packed 0xAARRGGBB pixel,
 * same convention as android.graphics.Color / java.awt.image.BufferedImage.getRGB.
 * Decoding actual image bytes into one of these is platform-specific (see
 * AndroidEmojiBitmapDecoder for the real android.graphics.BitmapFactory-backed
 * production path); everything in this file operates only on already-decoded
 * pixels, so it's fully JVM-unit-testable without Robolectric or a device.
 */
class PixelGrid(val width: Int, val height: Int, private val argb: (x: Int, y: Int) -> Int) {
    operator fun get(x: Int, y: Int): Int = argb(x, y)
}

/**
 * Port of TextEmojiManagerCoolLEDUX.getDrawItemsFromBitmap(name, srcSize, showSize)
 * and getImageData(...), and CoolledUXUtils.getDrawListDataFColor(...) as applied
 * to a decoded emoji/icon bitmap rather than a hand-drawn graffiti canvas.
 *
 * Both `getImageData` and `getDrawListDataFColor` need the SAME centered pixel
 * grid: a `showSize x showSize` canvas, opaque-black background, with the
 * decoded `srcSize x srcSize` source image pasted centered at
 * `((showSize-srcSize)/2, (showSize-srcSize)/2)` (verbatim if srcSize == showSize).
 */
object EmojiGlyphEncoder {
    /** Centers [source] onto an opaque-black [showSize]x[showSize] canvas; returns [source] unchanged if already that size. */
    fun centerOnBlackCanvas(source: PixelGrid, showSize: Int): PixelGrid {
        if (source.width == showSize && source.height == showSize) return source
        val offset = (showSize - source.width) / 2
        val black = 0xFF000000.toInt()
        return PixelGrid(showSize, showSize) { x, y ->
            val sx = x - offset
            val sy = y - offset
            if (sx in 0 until source.width && sy in 0 until source.height) source[sx, sy] else black
        }
    }

    /**
     * Port of TextEmojiManagerCoolLEDUX.getImageData(...): one bit per pixel,
     * "on" (1) if the pixel's RGB is non-black (alpha is not consulted, matching
     * the APK exactly), packed column-major MSB-first with
     * bytesPerColumn = ceil(height/8) - the same convention as
     * FontColumnTrimming/FontBitmapRotation, so a decoded emoji's monochrome
     * silhouette can go through the exact same rotate/trim/word-wrap pipeline
     * as a font glyph.
     */
    fun toMonochromeColumns(grid: PixelGrid): ByteArray {
        val bytesPerColumn = (grid.height + 7) / 8
        val out = ByteArray(grid.width * bytesPerColumn)
        for (x in 0 until grid.width) {
            for (byteIndex in 0 until bytesPerColumn) {
                val rowBase = byteIndex * 8
                val bitsInByte = minOf(8, grid.height - rowBase)
                var value = 0
                for (bit in 0 until bitsInByte) {
                    val argb = grid[x, rowBase + bit]
                    val isOn = ((argb ushr 16) and 0xFF) != 0 || ((argb ushr 8) and 0xFF) != 0 || (argb and 0xFF) != 0
                    if (isOn) value = value or (0x80 ushr bit)
                }
                out[x * bytesPerColumn + byteIndex] = value.toByte()
            }
        }
        return out
    }

    /**
     * Port of CoolledUXUtils.getDrawListDataFColor(...) as applied to a decoded
     * bitmap: column-major, 2 bytes/pixel via
     * TextEmojiManagerCoolLEDUX.getColorDataWithColorWithRGB444Transfer
     * (reused here as OriginalLedAssetPayloadEncoder.rgb444TransferColorBytes,
     * already verified against ground truth for the graffiti/GIF wrappers).
     */
    fun toRgb444Columns(grid: PixelGrid): ByteArray {
        val out = ByteArray(grid.width * grid.height * 2)
        var pos = 0
        for (x in 0 until grid.width) {
            for (y in 0 until grid.height) {
                val argb = grid[x, y]
                val alpha = (argb ushr 24) and 0xFF
                val encoded = OriginalLedAssetPayloadEncoder.rgb444TransferColorBytes(argb, alpha)
                out[pos++] = encoded[0]
                out[pos++] = encoded[1]
            }
        }
        return out
    }

    /**
     * Port of FontUtils.rotate90Degree(List&lt;DrawView.DrawItem&gt;, int) - the
     * List/color-based rotation image tokens use, as opposed to the
     * bit-packed byte-array rotation FontBitmapRotation implements for text
     * glyphs. Hand-traced from
     * reverse/jadx/sources/.../FontUtils.java (decompiled cleanly, unlike the
     * byte-array overload of the same name): rebuilding the index algebra
     * (`dest[row][col] = source[size-1-col][row]`) shows it's the exact same
     * clockwise-90-degree transpose as the byte-array version, just applied
     * to whole ARGB pixels instead of packed bits - so there's one shared
     * rotation formula for both representations in this port, even though
     * the APK implements them as two unrelated-looking functions.
     */
    fun rotate90Clockwise(grid: PixelGrid): PixelGrid {
        val size = grid.width
        require(grid.height == size) { "rotate90Clockwise requires a square grid, got ${grid.width}x${grid.height}" }
        // PixelGrid.get(x, y) uses x=column, y=row. The proven formula
        // (matching FontBitmapRotation.rotate90Clockwise) is
        // dest[row][col] = source[size-1-col][row]; substituting x=col,y=row
        // throughout gives dest.get(x,y) = source.get(x=y, y=size-1-x).
        return PixelGrid(size, size) { x, y -> grid[y, size - 1 - x] }
    }

    /** Port of FontUtils.rotate(int, List&lt;DrawView.DrawItem&gt;, int) - repeated rotate90Clockwise for 90/180/270. */
    fun rotateImage(angleDegrees: Int, grid: PixelGrid): PixelGrid = when (angleDegrees) {
        0, 360 -> grid
        90 -> rotate90Clockwise(grid)
        180 -> rotate90Clockwise(rotate90Clockwise(grid))
        270 -> rotate90Clockwise(rotate90Clockwise(rotate90Clockwise(grid)))
        else -> error("Unsupported CoolLEDUX image rotation angle: $angleDegrees")
    }

    /**
     * Port of CoolledUXUtils.getDrawListDataColorAndDeleteEmptyColumn(items, width, height):
     * trims leading/trailing all-blank columns and RGB444-encodes only the
     * surviving columns, column-major. A pixel is "blank" here if its ARGB
     * int is *exactly* opaque-black (0xFF000000) or exactly zero - a
     * different (narrower) check than [toMonochromeColumns]'s "RGB is
     * non-zero regardless of alpha": a pixel with, say, 50%-alpha black
     * would count as blank for [toMonochromeColumns] but not here. This is a
     * genuine APK inconsistency between the two functions, not a bug in this
     * port - see docs/APK_REVERSE_ENGINEERING_NOTES.md. In practice GIF
     * assets only have 1-bit transparency (fully opaque or fully
     * transparent), so this rarely matters for the bundled emoji/icon assets.
     *
     * Unlike font-glyph trimming, there is no "entirely blank" fallback
     * width here - an all-blank grid yields an empty result, matching the
     * APK (whose loop simply never executes when firstNonBlank > lastNonBlank).
     */
    fun toTrimmedRgb444Columns(grid: PixelGrid): ByteArray {
        fun isBlank(argb: Int) = argb == 0xFF000000.toInt() || argb == 0
        fun columnIsBlank(x: Int) = (0 until grid.height).all { y -> isBlank(grid[x, y]) }

        var first = 0
        while (first < grid.width && columnIsBlank(first)) first++
        if (first >= grid.width) return ByteArray(0)
        var last = grid.width - 1
        while (last > first && columnIsBlank(last)) last--

        val out = mutableListOf<Byte>()
        for (x in first..last) {
            for (y in 0 until grid.height) {
                val argb = grid[x, y]
                val alpha = (argb ushr 24) and 0xFF
                out += OriginalLedAssetPayloadEncoder.rgb444TransferColorBytes(argb, alpha).toList()
            }
        }
        return out.toByteArray()
    }
}
