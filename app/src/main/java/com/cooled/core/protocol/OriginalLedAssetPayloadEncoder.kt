package com.cooled.core.protocol

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.cooled.core.assets.OriginalLedAssetByteSources
import com.cooled.core.assets.OriginalLedAssetKinds

data class EncodedOriginalLedAssetPayload(
    val bytes: ByteArray,
    val format: String,
    val width: Int?,
    val height: Int?
)

object OriginalLedAssetPayloadEncoder {
    fun encode(assetPath: String, kind: String, displayColumns: Int?, displayRows: Int?): EncodedOriginalLedAssetPayload {
        val raw = OriginalLedAssetByteSources.active.read(assetPath) ?: ByteArray(0)
        val targetWidth = displayColumns?.coerceIn(8, 512) ?: 128
        val targetHeight = displayRows?.coerceIn(8, 128) ?: 32
        if (shouldRasterize(assetPath, kind)) {
            decodeBitmap(raw)?.let { bitmap ->
                return try {
                    val scaled = if (bitmap.width == targetWidth && bitmap.height == targetHeight) bitmap else Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                    val encoded = when (kind.lowercase()) {
                        OriginalLedAssetKinds.ICON, OriginalLedAssetKinds.EMOJI -> bitmapToArgbMaskAndRgb888(scaled)
                        else -> bitmapToRgb888(scaled)
                    }
                    EncodedOriginalLedAssetPayload(
                        bytes = encoded,
                        format = if (kind.lowercase() == OriginalLedAssetKinds.ICON || kind.lowercase() == OriginalLedAssetKinds.EMOJI) "argb-mask-rgb888" else "rgb888",
                        width = targetWidth,
                        height = targetHeight
                    )
                } finally {
                    if (bitmap.isRecycled.not()) bitmap.recycle()
                }
            }
        }
        return EncodedOriginalLedAssetPayload(raw, "raw", null, null)
    }

    private fun shouldRasterize(assetPath: String, kind: String): Boolean {
        val lowerPath = assetPath.lowercase()
        if (lowerPath.endsWith(".jt") || lowerPath.endsWith(".bin") || lowerPath.endsWith(".json")) return false
        return lowerPath.endsWith(".png") || lowerPath.endsWith(".webp") || lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg") || lowerPath.endsWith(".bmp") || lowerPath.endsWith(".gif")
    }

    private fun decodeBitmap(bytes: ByteArray): Bitmap? {
        if (bytes.isEmpty()) return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun bitmapToRgb888(bitmap: Bitmap): ByteArray {
        val out = ByteArray(bitmap.width * bitmap.height * 3)
        var pos = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val px = bitmap.getPixel(x, y)
                val alpha = (px ushr 24) and 0xFF
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                out[pos++] = if (alpha == 0) 0 else r.toByte()
                out[pos++] = if (alpha == 0) 0 else g.toByte()
                out[pos++] = if (alpha == 0) 0 else b.toByte()
            }
        }
        return out
    }

    private fun bitmapToArgbMaskAndRgb888(bitmap: Bitmap): ByteArray {
        val maskBytesPerRow = (bitmap.width + 7) / 8
        val mask = ByteArray(maskBytesPerRow * bitmap.height)
        val rgb = ByteArray(bitmap.width * bitmap.height * 3)
        var rgbPos = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val px = bitmap.getPixel(x, y)
                val alpha = (px ushr 24) and 0xFF
                if (alpha >= 16) {
                    val maskIndex = y * maskBytesPerRow + (x / 8)
                    mask[maskIndex] = (mask[maskIndex].toInt() or (0x80 ushr (x % 8))).toByte()
                }
                rgb[rgbPos++] = if (alpha == 0) 0 else ((px ushr 16) and 0xFF).toByte()
                rgb[rgbPos++] = if (alpha == 0) 0 else ((px ushr 8) and 0xFF).toByte()
                rgb[rgbPos++] = if (alpha == 0) 0 else (px and 0xFF).toByte()
            }
        }
        return mask + rgb
    }
}
