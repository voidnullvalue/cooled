package com.cooled.core.protocol

import com.cooled.core.assets.FileOriginalLedAssetBytes
import com.cooled.core.assets.OriginalLedAssetByteSources
import com.cooled.core.assets.OriginalLedAssetKinds
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class OriginalLedAssetPayloadEncoderTest {
    private val root = findAssetRoot()

    @Test
    fun jtTemplateAssetsRemainRawPayloadInputsForTemplateWiring() {
        val previous = OriginalLedAssetByteSources.active
        try {
            OriginalLedAssetByteSources.active = FileOriginalLedAssetBytes(root)
            val path = "raw-assets/assets/flutter_assets/assets/coolledux/business_hours_style/3264/1_business_hours_standard_style/1_business/1_7x14_time_digit.jt"
            val raw = root.resolve(path).readBytes()
            val encoded = OriginalLedAssetPayloadEncoder.encode(path, OriginalLedAssetKinds.CLOCK_TEMPLATE, 64, 32)

            assertEquals("raw", encoded.format)
            assertEquals(null, encoded.width)
            assertEquals(null, encoded.height)
            assertArrayEquals(raw, encoded.bytes)
        } finally {
            OriginalLedAssetByteSources.active = previous
        }
    }

    @Test
    fun realGifAssetGoldenVectorForRawAnimationWrapping() {
        // tools/apk-re/extract-coolled-apk-assets.sh now pulls in
        // res/drawable-xxhdpi-v4 (see docs/APK_REVERSE_ENGINEERING_NOTES.md),
        // which includes ~3,770 real .gif emoji/icon assets - this used to
        // be blocked ("extracted LED asset tree currently contains no GIF
        // files"), now fixed with a real golden vector instead of a
        // placeholder assertion.
        val previous = OriginalLedAssetByteSources.active
        try {
            OriginalLedAssetByteSources.active = FileOriginalLedAssetBytes(root)
            val path = "raw-assets/res/drawable-xxhdpi-v4/emoji_fc_16x16_60.gif"
            val raw = root.resolve(path).readBytes()
            assertArrayEquals("GIF8".toByteArray(), raw.copyOfRange(0, 4))

            val encoded = OriginalLedAssetPayloadEncoder.encode(path, OriginalLedAssetKinds.ANIMATION, 64, 32)

            assertEquals("raw-gif", encoded.format)
            assertEquals(null, encoded.width)
            assertEquals(null, encoded.height)
            assertArrayEquals(raw, encoded.bytes)
        } finally {
            OriginalLedAssetByteSources.active = previous
        }
    }

    // --- Part 2: cross-check the original-asset RGB444 path against the newer,
    // confirmed-exact EmojiGlyphEncoder primitives. OriginalLedAssetPayloadEncoder
    // .bitmapToRgb444TransferColumnMajor and EmojiGlyphEncoder.toRgb444Columns both
    // walk pixels column-major (x outer, y inner) and encode each via the shared
    // OriginalLedAssetPayloadEncoder.rgb444TransferColorBytes, so their algorithms
    // are byte-identical. These tests pin the primitive and the ordering. ---

    @Test
    fun rgb444TransferColorBytesMatchesTheApkThresholdConstantsAndPacking() {
        // TextEmojiManagerCoolLEDUX.rgb444Transfer: >=238 -> 15, <=47 -> 0,
        // else (channel-47)/14 + 1. Packed as [0|redNibble][green<<4|blue].
        fun transfer(ch: Int): Int = when {
            ch >= 238 -> 15
            ch <= 47 -> 0
            else -> ((ch - 47) / 14) + 1
        }
        for (r in intArrayOf(0, 47, 48, 61, 62, 100, 237, 238, 255)) {
            for (g in intArrayOf(0, 47, 48, 238, 255)) {
                for (b in intArrayOf(0, 47, 48, 238, 255)) {
                    val argb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    val enc = OriginalLedAssetPayloadEncoder.rgb444TransferColorBytes(argb)
                    val rn = transfer(r)
                    val gn = transfer(g)
                    val bn = transfer(b)
                    assertArrayEquals(
                        "argb=${argb.toUInt().toString(16)}",
                        byteArrayOf(rn.toByte(), ((gn shl 4) or bn).toByte()),
                        enc
                    )
                }
            }
        }
    }

    @Test
    fun emojiRgb444ColumnsMatchAManualColumnMajorEncodeSharingTheSamePrimitive() {
        // Same column-major (x outer, y inner) loop + same rgb444TransferColorBytes
        // that OriginalLedAssetPayloadEncoder.bitmapToRgb444TransferColumnMajor uses.
        val w = 3
        val h = 2
        val pixels = intArrayOf(
            // (x=0,y=0) (x=1,y=0) (x=2,y=0)
            0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(),
            // (x=0,y=1) (x=1,y=1) (x=2,y=1)
            0xFF646464.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt()
        )
        val grid = PixelGrid(w, h) { x, y -> pixels[y * w + x] }

        val expected = ArrayList<Byte>()
        for (x in 0 until w) {
            for (y in 0 until h) {
                val argb = pixels[y * w + x]
                expected += OriginalLedAssetPayloadEncoder.rgb444TransferColorBytes(argb, (argb ushr 24) and 0xFF).toList()
            }
        }
        assertArrayEquals(expected.toByteArray(), EmojiGlyphEncoder.toRgb444Columns(grid))
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
