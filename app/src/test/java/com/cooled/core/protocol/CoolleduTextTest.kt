package com.cooled.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CoolleduTextTest {
    private fun withFakeFontSource(glyphs: Map<Char, ByteArray>, block: () -> Unit) {
        val previous = CoolleduxFontSources.active
        try {
            CoolleduxFontSources.active = object : CoolleduxFontSource {
                override fun readGlyph32(codePoint: Int, bold: Boolean): ByteArray? = null
                override fun readGlyph16(codePoint: Int, bold: Boolean): ByteArray? = glyphs[codePoint.toChar()]
                override fun readGlyph8(codePoint: Int): ByteArray? = null
            }
            block()
        } finally {
            CoolleduxFontSources.active = previous
        }
    }

    @Test
    fun tokenizerSplitsPlainTextOneCharacterPerToken() {
        assertEquals(listOf("H", "E", "L", "L", "O"), CoolleduTextTokenizer.tokenize("HELLO"))
    }

    @Test
    fun tokenizerThrowsOnAnEmojiPatternMatchInsteadOfMishandlingIt() {
        assertThrows(IllegalStateException::class.java) { CoolleduTextTokenizer.tokenize("hi i012 there") }
    }

    @Test
    fun streamTextGoldenVectorNoRotationWithSpacing() {
        withFakeFontSource(mapOf('A' to byteArrayOf(0x11, 0x00, 0x22, 0x00), 'B' to byteArrayOf(0x33, 0x00))) {
            val content = CoolleduTextContentProgramContent(
                text = "AB", showWidth = 32, showHeight = 16, mode = 2, textSize = 16, textSpacing = 1, textRotate = 0
            )
            val encoded = CoolleduStreamText.encode(content)
            // A (2 cols) + 1 spacing column + B (1 col, no trailing spacing - last token)
            assertArrayEquals(byteArrayOf(0x11, 0x00, 0x22, 0x00, 0x00, 0x00, 0x33, 0x00), encoded)
        }
    }

    @Test
    fun combineTextGoldenVectorFillingExactlyOneRow() {
        withFakeFontSource(mapOf('A' to byteArrayOf(0x11, 0x00, 0x22, 0x00), 'B' to byteArrayOf(0x33, 0x00))) {
            val content = CoolleduTextContentProgramContent(
                text = "AB", showWidth = 3, showHeight = 16, mode = 1, textSize = 16, textSpacing = 0, textRotate = 0
            )
            val encoded = CoolleduCombineText.encode(content)
            // A (2 cols) + B (1 col) = 3 columns = showWidth exactly, no centering padding needed.
            assertArrayEquals(byteArrayOf(0x11, 0x00, 0x22, 0x00, 0x33, 0x00), encoded)
        }
    }

    @Test
    fun combineCanvasModesMatchCoolLedux() {
        // Confirmed by direct read of FontUtils.getFontByteDataCoolleduForEmoji's
        // mode dispatch (L10-L13): modes 1 and 4-13 all set the combine flag,
        // exactly the same set CoolLEDUX uses.
        withFakeFontSource(mapOf('A' to byteArrayOf(0x11, 0x00))) {
            for (mode in listOf(1, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)) {
                // showWidth=1 so combine mode's row-centering is a no-op (the
                // single glyph already exactly fills the row) and both paths
                // are directly comparable byte-for-byte.
                val combine = CoolleduCombineText.encode(CoolleduTextContentProgramContent(text = "A", mode = mode, textSize = 16, showHeight = 16, showWidth = 1))
                val stream = CoolleduStreamText.encode(CoolleduTextContentProgramContent(text = "A", mode = mode, textSize = 16, showHeight = 16, showWidth = 1))
                assertArrayEquals(combine, stream)
            }
        }
    }

    @Test
    fun programBytecodeFramingMatchesCoolledUUtilsGetDataWithTextContentProgramContent() {
        withFakeFontSource(mapOf('A' to byteArrayOf(0x11, 0x00, 0x22, 0x00), 'B' to byteArrayOf(0x33, 0x00))) {
            val content = CoolleduTextContentProgramContent(
                text = "AB", layerType = 0, startColumn = 0, startRow = 0,
                showWidth = 32, showHeight = 16, mode = 2, speed = 1, stayTime = 0,
                textSize = 16, textSpacing = 1, textRotate = 0
            )
            val encoded = CoolleduProgramBytecode.text(content)
            val rendered = byteArrayOf(0x11, 0x00, 0x22, 0x00, 0x00, 0x00, 0x33, 0x00) // same as the stream golden vector above
            val inner = byteArrayOf(0x01) + ByteArray(7) +
                byteArrayOf(0x00) + // layerType
                byteArrayOf(0x00, 0x00) + // startColumn
                byteArrayOf(0x00, 0x00) + // startRow
                byteArrayOf(0x00, 0x20) + // showWidth = 32
                byteArrayOf(0x00, 0x10) + // showHeight = 16
                byteArrayOf(0x02) + // mode
                byteArrayOf(0x01) + // speed
                byteArrayOf(0x00) + // stayTime
                byteArrayOf(0x00, 0x00, 0x00, 0x08) + // rendered.size = 8
                rendered
            val expected = byteArrayOf(0x00, 0x00, 0x00, (inner.size + 4).toByte()) + inner
            assertArrayEquals(expected, encoded)
        }
    }

    @Test
    fun rejectsRescaleMirrorAndEmojiTokensUntilThoseAreActuallyPorted() {
        withFakeFontSource(mapOf('A' to byteArrayOf(0x11, 0x00))) {
            assertThrows(IllegalArgumentException::class.java) {
                CoolleduGlyphPipeline.readAndShapeGlyph('A'.code, CoolleduTextContentProgramContent(text = "A", textSize = 12, showHeight = 16))
            }
            assertThrows(IllegalArgumentException::class.java) {
                CoolleduStreamText.encode(CoolleduTextContentProgramContent(text = "A", textSize = 16, showHeight = 16, isMirror = true))
            }
            assertThrows(IllegalStateException::class.java) {
                CoolleduStreamText.encode(CoolleduTextContentProgramContent(text = "hi i012", textSize = 16, showHeight = 16))
            }
        }
    }

    @Test
    fun programComposerRoutesCoolleduTextThroughTheRealEncoderWithinScope() {
        withFakeFontSource(mapOf('A' to byteArrayOf(0x11, 0x00))) {
            val body = ProgramComposer.encodeContentForTest(
                com.cooled.core.model.DeviceFamily.COOLLEDU,
                ProgramContent.Text(text = "A", speed = 1, effect = 2)
            )
            // Real encoding starts with the "01" marker byte at offset 4
            // (after the 4-byte outer length prefix) - the unverified
            // placeholder starts with the unrelated literal 0x54 at offset 0.
            assertEquals(0x01, body[4].toInt() and 0xFF)
        }
    }

    @Test
    fun programComposerFallsBackToPlaceholderForCoolleduTextOutsideScope() {
        // "i012" matches the emoji pattern CoolleduTextTokenizer doesn't
        // support yet, so dispatch must fall back to the documented
        // unverified placeholder rather than throwing all the way up
        // through ProgramComposer.
        val body = ProgramComposer.encodeContentForTest(
            com.cooled.core.model.DeviceFamily.COOLLEDU,
            ProgramContent.Text(text = "hi i012", speed = 1, effect = 2)
        )
        assertEquals(0x54, body[0].toInt() and 0xFF)
    }

    @Test
    fun programComposerRoutesCoolledmTextThroughTheSameRealEncoderAsColledu() {
        // CoolledMUtils.getDataWithTextContentProgramContent/FontUtils.
        // getFontByteDataCoolledmForEmoji are confirmed structurally
        // identical to CoolLEDU's own versions - see CoolleduProgramBytecode's
        // class doc - so CoolLEDM routes through the exact same pipeline.
        withFakeFontSource(mapOf('A' to byteArrayOf(0x11, 0x00))) {
            val coolledm = ProgramComposer.encodeContentForTest(
                com.cooled.core.model.DeviceFamily.COOLLEDM,
                ProgramContent.Text(text = "A", speed = 1, effect = 2)
            )
            val colledu = ProgramComposer.encodeContentForTest(
                com.cooled.core.model.DeviceFamily.COOLLEDU,
                ProgramContent.Text(text = "A", speed = 1, effect = 2)
            )
            assertArrayEquals(colledu, coolledm)
            assertEquals(0x01, coolledm[4].toInt() and 0xFF)
        }
    }

    @Test
    fun programComposerRoutesColleduudTextThroughTheSameRealEncoderAsColledu() {
        // CoolledUDUtils (iLedBike) calls DeviceManager.CoolleduTextContentProgramContent
        // and FontUtils.getFontByteDataCoolleduForEmoji directly - the exact
        // same types/functions CoolLEDU itself uses, confirmed by direct read.
        withFakeFontSource(mapOf('A' to byteArrayOf(0x11, 0x00))) {
            val coolleduud = ProgramComposer.encodeContentForTest(
                com.cooled.core.model.DeviceFamily.COOLLEDUD,
                ProgramContent.Text(text = "A", speed = 1, effect = 2)
            )
            val colledu = ProgramComposer.encodeContentForTest(
                com.cooled.core.model.DeviceFamily.COOLLEDU,
                ProgramContent.Text(text = "A", speed = 1, effect = 2)
            )
            assertArrayEquals(colledu, coolleduud)
        }
    }
}
