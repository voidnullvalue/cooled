package com.cooled.core.protocol

/**
 * Port of the script-detection helpers in
 * com.jtkj.led1248.light.utils.ArabicCharDotMatrixGenerator. These are pure
 * Unicode-codepoint-range checks (no Android/ICU dependency), used to decide
 * whether a character can be read from the plain font table
 * (`FontUtils.readFontData`, ported as `CoolleduxGlyphPipeline`) or needs the
 * runtime-drawn glyph path (`ArabicCharDotMatrixGenerator.readFontDataFromDraw`,
 * NOT yet ported - see docs/APK_REVERSE_ENGINEERING_NOTES.md for why that part
 * needs real on-device Android Canvas/Typeface rendering rather than a pure
 * Kotlin/JVM port).
 */
object ScriptDetection {
    /** Arabic script, including presentation forms. */
    fun isArabic(c: Char): Boolean {
        val code = c.code
        return code in 1536..1791 ||
            code in 1872..1919 ||
            code in 2208..2303 ||
            code in 64336..65023 ||
            code in 65136..65279 ||
            code in 60928..61183
    }

    /** Devanagari (Hindi). */
    fun isHindi(c: Char): Boolean = c.code in 2304..2431

    /** Hebrew. */
    fun isHebrew(c: Char): Boolean = c.code in 1424..1535

    /** Thai. */
    fun isThai(c: Char): Boolean = c.code in 3584..3711

    /** Vietnamese-specific Latin Extended characters (precomposed diacritics). */
    fun isVietnameseChar(codePoint: Int): Boolean =
        codePoint in 192..255 || codePoint in 258..432 || codePoint in 7840..7929

    fun isVietnamese(c: Char): Boolean = isVietnameseChar(c.code)

    fun isVietnameseLanguageCode(languageCode: String): Boolean = languageCode.equals("vi", ignoreCase = true)

    fun isChineseLanguageCode(languageCode: String): Boolean = languageCode.equals("zh-CN", ignoreCase = true)

    fun isChineseChar(codePoint: Int): Boolean =
        codePoint in 19968..40959 ||
            codePoint in 13312..19903 ||
            codePoint in 131072..173791 ||
            codePoint in 63744..64255 ||
            codePoint in 194560..195103

    /**
     * Port of ArabicCharDotMatrixGenerator.isLocalTypeSupported(String):
     * true for language codes the APK renders using a bundled system-font
     * draw path even though a font-table glyph might otherwise exist
     * (ar/iw/th/hi are never table-backed).
     */
    fun isLocalTypeSupported(languageCode: String): Boolean = when {
        languageCode.equals("ar", ignoreCase = true) -> false
        languageCode.equals("iw", ignoreCase = true) -> false
        languageCode.equals("th", ignoreCase = true) -> false
        languageCode.equals("hi", ignoreCase = true) -> false
        else -> true
    }

    /**
     * Port of ArabicCharDotMatrixGenerator.isLanguageSupported(char): true
     * means this character CAN be read from the plain font table; false
     * means it needs the runtime-drawn glyph path.
     */
    fun isLanguageSupported(c: Char): Boolean =
        !isArabic(c) && !isHebrew(c) && !isHindi(c) && !isThai(c) && !isVietnameseChar(c.code)

    /** Port of ArabicCharDotMatrixGenerator.getLanguageCodeByInput(char); null when no draw-path language applies. */
    fun getLanguageCodeByInput(c: Char): String? = when {
        isArabic(c) -> "ar"
        isHebrew(c) -> "iw"
        isHindi(c) -> "hi"
        isVietnamese(c) -> "vi"
        isThai(c) -> "th"
        else -> null
    }
}
