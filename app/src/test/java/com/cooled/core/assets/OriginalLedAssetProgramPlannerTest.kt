package com.cooled.core.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OriginalLedAssetProgramPlannerTest {
    @Test
    fun selectsPreferredAssetByKindAndClampsProgramValues() {
        val selection = OriginalLedAssetProgramPlanner.selectPreferred(
            kind = OriginalLedAssetKinds.ICON,
            displayColumns = 128,
            displayRows = 32,
            speed = 999,
            effect = -4,
            programType = 14,
            extraTypeByte = 1,
            catalog = fakeCatalog()
        )

        assertNotNull(selection)
        val content = selection!!.toProgramContent()
        assertEquals("raw-assets/assets/icon/demo_icon.png", content.assetPath)
        assertEquals(OriginalLedAssetKinds.ICON, content.kind)
        assertEquals(255, content.speed)
        assertEquals(0, content.effect)
        assertEquals(128, content.displayColumns)
        assertEquals(32, content.displayRows)
    }

    @Test
    fun selectByPathAllowsManualPathNotInCatalog() {
        val selection = OriginalLedAssetProgramPlanner.selectByPath(
            assetPath = "/manual/missing.png",
            kind = OriginalLedAssetKinds.IMAGE,
            displayColumns = 64,
            displayRows = 16,
            catalog = fakeCatalog()
        )

        assertNotNull(selection)
        val content = selection!!.toProgramContent()
        assertEquals("manual/missing.png", content.assetPath)
        assertEquals(OriginalLedAssetKinds.IMAGE, content.kind)
        assertEquals(64, content.displayColumns)
        assertEquals(16, content.displayRows)
    }

    @Test
    fun blankManualPathReturnsNull() {
        assertNull(
            OriginalLedAssetProgramPlanner.selectByPath(
                assetPath = "   ",
                kind = OriginalLedAssetKinds.IMAGE,
                displayColumns = null,
                displayRows = null,
                catalog = fakeCatalog()
            )
        )
    }

    @Test
    fun availableKindsFollowUploadPriority() {
        assertEquals(
            listOf(OriginalLedAssetKinds.ANIMATION, OriginalLedAssetKinds.ICON, OriginalLedAssetKinds.IMAGE),
            OriginalLedAssetProgramPlanner.availableKinds(fakeCatalog())
        )
    }

    private fun fakeCatalog(): OriginalLedAssetCatalog = object : OriginalLedAssetCatalog {
        override fun listAssets(): List<OriginalLedAsset> = listOf(
            OriginalLedAsset(OriginalLedAssetKinds.IMAGE, "raw-assets/assets/image/default.png", 300, "sha-image"),
            OriginalLedAsset(OriginalLedAssetKinds.ICON, "raw-assets/assets/icon/demo_icon.png", 200, "sha-icon-demo"),
            OriginalLedAsset(OriginalLedAssetKinds.ICON, "raw-assets/assets/icon/large_icon.png", 500, "sha-icon-large"),
            OriginalLedAsset(OriginalLedAssetKinds.ANIMATION, "raw-assets/assets/gif/sample.gif", 900, "sha-gif"),
            OriginalLedAsset(OriginalLedAssetKinds.FONT, "fonts/32_32_large", 8388608, "sha-font")
        )

        override fun summary(): OriginalLedAssetSummary = OriginalLedAssetSummary(
            total = listAssets().size,
            byKind = listAssets().groupingBy { it.kind }.eachCount(),
            examples = listAssets().take(2)
        )
    }
}
