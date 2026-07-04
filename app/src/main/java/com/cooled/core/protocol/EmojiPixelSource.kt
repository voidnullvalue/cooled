package com.cooled.core.protocol

import com.cooled.core.assets.OriginalLedAssetByteSources

/** Decodes already-read image bytes (GIF/PNG) into a [PixelGrid]. Platform-specific - see AndroidPixelGridDecoder. */
interface PixelGridDecoder {
    fun decode(bytes: ByteArray): PixelGrid?
}

object PixelGridDecoders {
    @Volatile
    var active: PixelGridDecoder = UnavailablePixelGridDecoder
}

object UnavailablePixelGridDecoder : PixelGridDecoder {
    override fun decode(bytes: ByteArray): PixelGrid? = null
}

/**
 * Resolves a CoolLEDUX emoji/icon resource name (e.g. "emoji_fc_16x16_60",
 * matching TextEmojiTokenizer.Token.imageNameBySize values) to its extracted
 * asset bytes. These were only ever present as an API/density-qualified
 * Android resource (res/drawable-xxhdpi-v4 in the original APK, no plain
 * res/drawable or -nodpi fallback exists for them - see
 * tools/apk-re/extract-coolled-apk-assets.sh), and are either .gif or .png
 * depending on the specific asset.
 */
object EmojiAssetPaths {
    private const val DENSITY_FOLDER = "res/drawable-xxhdpi-v4"

    fun readImageBytes(imageName: String): ByteArray? {
        val source = OriginalLedAssetByteSources.active
        return source.read("$DENSITY_FOLDER/$imageName.gif") ?: source.read("$DENSITY_FOLDER/$imageName.png")
    }
}

/**
 * Port of TextEmojiManagerCoolLEDUX.getDrawItemsFromBitmap(name, srcSize, showSize)
 * (and its single-arg overload, which is just this with srcSize == showSize):
 * decode the named resource, then center it on a showSize x showSize
 * opaque-black canvas via EmojiGlyphEncoder.centerOnBlackCanvas. Returns null
 * on any decode failure, matching the APK's own try/catch-and-return-empty
 * fallback (readAndShapeGlyph-equivalent callers treat a null the same way
 * FontUtils.readFontData's blank-glyph fallback is treated elsewhere in this
 * port - see docs/APK_REVERSE_ENGINEERING_NOTES.md).
 */
object EmojiTokenGlyphs {
    fun decodeAndCenter(imageName: String, showSize: Int): PixelGrid? {
        val bytes = EmojiAssetPaths.readImageBytes(imageName) ?: return null
        val decoded = PixelGridDecoders.active.decode(bytes) ?: return null
        return EmojiGlyphEncoder.centerOnBlackCanvas(decoded, showSize)
    }
}
