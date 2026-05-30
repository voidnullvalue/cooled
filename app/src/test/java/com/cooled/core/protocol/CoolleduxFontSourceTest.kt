package com.cooled.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CoolleduxFontSourceTest {
    private val root = findAssetRoot()

    @Test
    fun fileFontSourceReadsExactRecordsForHelloDigitsPunctuationAndSymbol() {
        val source = FileCoolleduxFontSource(root)
        val large = root.resolve("fonts/32_32_large").readBytes()
        val small = root.resolve("fonts/32_32_small").readBytes()
        val tiny = root.resolve("fonts/8_small").readBytes()

        listOf('H', 'E', 'L', 'O', '0', '7', '!', '?').forEach { ch ->
            val cp = ch.code
            assertArrayEquals(large.copyOfRange(cp * 128, cp * 128 + 128), source.readGlyph32(cp, bold = true))
            assertArrayEquals(small.copyOfRange(cp * 128, cp * 128 + 128), source.readGlyph32(cp, bold = false))
            assertArrayEquals(tiny.copyOfRange(cp * 8, cp * 8 + 8), source.readGlyph8(cp))
        }

        val heart = 0x2665
        val heartGlyph = source.readGlyph32(heart, bold = true)
        assertNotNull(heartGlyph)
        assertArrayEquals(large.copyOfRange(heart * 128, heart * 128 + 128), heartGlyph)
        assertTrue("symbol glyph should not be all zero", heartGlyph!!.any { it.toInt() != 0 })
    }

    @Test
    fun fileFontSourceMapsUnicodeAndFlutterFontLibraryTables() {
        val source = FileCoolleduxFontSource(root)
        val cp = 'A'.code
        assertEquals(32, source.readGlyph16(cp, bold = false)?.size)
        assertEquals(32, source.readGlyph16(cp, bold = true)?.size)
        assertEquals(28, source.readGlyph14Bold(cp)?.size)
        assertEquals(24, source.readGlyph12(cp, bold = false)?.size)
        assertEquals(24, source.readGlyph12(cp, bold = true)?.size)
    }

    @Test
    fun coolleduxTextBodyEmbedsAssetBackedHelloGlyphsAndSpacing() {
        val previous = CoolleduxFontSources.active
        try {
            val source = FileCoolleduxFontSource(root)
            CoolleduxFontSources.active = source
            val body = CoolleduxProgramBytecode.text("HELLO", speed = 9, effect = 2, displayColumns = 128, displayRows = 32)
            val h = source.readGlyph32('H'.code, bold = true)!!
            val e = source.readGlyph32('E'.code, bold = true)!!
            val hOffset = body.indexOfSubArray(h)
            val eOffset = body.indexOfSubArray(e)

            assertTrue("HELLO body should include the exact H glyph record", hOffset >= 0)
            assertEquals("default text spacing inserts one empty 32px column", hOffset + 128 + 4, eOffset)
        } finally {
            CoolleduxFontSources.active = previous
        }
    }

    private fun ByteArray.indexOfSubArray(needle: ByteArray): Int {
        outer@ for (i in 0..(size - needle.size)) {
            for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    private fun findAssetRoot(): File {
        val candidates = listOf(
            File("app/src/main/assets/coolled-original"),
            File("src/main/assets/coolled-original"),
            File("../app/src/main/assets/coolled-original")
        )
        return candidates.firstOrNull { it.isDirectory } ?: candidates.first()
    }
}
