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

    private fun findAssetRoot(): File {
        val candidates = listOf(
            File("app/src/main/assets/coolled-original"),
            File("src/main/assets/coolled-original"),
            File("../app/src/main/assets/coolled-original")
        )
        return candidates.firstOrNull { it.isDirectory } ?: candidates.first()
    }
}
