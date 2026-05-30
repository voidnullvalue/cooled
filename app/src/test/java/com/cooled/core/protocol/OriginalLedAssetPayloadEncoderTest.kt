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
    fun missingGifAnimationAssetIsDocumentedAsApproximateUntilApkMappingExists() {
        val gifCount = root.walkTopDown().count { it.isFile && it.extension.equals("gif", ignoreCase = true) }
        assertEquals("extracted LED asset tree currently contains no GIF files to build a golden vector from", 0, gifCount)
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
