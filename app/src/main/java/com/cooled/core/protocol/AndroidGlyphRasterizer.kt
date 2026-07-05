package com.cooled.core.protocol

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import java.util.Locale

/**
 * On-device port of com.jtkj.led1248.light.utils.ArabicCharDotMatrixGenerator's
 * runtime glyph-drawing path (the piece earlier passes correctly flagged as
 * "not JVM-portable": it rasterizes a character with a real android.graphics
 * Canvas/Paint/Typeface using a bundled .ttf, then reads the pixels back into a
 * dot-matrix). This is the concrete [GlyphRasterizer] registered on-device; a
 * plain JVM unit test cannot exercise it (no Canvas), so it is written as a
 * clean, direct, line-cited transcription and the ONLY genuinely testable part
 * - the pixel readback - is factored into the pure [ArabicDotMatrix] (which has
 * a JVM golden vector). See docs/APK_REVERSE_ENGINEERING_NOTES.md.
 *
 * Fonts: the APK loads its .ttf assets from the APK asset root ("Arabic_Bold.ttf",
 * "hi-bold.ttf", ...). This repo's extraction places the identical files under
 * [FONT_ASSET_DIR]; getThFont uses the platform "sans-serif-thai" family (no
 * asset), exactly as the APK does.
 *
 * SCOPE: this covers the ar/iw/hi/th (and, incidentally, vi) draw path used by
 * FontUtils.getFontByteDataCoolleduxForEmoji. The 7-arg drawSingleChar overload
 * (ArabicCharDotMatrixGenerator.java:276-523) is the zh-CN localType path with
 * ~250 lines of Chinese-specific point-size tuning; Chinese is rendered via the
 * font-table path in this port (isLanguageSupported(zh-CN char) == true), so
 * that overload is a deliberate, documented gap rather than a transcription of
 * dead-for-our-scope code (see class doc / task report).
 */
class AndroidGlyphRasterizer(private val assets: AssetManager) : GlyphRasterizer {

    // ---- Typeface selection (ArabicCharDotMatrixGenerator.java:208-225, 918-955) ----

    /** Port of getFontByInput(char, boolean) (lines 208-225): pick a bundled typeface by the character's script. */
    private fun getFontByInput(char: Char, bold: Boolean): Typeface? = when {
        ScriptDetection.isArabic(char) -> getArFont(bold)
        ScriptDetection.isHebrew(char) -> getIwFont(bold)
        ScriptDetection.isHindi(char) -> getHiFont(bold)
        ScriptDetection.isVietnamese(char) -> getViFont(bold)
        ScriptDetection.isThai(char) -> getThFont(bold)
        else -> null
    }

    /** getArFont (lines 918-925): both bold and thin map to Arabic_Bold.ttf in the APK. */
    private fun getArFont(bold: Boolean): Typeface = fromAsset("Arabic_Bold.ttf")

    /** getIwFont (lines 950-955). */
    private fun getIwFont(bold: Boolean): Typeface = fromAsset(if (bold) "iw-bold.ttf" else "iw-thin.ttf")

    /** getHiFont (lines 943-948). */
    private fun getHiFont(bold: Boolean): Typeface = fromAsset(if (bold) "hi-bold.ttf" else "hi-thin.ttf")

    /** getViFont (lines 936-941): note the APK loads the "1"-suffixed variants. */
    private fun getViFont(bold: Boolean): Typeface = fromAsset(if (bold) "vi-bold1.ttf" else "vi-thin1.ttf")

    /** getThFont (lines 927-934): platform Thai family, not a bundled asset. */
    private fun getThFont(bold: Boolean): Typeface =
        Typeface.create("sans-serif-thai", if (bold) Typeface.BOLD else Typeface.NORMAL)

    private val typefaceCache = HashMap<String, Typeface>()
    private fun fromAsset(name: String): Typeface =
        typefaceCache.getOrPut(name) { Typeface.createFromAsset(assets, "$FONT_ASSET_DIR/$name") }

    // ---- Single-character draw (drawSingleChar 6-arg, lines 525-620) ----

    override fun drawChar(languageCode: String, char: Char, size: Int, textSize: Int, bold: Boolean): ByteArray {
        val font = getFontByInput(char, bold)
        val bitmap = drawSingleChar(languageCode, char, size, font, bold, textSize)
        // generateDotMatrix (lines 261-270): isVi(lang) picks the Vi sampling rule.
        val vietnamese = ScriptDetection.isVietnameseLanguageCode(languageCode)
        return ArabicDotMatrix.rasterizePixels(toPixelGrid(bitmap), vietnamese, threshold = 128)
    }

