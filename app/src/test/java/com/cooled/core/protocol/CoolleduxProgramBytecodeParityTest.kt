package com.cooled.core.protocol

import com.cooled.core.assets.OriginalLedAssetByteSources
import com.cooled.core.assets.OriginalLedAssetBytes
import com.cooled.core.assets.OriginalLedAssetKinds
import com.cooled.core.model.DeviceFamily
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CoolleduxProgramBytecodeParityTest {
    @Test
    fun textProgramUsesRecoveredApkContentBlockWithoutExtraCombineLength() {
        val glyphBytes = byteArrayOf(0x00, 0x01, 0x00, 0x02, 0x00, 0x02, 0x55, 0x66)
        val body = ProgramComposer.getDataWithProgram(
            ProgramComposer.getDataForProgram(
                CoolLedUxTextContentProgramContent(
                    text = "A",
                    showWidth = 32,
                    showHeight = 16,
                    mode = 2,
                    speed = 9,
                    glyphBytes = glyphBytes
                )
            )
        )

        assertEquals(0, body[0].toInt())
        assertEquals(1, body[8].toInt())
        assertEquals(0, body[9].toInt())
        assertEquals(0, body[10].toInt())
        assertEquals(0, body[11].toInt())
        assertEquals(0, body[12].toInt())
        assertEquals(34, body[13].toInt() and 0xFF)
        assertEquals(0x01, body[14].toInt() and 0xFF)
        assertArrayEquals(glyphBytes, body.copyOfRange(body.size - glyphBytes.size, body.size))
    }

    @Test
    fun fontUtilsKeepsVerbatimGlyphBytesForGoldenVectors() {
        val supplied = byteArrayOf(1, 2, 3, 4, 5)
        val encoded = ProgramComposer.getFontByteDataCoolleduxForEmoji(
            CoolLedUxTextContentProgramContent(text = "HELLO", glyphBytes = supplied)
        )

        assertArrayEquals(supplied, encoded)
    }

    @Test
    fun fontUtilsBuildsStructuredAssetBackedGlyphPayloadForDigitsPunctuationAndSymbol() {
        val previous = CoolleduxFontSources.active
        try {
            CoolleduxFontSources.active = object : CoolleduxFontSource {
                override fun readGlyph32(codePoint: Int, bold: Boolean): ByteArray? = ByteArray(128) { (codePoint + it).toByte() }
                override fun readGlyph8(codePoint: Int): ByteArray? = ByteArray(8) { (codePoint xor it).toByte() }
            }
            val encoded = ProgramComposer.getFontByteDataCoolleduxForEmoji(
                CoolLedUxTextContentProgramContent(text = "H7!?♥", fontWidth = 32, fontHeight = 32)
            )

            assertEquals(5, readU16(encoded, 0))
            assertEquals(160, readU16(encoded, 2))
            repeat(5) { assertEquals(32, readU16(encoded, 4 + it * 2)) }
            assertEquals(4 + 5 * 2 + 5 * 128, encoded.size)
        } finally {
            CoolleduxFontSources.active = previous
        }
    }


    @Test
    fun fontUtilsReturnsZeroFilledGlyphForMissingFontsLikeTheApkDoesButStillRejectsEmoji() {
        // FontUtils.readFontData(...) in the APK returns a zero-filled glyph
        // (not an error) whenever its font table can't produce one - see
        // docs/APK_REVERSE_ENGINEERING_NOTES.md, "readFontData ... blank-glyph
        // fallback". Non-BMP/emoji text is a genuinely unimplemented feature
        // (the APK's own emoji subsystem draws bitmap images, not font
        // glyphs), not a missing-data case, so that still throws.
        val previous = CoolleduxFontSources.active
        try {
            CoolleduxFontSources.active = MissingCoolleduxFontSource

            val encoded = ProgramComposer.getFontByteDataCoolleduxForEmoji(
                CoolLedUxTextContentProgramContent(text = "A", fontWidth = 32, fontHeight = 32)
            )
            assertEquals(4 + 2 + 128, encoded.size)
            assertArrayEquals(ByteArray(128), encoded.copyOfRange(encoded.size - 128, encoded.size))

            assertThrows(IllegalArgumentException::class.java) {
                ProgramComposer.getFontByteDataCoolleduxForEmoji(
                    CoolLedUxTextContentProgramContent(text = "😀", fontWidth = 32, fontHeight = 32)
                )
            }
        } finally {
            CoolleduxFontSources.active = previous
        }
    }

    @Test
    fun rawGifAnimationAssetsUseApkRawGifContentBlock() {
        val previous = OriginalLedAssetByteSources.active
        try {
            val gif = "GIF89a-demo".encodeToByteArray()
            OriginalLedAssetByteSources.active = object : OriginalLedAssetBytes {
                override fun read(path: String): ByteArray? = gif
            }

            val body = ProgramComposer.encodeContentForTest(
                DeviceFamily.COOLLEDUX,
                ProgramContent.OriginalAsset("demo.gif", OriginalLedAssetKinds.ANIMATION, displayColumns = 64, displayRows = 32)
            )

            assertEquals(1, body[8].toInt() and 0xFF)
            assertEquals(0x0c, body[14].toInt() and 0xFF)
            assertEquals(3, body[22].toInt() and 0xFF)
            assertEquals(64, readU16(body, 28))
            assertEquals(32, readU16(body, 30))
            assertEquals(gif.size, readU32(body, 32))
            assertArrayEquals(gif, body.copyOfRange(body.size - gif.size, body.size))
        } finally {
            OriginalLedAssetByteSources.active = previous
        }
    }

    @Test
    fun iconAssetsUseApkGraffitiContentBlockWithPayloadLength() {
        val previous = OriginalLedAssetByteSources.active
        try {
            val payload = byteArrayOf(0x11, 0x20, 0x33, 0x40)
            OriginalLedAssetByteSources.active = object : OriginalLedAssetBytes {
                override fun read(path: String): ByteArray? = payload
            }

            val body = ProgramComposer.encodeContentForTest(
                DeviceFamily.COOLLEDUX,
                ProgramContent.OriginalAsset("icon-payload.jt", OriginalLedAssetKinds.ICON, speed = 9, effect = 2, displayColumns = 2, displayRows = 1)
            )

            assertEquals(0x02, body[14].toInt() and 0xFF)
            assertEquals(5, body[22].toInt() and 0xFF)
            assertEquals(8, readU16(body, 27))
            assertEquals(8, readU16(body, 29))
            assertEquals(2, body[31].toInt() and 0xFF)
            assertEquals(9, body[32].toInt() and 0xFF)
            assertEquals(3, body[33].toInt() and 0xFF)
            assertEquals(payload.size, readU32(body, 34))
            assertArrayEquals(payload, body.copyOfRange(body.size - payload.size, body.size))
        } finally {
            OriginalLedAssetByteSources.active = previous
        }
    }

    @Test
    fun rgb444TransferUsesApkNibblePackingAndThresholds() {
        assertArrayEquals(byteArrayOf(0x00, 0x00), OriginalLedAssetPayloadEncoder.rgb444TransferColorBytes(0xFF000000.toInt()))
        assertArrayEquals(byteArrayOf(0x0F, 0x00), OriginalLedAssetPayloadEncoder.rgb444TransferColorBytes(0xFFFF0000.toInt()))
        assertArrayEquals(byteArrayOf(0x00, 0xF0.toByte()), OriginalLedAssetPayloadEncoder.rgb444TransferColorBytes(0xFF00FF00.toInt()))
        assertArrayEquals(byteArrayOf(0x00, 0x0F), OriginalLedAssetPayloadEncoder.rgb444TransferColorBytes(0xFF0000FF.toInt()))
        assertArrayEquals(byteArrayOf(0x0F, 0xFF.toByte()), OriginalLedAssetPayloadEncoder.rgb444TransferColorBytes(0xFFFFFFFF.toInt()))
        assertArrayEquals(byteArrayOf(0x06, 0x66), OriginalLedAssetPayloadEncoder.rgb444TransferColorBytes(0xFF808080.toInt()))
        assertArrayEquals(byteArrayOf(0x00, 0x00), OriginalLedAssetPayloadEncoder.rgb444TransferColorBytes(0x00000000, alpha = 0))
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    private fun readU32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
}
