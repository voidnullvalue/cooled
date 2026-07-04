package com.cooled.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptVisualTextTest {
    @Test
    fun hebrewIsReversedByBidiReorderingForAPureRtlRun() {
        // Codepoints captured from an actual icu4j 72.1 run (the exact
        // version the APK bundles, icudt72b) rather than hand-predicted -
        // see docs/APK_REVERSE_ENGINEERING_NOTES.md. Built from explicit
        // char codes rather than literal Unicode source characters to avoid
        // any risk of source-encoding mangling.
        val alef = 0x5D0.toChar()
        val bet = 0x5D1.toChar()
        val gimel = 0x5D2.toChar()
        val hebrew = "" + alef + bet + gimel
        val result = ScriptVisualText.getVisualText("iw", hebrew)
        assertEquals("" + gimel + bet + alef, result) // a pure RTL run is a straight reversal
    }

    @Test
    fun arabicIsShapedIntoPresentationFormsThenReversed() {
        val beh = 0x628.toChar()
        val teh = 0x62A.toChar()
        val arabic = "" + beh + teh
        val result = ScriptVisualText.getVisualText("ar", arabic)
        // Shaped presentation forms (0xFE96/0xFE91), reversed by the RTL BiDi reorder.
        assertEquals("" + 0xFE96.toChar() + 0xFE91.toChar(), result)
    }

    @Test
    fun vietnameseThaiHindiAreNfcNormalizedOnly() {
        val decomposedE = "e" + 0x0301.toChar() // "e" + combining acute accent
        val precomposedE = 0xE9.toChar().toString()
        assertEquals(precomposedE, ScriptVisualText.getVisualText("vi", decomposedE))
        assertEquals(precomposedE, ScriptVisualText.getVisualText("th", decomposedE))
        assertEquals(precomposedE, ScriptVisualText.getVisualText("hi", decomposedE))
    }

    @Test
    fun unrecognizedLanguageCodeReturnsTextUnchanged() {
        assertEquals("HELLO", ScriptVisualText.getVisualText("en", "HELLO"))
        assertEquals("HELLO", ScriptVisualText.getVisualText("", "HELLO"))
    }

    @Test
    fun blankOrNullInputBecomesEmptyString() {
        assertEquals("", ScriptVisualText.getVisualText("ar", null))
        assertEquals("", ScriptVisualText.getVisualText("ar", ""))
        assertEquals("", ScriptVisualText.getVisualText("ar", "   "))
    }

    @Test
    fun reverseIfArabicReversesAnyStringRegardlessOfScript() {
        assertEquals("CBA", ScriptVisualText.reverseIfArabic("ABC"))
        assertEquals("", ScriptVisualText.reverseIfArabic(""))
    }
}
