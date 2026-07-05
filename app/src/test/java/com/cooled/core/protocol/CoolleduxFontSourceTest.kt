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
    fun textFontSizeOverrideActuallyChangesWhichFontTableIsRead() {
        // Regression coverage for the previously-missing font-size override:
        // auto-pick for displayRows=32 selects the 32px table (bytesPerColumn=4),
        // but an explicit override of 8 should route through the 8px table
        // (bytesPerColumn=1) instead - producing a meaningfully smaller
        // rendered-glyph payload for the same text, not just a cosmetic no-op.
        val previous = CoolleduxFontSources.active
        try {
            CoolleduxFontSources.active = FileCoolleduxFontSource(root)
            val auto = CoolleduxProgramBytecode.text("HELLO", speed = 9, effect = 2, displayColumns = 128, displayRows = 32)
            val overridden = CoolleduxProgramBytecode.text("HELLO", speed = 9, effect = 2, displayColumns = 128, displayRows = 32, fontSizeOverride = 8)
            assertTrue("8px override should produce a smaller payload than the auto-picked 32px table", overridden.size < auto.size)

            // An unsupported override value must not silently corrupt the
            // encode - it should fall back to the same auto-pick as if no
            // override were given at all.
            val invalidOverride = CoolleduxProgramBytecode.text("HELLO", speed = 9, effect = 2, displayColumns = 128, displayRows = 32, fontSizeOverride = 20)
            assertArrayEquals(auto, invalidOverride)
        } finally {
            CoolleduxFontSources.active = previous
        }
    }

    @Test
    fun coolleduxTextBodyFramesFiveHelloGlyphsThroughTheRealStreamModeEncoder() {
        // CoolleduxProgramBytecode.text(..., effect = 2) now routes through
        // CoolleduxStreamText (mode 2 is not a combine-canvas mode), which
        // trims and inter-glyph-spaces real font-table bytes instead of
        // embedding them verbatim - so this asserts the real
        // [tokenCount][runningTotal][per-glyph (colCount, type, bytes)]
        // framing round-trips cleanly against real bundled assets, rather
        // than asserting raw glyph bytes appear untouched (which was only
        // ever true of the old placeholder encoder).
        val previous = CoolleduxFontSources.active
        try {
            CoolleduxFontSources.active = FileCoolleduxFontSource(root)
            val body = CoolleduxProgramBytecode.text("HELLO", speed = 9, effect = 2, displayColumns = 128, displayRows = 32)

            // Text bytes start after getDataWithProgram's 10-byte outer
            // header plus getDataWithTextContentProgramContent's 26-byte
            // content-block header (4-byte length + 22 fixed fields) - see
            // coolLedUxTextProgram_matchesRecoveredApkBlockLayout in
            // ProtocolCoreTest.kt for the same 26-byte inner offset.
            val textStart = 10 + 26
            val tokenCount = readBe16(body, textStart)
            assertEquals(5, tokenCount)
            val runningTotal = readBe32(body, textStart + 2)

            var pos = textStart + 6
            var columnsSeen = 0
            var glyphsSeen = 0
            val bytesPerColumn = 4
            while (pos < body.size) {
                val colCount = body[pos].toInt() and 0xFF
                val type = body[pos + 1].toInt() and 0xFF
                assertEquals("HELLO has no emoji/image tokens", 0, type)
                pos += 2 + colCount * bytesPerColumn
                columnsSeen += colCount
                glyphsSeen++
            }
            assertEquals("chunk framing must consume the buffer exactly", body.size, pos)
            assertEquals(5, glyphsSeen)
            assertEquals(runningTotal, columnsSeen)
        } finally {
            CoolleduxFontSources.active = previous
        }
    }

    private fun readBe16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun readBe32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun findAssetRoot(): File {
        val candidates = listOf(
            File("app/src/main/assets/coolled-original"),
            File("src/main/assets/coolled-original"),
            File("../app/src/main/assets/coolled-original")
        )
        return candidates.firstOrNull { it.isDirectory } ?: candidates.first()
    }
}
