package com.cooled.core.protocol

/**
 * Abstraction over ArabicCharDotMatrixGenerator's runtime glyph *rasterization*
 * (readFontDataFromDraw -> generateDotMatrix -> drawSingleChar/drawArabicContent
 * -> bitmapToDotMatrix -> dotMatrixToBytes). The actual draw needs a live
 * android.graphics Canvas/Paint/Typeface, so the concrete implementation
 * (AndroidGlyphRasterizer) can only run on-device / under Robolectric - hence
 * this indirection, mirroring how PixelGridDecoders/CoolleduxFontSources swap a
 * platform-specific backend behind a JVM-safe default. The pixel-readback math
 * both share (ArabicDotMatrix) is pure and JVM-tested.
 *
 * Every method returns the glyph in the SAME column-major/MSB-first,
 * `bytesPerColumn = ceil(height/8)` byte format as font-table glyphs, so the
 * result feeds straight into CoolleduxGlyphPipeline.shapeTail (rotate/trim)
 * exactly like a table-read glyph does in FontUtils.getFontByteDataCoolleduxForEmoji.
 */
interface GlyphRasterizer {
    /**
     * Port of the single-character draw path:
     * ArabicCharDotMatrixGenerator.readFontDataFromDraw(lang, char, size, textSize, bold)
     * (the 5-arg char overload, ArabicCharDotMatrixGenerator.java:200-202) ->
     * generateDotMatrix (lines 261-270) -> drawSingleChar (the 6-arg overload,
     * lines 525-620). [size] is the square bitmap dimension (== showHeight at
     * the FontUtils call site, FontUtils.java:11616); [textSize] is the Paint
     * point size; the typeface is chosen from the character's script via
     * getFontByInput.
     */
    fun drawChar(languageCode: String, char: Char, size: Int, textSize: Int, bold: Boolean): ByteArray

    /**
     * Port of the multi-character (whole-segment) draw path:
     * ArabicCharDotMatrixGenerator.readFontDataFromDraw(lang, String, width, height, textSize, bold)
     * (the String overload, ArabicCharDotMatrixGenerator.java:204-206) ->
     * generateDotMatrix (lines 272-274) -> drawArabicContent (the 7-arg
     * overload, lines 622-652). Used for the multi-character Arabic/Hebrew word
     * segments the script tokenizer emits ([width] == text.length * showHeight
     * at the FontUtils call site, FontUtils.java:11590-11592).
     */
    fun drawString(languageCode: String, text: String, width: Int, height: Int, textSize: Int, bold: Boolean): ByteArray
}

object GlyphRasterizers {
    @Volatile
    var active: GlyphRasterizer = UnavailableGlyphRasterizer
}

/**
 * JVM-safe default used until a real Android backend is registered (see
 * AndroidBleTransport.init). Throws a clear error rather than silently
 * producing wrong bytes - the runtime-drawn RTL/Hindi/Thai glyph path
 * genuinely cannot be reproduced off-device, so a plain JVM unit test must not
 * exercise it (a faked Canvas would give false confidence - see the task's
 * ground rules and docs/APK_REVERSE_ENGINEERING_NOTES.md).
 */
object UnavailableGlyphRasterizer : GlyphRasterizer {
    private const val MESSAGE =
        "ArabicCharDotMatrixGenerator runtime glyph rasterization requires a real " +
            "android.graphics Canvas/Typeface and is only available on-device (register " +
            "AndroidGlyphRasterizer). It cannot be produced or verified in a plain JVM unit test."

    override fun drawChar(languageCode: String, char: Char, size: Int, textSize: Int, bold: Boolean): ByteArray =
        error(MESSAGE)

    override fun drawString(languageCode: String, text: String, width: Int, height: Int, textSize: Int, bold: Boolean): ByteArray =
        error(MESSAGE)
}