    /**
     * Port of drawSingleChar(String lang, char, int size, Typeface, boolean bold, int textSize)
     * (ArabicCharDotMatrixGenerator.java:525-620). Baseline-Y adjustments in the
     * Vietnamese branch were reconstructed from
     * reverse/apktool/.../ArabicCharDotMatrixGenerator.smali (method at smali
     * line 607): the jadx pseudocode rendered them as a *cumulative* -0.5 then
     * -1.0 fall-through (L37->L39), but the smali (`:cond_8` -> -0.5 -> `:goto_3`
     * vs. `:goto_2` -> -1.0 -> `:goto_3`) shows they are *mutually exclusive* -
     * another instance of jadx control-flow rendering being unreliable here, per
     * the reverse-engineering notes.
     */
    private fun drawSingleChar(lang: String, char: Char, size: Int, font: Typeface?, bold: Boolean, textSize: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        if (!ScriptDetection.isVietnameseLanguageCode(lang)) {
            // Non-Vietnamese: black text on white (lines 528-548 / smali :cond_c).
            val canvas = Canvas(bitmap)
            canvas.drawColor(WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = BLACK
            if (ScriptDetection.isArabic(char)) {
                paint.textSize = if (bold) textSize.toFloat() else textSize * 0.9f
            } else {
                paint.textSize = textSize.toFloat()
            }
            paint.typeface = font
            paint.textAlign = Paint.Align.CENTER
            paint.textLocale = Locale(ScriptDetection.getLanguageCodeByInput(char) ?: lang)
            val fm = paint.fontMetrics
            val w = size.toFloat()
            canvas.drawText(char.toString(), w / 2.0f, (w - (fm.ascent + fm.descent)) / 2.0f, paint)
            return bitmap
        }

        // Vietnamese: white text on black, with tnum/point-size tweaks for ASCII alnum (lines 549-619).
        val special = char in '0'..'9' || char in 'a'..'z' || char in 'A'..'Z'
        val canvas = Canvas(bitmap)
        canvas.drawColor(BLACK)
        val paint = Paint()
        paint.color = WHITE
        val ts = textSize.toFloat()
        paint.textSize = ts
        // if size == textSize && textSize == 12 -> not special (smali 700-704).
        var isSpecial = special
        if (size == textSize && textSize == 12) isSpecial = false
        paint.textAlign = Paint.Align.CENTER
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = false
        paint.typeface = font
        paint.textLocale = Locale(lang)
        if (isSpecial) {
            paint.textSize = ts + 1.0f
            paint.fontFeatureSettings = "tnum"
            if (bold) {
                paint.typeface = Typeface.create(font, Typeface.BOLD)
                paint.strokeWidth = 1.2f
                paint.strokeJoin = Paint.Join.ROUND
            } else {
                paint.typeface = Typeface.create(font, Typeface.NORMAL)
                paint.strokeWidth = 0.0f
            }
        } else if (size == textSize && textSize == 12) {
            paint.textSize = ts + 1.0f
            paint.fontFeatureSettings = "tnum"
            paint.typeface = Typeface.create(font, Typeface.NORMAL)
            paint.style = Paint.Style.FILL
            paint.strokeWidth = 0.0f
        }
        val fm = paint.fontMetrics
        val x = size / 2.0f
        var y = (size - (fm.ascent + fm.descent)) / 2.0f
        if (size == textSize) {
            // Mutually-exclusive baseline shift (smali 836-862), NOT cumulative.
            y -= if (isSpecial) {
                if (bold) 1.0f else 0.5f
            } else {
                when {
                    textSize == 12 -> 1.0f
                    !bold -> 0.5f
                    else -> 1.0f
                }
            }
        }
        canvas.drawText(char.toString(), x, y, paint)
        return bitmap
    }

    // ---- Multi-character (whole-segment) draw (drawArabicContent 7-arg, lines 622-652) ----

    override fun drawString(languageCode: String, text: String, width: Int, height: Int, textSize: Int, bold: Boolean): ByteArray {
        val font = getFontByInput(text[0], bold)
        val bitmap = drawArabicContent(languageCode, text, width, height, font, bold, textSize)
        // generateDotMatrix(String,String,...) (lines 272-274) always uses the
        // non-Vi sampling rule.
        return ArabicDotMatrix.rasterizePixels(toPixelGrid(bitmap), vietnamese = false, threshold = 128)
    }

    /**
     * Port of drawArabicContent(String lang, String s, int width, int height,
     * Typeface, boolean bold, int textSize) (ArabicCharDotMatrixGenerator.java:622-652):
     * right-aligned draw at `x = width - 5`, reversing the string when it starts
     * with an Arabic OR Hebrew character (reverseIfArabic - which, per the port
     * of that method, reverses unconditionally when called).
     */
    private fun drawArabicContent(lang: String, s: String, width: Int, height: Int, font: Typeface?, bold: Boolean, textSize: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = BLACK
        if (ScriptDetection.isArabic(s[0])) {
            paint.textSize = if (bold) textSize.toFloat() else textSize * 0.9f
        } else {
            paint.textSize = textSize.toFloat()
        }
        paint.typeface = font
        paint.textAlign = Paint.Align.RIGHT
        paint.textLocale = Locale(ScriptDetection.getLanguageCodeByInput(s[0]) ?: lang)
        val fm = paint.fontMetrics
        val x = (width - 5).toFloat()
        val y = (height - (fm.ascent + fm.descent)) / 2.0f
        val drawn = if (ScriptDetection.isArabic(s[0]) || ScriptDetection.isHebrew(s[0])) {
            ScriptVisualText.reverseIfArabic(s)
        } else {
            s
        }
        canvas.drawText(drawn, x, y, paint)
        return bitmap
    }

    private fun toPixelGrid(bitmap: Bitmap): PixelGrid {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val width = bitmap.width
        if (!bitmap.isRecycled) bitmap.recycle()
        return PixelGrid(width, pixels.size / width) { x, y -> pixels[y * width + x] }
    }

    private companion object {
        // ArabicCharDotMatrixGenerator draws with these two literal colors
        // (Canvas.drawColor(-1) = opaque white; ViewCompat.MEASURED_STATE_MASK /
        // 0xFF000000 = opaque black).
        const val WHITE = -0x1 // 0xFFFFFFFF
        const val BLACK = -0x1000000 // 0xFF000000
        const val FONT_ASSET_DIR = "coolled-original/raw-assets/assets"
    }
}
