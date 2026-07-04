package com.cooled.core.protocol

import com.ibm.icu.text.ArabicShaping
import com.ibm.icu.text.Bidi
import java.text.Normalizer

/**
 * Port of ArabicCharDotMatrixGenerator.getVisualText(...)/reverseIfArabic(...).
 * Uses the same ICU4J library the APK bundles (icudt72b -> icu4j 72.1, wired
 * in via app/build.gradle.kts) so Arabic shaping/BiDi reordering is
 * byte-identical to the original app instead of a hand-rolled
 * re-implementation of the Unicode BiDi algorithm - see
 * docs/APK_REVERSE_ENGINEERING_NOTES.md.
 */
object ScriptVisualText {
    /**
     * Reorders/normalizes [text] into "visual order" for the given
     * [languageCode], matching the APK's per-language dispatch exactly:
     *  - ar: Arabic letter shaping (ArabicShaping.LETTERS_SHAPE) then a
     *    right-to-left BiDi reorder with mirroring.
     *  - iw: right-to-left BiDi reorder with mirroring (no shaping).
     *  - vi/th/hi: Unicode NFC normalization only.
     *  - anything else, or blank/empty input: returned unchanged (blank
     *    input specifically becomes "").
     */
    fun getVisualText(languageCode: String, text: String?): String {
        if (text == null) return ""
        if (text.isBlank()) return ""
        return when (languageCode) {
            "ar" -> Bidi(ArabicShaping(ArabicShaping.LETTERS_SHAPE).shape(text), Bidi.DIRECTION_RIGHT_TO_LEFT.toInt()).writeReordered(Bidi.DO_MIRRORING.toInt())
            "iw" -> Bidi(text, Bidi.DIRECTION_RIGHT_TO_LEFT.toInt()).writeReordered(Bidi.DO_MIRRORING.toInt())
            "vi", "th", "hi" -> Normalizer.normalize(text, Normalizer.Form.NFC)
            else -> text
        }
    }

    /** Port of ArabicCharDotMatrixGenerator.reverseIfArabic(String): unconditionally reverses the string (a real APK naming quirk - it reverses any input, not just Arabic). */
    fun reverseIfArabic(text: String): String = text.reversed()
}
