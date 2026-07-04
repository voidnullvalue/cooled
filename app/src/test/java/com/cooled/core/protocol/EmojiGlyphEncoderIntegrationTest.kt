package com.cooled.core.protocol

import com.cooled.core.assets.FileOriginalLedAssetBytes
import com.cooled.core.assets.OriginalLedAssetByteSources
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies EmojiAssetPaths against real bundled emoji assets (extracted by
 * tools/apk-re/extract-coolled-apk-assets.sh - see
 * docs/APK_REVERSE_ENGINEERING_NOTES.md), at the byte level.
 *
 * This container's JVM unit test compile classpath does not resolve
 * javax.imageio (java.desktop isn't visible to this Kotlin compilation, even
 * though the module exists in the installed JDK - `java --list-modules`
 * shows it, but AGP's unit-test Kotlin compile task doesn't expose it here),
 * so real *pixel decoding* is not JVM-testable in this environment either.
 * EmojiGlyphEncoderTest already covers the actual pixel-processing algorithm
 * exhaustively against synthetic PixelGrids; this test only proves the real
 * files exist, are well-formed at the container-format level (correct GIF
 * magic bytes), and that EmojiAssetPaths resolves them correctly - pixel
 * decoding itself needs an Android instrumentation test or on-device run.
 */
class EmojiGlyphEncoderIntegrationTest {
    private val root = findAssetRoot()

    @Test
    fun realGifEmojiAssetExistsAndHasValidGifMagicBytes() {
        val file = root.resolve("raw-assets/res/drawable-xxhdpi-v4/emoji_fc_16x16_60.gif")
        assertTrue("expected extracted asset at $file", file.isFile)
        val bytes = file.readBytes()
        assertTrue("expected non-empty file", bytes.isNotEmpty())
        assertArrayEquals("GIF".toByteArray() + byteArrayOf('8'.code.toByte()), bytes.copyOfRange(0, 4))
    }

    @Test
    fun realPngEmojiAssetExistsAndHasValidPngMagicBytes() {
        val file = root.resolve("raw-assets/res/drawable-xxhdpi-v4/emoji_fc_32x32_1_23.png")
        assertTrue("expected extracted asset at $file", file.isFile)
        val bytes = file.readBytes()
        val pngMagic = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        assertArrayEquals(pngMagic, bytes.copyOfRange(0, 4))
    }

    @Test
    fun emojiAssetPathsResolvesGifBeforePngAndReturnsExactBytes() {
        val previous = OriginalLedAssetByteSources.active
        try {
            val fileSource = FileOriginalLedAssetBytes(root)
            OriginalLedAssetByteSources.active = fileSource

            val gifBytes = root.resolve("raw-assets/res/drawable-xxhdpi-v4/emoji_fc_16x16_60.gif").readBytes()
            val resolved = EmojiAssetPaths.readImageBytes("emoji_fc_16x16_60")
            assertArrayEquals(gifBytes, resolved)

            val pngBytes = root.resolve("raw-assets/res/drawable-xxhdpi-v4/emoji_fc_32x32_1_23.png").readBytes()
            val resolvedPng = EmojiAssetPaths.readImageBytes("emoji_fc_32x32_1_23")
            assertArrayEquals(pngBytes, resolvedPng)
        } finally {
            OriginalLedAssetByteSources.active = previous
        }
    }

    @Test
    fun emojiAssetPathsReturnsNullForAnUnknownName() {
        val previous = OriginalLedAssetByteSources.active
        try {
            OriginalLedAssetByteSources.active = FileOriginalLedAssetBytes(root)
            assertNull(EmojiAssetPaths.readImageBytes("emoji_fc_does_not_exist_999"))
        } finally {
            OriginalLedAssetByteSources.active = previous
        }
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
