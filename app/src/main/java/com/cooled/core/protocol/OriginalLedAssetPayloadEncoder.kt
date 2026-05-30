package com.cooled.core.protocol

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.cooled.core.assets.OriginalLedAssetByteSources
import com.cooled.core.assets.OriginalLedAssetKinds
import com.cooled.core.assets.OriginalLedAssetUploadRules

data class EncodedOriginalLedAssetPayload(
    val bytes: ByteArray,
    val format: String,
    val width: Int?,
    val height: Int?
)

object OriginalLedAssetPayloadEncoder {
    fun encode(assetPath: String, kind: String, displayColumns: Int?, displayRows: Int?): EncodedOriginalLedAssetPayload {
        val check = OriginalLedAssetUploadRules.check(assetPath, kind)
        require(check.uploadable) { "Original asset is not uploadable: ${check.reason}" }
        val raw = OriginalLedAssetByteSources.active.read(assetPath) ?: ByteArray(0)
        val targetWidth = displayColumns?.coerceIn(8, 512) ?: 128
        val targetHeight = displayRows?.coerceIn(8, 128) ?: 32
        val lowerPath = assetPath.lowercase()
        if (kind.equals(OriginalLedAssetKinds.ANIMATION, ignoreCase = true) || lowerPath.endsWith(".gif")) {
            return EncodedOriginalLedAssetPayload(raw, "raw-gif", null, null)
        }
        if (shouldRasterize(assetPath)) {
            decodeBitmap(raw)?.let { bitmap ->
                return try {
                    val scaled = if (bitmap.width == targetWidth && bitmap.height == targetHeight) bitmap else Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                    EncodedOriginalLedAssetPayload(
                        bytes = bitmapToRgb444TransferColumnMajor(scaled),
                        format = "rgb444-transfer-column-major",
                        width = targetWidth,
                        height = targetHeight
                    )
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
        }
        return EncodedOriginalLedAssetPayload(raw, "raw", null, null)
    }

    private fun shouldRasterize(assetPath: String): Boolean {
        val lowerPath = assetPath.lowercase()
        if (lowerPath.endsWith(".jt")) return false
        return lowerPath.endsWith(".png") || lowerPath.endsWith(".webp") || lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg") || lowerPath.endsWith(".bmp")
    }

    private fun decodeBitmap(bytes: ByteArray): Bitmap? {
        if (bytes.isEmpty()) return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /**
     * Mirrors CoolledUXUtils.getDrawListDataFColor(...): column-major pixels, each
     * encoded using TextEmojiManagerCoolLEDUX's RGB444 transfer byte pair.
     */
    private fun bitmapToRgb444TransferColumnMajor(bitmap: Bitmap): ByteArray {
        val out = ByteArray(bitmap.width * bitmap.height * 2)
        var pos = 0
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                val px = bitmap.getPixel(x, y)
                val alpha = (px ushr 24) and 0xFF
                val encoded = rgb444TransferColorBytes(px, alpha)
                out[pos++] = encoded[0]
                out[pos++] = encoded[1]
            }
        }
        return out
    }

    internal fun rgb444TransferColorBytes(argb: Int, alpha: Int = (argb ushr 24) and 0xFF): ByteArray {
        val r = if (alpha == 0) 0 else rgb444Transfer((argb ushr 16) and 0xFF)
        val g = if (alpha == 0) 0 else rgb444Transfer((argb ushr 8) and 0xFF)
        val b = if (alpha == 0) 0 else rgb444Transfer(argb and 0xFF)
        return byteArrayOf(r.toByte(), ((g shl 4) or b).toByte())
    }

    private fun rgb444Transfer(channel: Int): Int = when {
        channel >= 238 -> 15
        channel <= 47 -> 0
        else -> ((channel - 47) / 14) + 1
    }
}
