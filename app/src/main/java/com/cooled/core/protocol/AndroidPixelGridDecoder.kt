package com.cooled.core.protocol

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Production [PixelGridDecoder] using android.graphics.BitmapFactory, matching
 * FontUtils/TextEmojiManagerCoolLEDUX's own decode call
 * (`BitmapFactory.decodeResource(..., Options().apply { inScaled = false })`).
 * Not JVM-unit-testable (needs a real Android runtime or Robolectric); the
 * pixel-processing logic it feeds (EmojiGlyphEncoder) is tested separately
 * against synthetic PixelGrids, and against real extracted assets decoded via
 * a JVM-only ImageIO test double - see EmojiGlyphEncoderIntegrationTest.
 */
object AndroidPixelGridDecoder : PixelGridDecoder {
    override fun decode(bytes: ByteArray): PixelGrid? {
        val options = BitmapFactory.Options().apply { inScaled = false }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        return try {
            toPixelGrid(bitmap)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun toPixelGrid(bitmap: Bitmap): PixelGrid {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return PixelGrid(bitmap.width, bitmap.height) { x, y -> pixels[y * bitmap.width + x] }
    }
}
