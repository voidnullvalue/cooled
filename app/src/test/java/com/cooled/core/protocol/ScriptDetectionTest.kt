package com.cooled.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptDetectionTest {
    private fun assertRange(name: String, range: IntRange, check: (Char) -> Boolean) {
        assertTrue("$name should include lower bound ${range.first}", check(range.first.toChar()))
        assertTrue("$name should include upper bound ${range.last}", check(range.last.toChar()))
        if (range.first > 0) assertFalse("$name should exclude ${range.first - 1}", check((range.first - 1).toChar()))
        assertFalse("$name should exclude ${range.last + 1}", check((range.last + 1).toChar()))
    }

    @Test
    fun arabicRangesMatchApkBoundariesExactly() {
        assertRange("Arabic block", 1536..1791, ScriptDetection::isArabic)
        assertRange("Arabic Extended-A", 1872..1919, ScriptDetection::isArabic)
        assertRange("Arabic Supplement", 2208..2303, ScriptDetection::isArabic)
        assertRange("Arabic Presentation Forms-A", 64336..65023, ScriptDetection::isArabic)
        assertRange("Arabic Presentation Forms-B", 65136..65279, ScriptDetection::isArabic)
        assertRange("Arabic Presentation Forms-B (low)", 60928..61183, ScriptDetection::isArabic)
        assertFalse(ScriptDetection.isArabic('A'))
    }

    @Test
    fun hebrewHindiThaiRangesMatchApkBoundariesExactly() {
        assertRange("Hebrew", 1424..1535, ScriptDetection::isHebrew)
        assertRange("Devanagari", 2304..2431, ScriptDetection::isHindi)
        assertRange("Thai", 3584..3711, ScriptDetection::isThai)
    }

    @Test
    fun vietnameseCodepointRangesMatchApkBoundariesExactly() {
        assertTrue(ScriptDetection.isVietnameseChar(192))
        assertTrue(ScriptDetection.isVietnameseChar(255))
        assertFalse(ScriptDetection.isVietnameseChar(191))
        assertFalse(ScriptDetection.isVietnameseChar(256))
        assertTrue(ScriptDetection.isVietnameseChar(258))
        assertTrue(ScriptDetection.isVietnameseChar(432))
        assertTrue(ScriptDetection.isVietnameseChar(7840))
        assertTrue(ScriptDetection.isVietnameseChar(7929))
        assertFalse(ScriptDetection.isVietnameseChar(7930))
    }

    @Test
    fun chineseCodepointRangesMatchApkBoundariesExactly() {
        assertTrue(ScriptDetection.isChineseChar(19968))
        assertTrue(ScriptDetection.isChineseChar(40959))
        assertTrue(ScriptDetection.isChineseChar(13312))
        assertTrue(ScriptDetection.isChineseChar(19903))
        assertTrue(ScriptDetection.isChineseChar(131072))
        assertTrue(ScriptDetection.isChineseChar(173791))
        assertTrue(ScriptDetection.isChineseChar(63744))
        assertTrue(ScriptDetection.isChineseChar(64255))
        assertTrue(ScriptDetection.isChineseChar(194560))
        assertTrue(ScriptDetection.isChineseChar(195103))
        assertFalse(ScriptDetection.isChineseChar('A'.code))
    }

    @Test
    fun isLanguageSupportedIsFalseForEveryDrawPathScript() {
        assertFalse(ScriptDetection.isLanguageSupported('ء')) // Arabic letter
        assertFalse(ScriptDetection.isLanguageSupported('א')) // Hebrew alef
        assertFalse(ScriptDetection.isLanguageSupported('क')) // Devanagari ka
        assertFalse(ScriptDetection.isLanguageSupported('ก')) // Thai ko kai
        assertFalse(ScriptDetection.isLanguageSupported('À')) // Vietnamese A-grave
        assertTrue(ScriptDetection.isLanguageSupported('A'))
        assertTrue(ScriptDetection.isLanguageSupported('7'))
    }

    @Test
    fun getLanguageCodeByInputMapsEachScriptCorrectly() {
        assertEquals("ar", ScriptDetection.getLanguageCodeByInput('ء'))
        assertEquals("iw", ScriptDetection.getLanguageCodeByInput('א'))
        assertEquals("hi", ScriptDetection.getLanguageCodeByInput('क'))
        assertEquals("th", ScriptDetection.getLanguageCodeByInput('ก'))
        assertEquals("vi", ScriptDetection.getLanguageCodeByInput('À'))
        assertNull(ScriptDetection.getLanguageCodeByInput('A'))
    }

    @Test
    fun isLocalTypeSupportedIsFalseOnlyForDrawOnlyLanguageCodes() {
        assertFalse(ScriptDetection.isLocalTypeSupported("ar"))
        assertFalse(ScriptDetection.isLocalTypeSupported("AR"))
        assertFalse(ScriptDetection.isLocalTypeSupported("iw"))
        assertFalse(ScriptDetection.isLocalTypeSupported("th"))
        assertFalse(ScriptDetection.isLocalTypeSupported("hi"))
        assertTrue(ScriptDetection.isLocalTypeSupported("en"))
        assertTrue(ScriptDetection.isLocalTypeSupported("vi")) // Vietnamese is normalized via NFC, not the ar/iw/th/hi draw gate
        assertTrue(ScriptDetection.isLocalTypeSupported(""))
    }

    @Test
    fun languageCodeHelpersAreCaseInsensitive() {
        assertTrue(ScriptDetection.isVietnameseLanguageCode("VI"))
        assertTrue(ScriptDetection.isChineseLanguageCode("zh-cn"))
        assertFalse(ScriptDetection.isVietnameseLanguageCode("en"))
    }
}
