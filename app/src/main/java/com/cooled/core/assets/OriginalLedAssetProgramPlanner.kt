package com.cooled.core.assets

import com.cooled.core.protocol.ProgramContent

data class OriginalLedAssetProgramSelection(
    val asset: OriginalLedAsset,
    val displayColumns: Int?,
    val displayRows: Int?,
    val speed: Int,
    val effect: Int,
    val programType: Int?,
    val extraTypeByte: Int?,
    val uploadCheck: OriginalLedAssetUploadCheck = OriginalLedAssetUploadRules.check(asset)
) {
    fun toProgramContent(): ProgramContent.OriginalAsset = ProgramContent.OriginalAsset(
        assetPath = asset.path,
        kind = asset.kind,
        speed = speed.coerceIn(0, 255),
        effect = effect.coerceIn(0, 255),
        displayColumns = displayColumns,
        displayRows = displayRows
    )

    fun debugSummary(): String = "asset='${asset.path}' kind=${asset.kind} matrix=${displayColumns ?: "?"}x${displayRows ?: "?"} speed=${speed.coerceIn(0, 255)} effect=${effect.coerceIn(0, 255)} programType=${programType ?: "none"} extra=${extraTypeByte ?: "none"} uploadable=${uploadCheck.uploadable} reason=${uploadCheck.reason}"
}

object OriginalLedAssetProgramPlanner {
    fun availableKinds(catalog: OriginalLedAssetCatalog = OriginalLedAssetCatalogs.active): List<String> = catalog.uploadableKinds()

    fun selectPreferred(
        kind: String,
        displayColumns: Int?,
        displayRows: Int?,
        speed: Int = 255,
        effect: Int = 2,
        programType: Int? = null,
        extraTypeByte: Int? = null,
        catalog: OriginalLedAssetCatalog = OriginalLedAssetCatalogs.active
    ): OriginalLedAssetProgramSelection? {
        val cleanKind = kind.ifBlank { OriginalLedAssetKinds.PAYLOAD_ASSET }
        val asset = catalog.firstPreferred(cleanKind) ?: return null
        return selectionForAsset(asset, displayColumns, displayRows, speed, effect, programType, extraTypeByte)
    }

    fun selectByPath(
        assetPath: String,
        kind: String,
        displayColumns: Int?,
        displayRows: Int?,
        speed: Int = 255,
        effect: Int = 2,
        programType: Int? = null,
        extraTypeByte: Int? = null,
        catalog: OriginalLedAssetCatalog = OriginalLedAssetCatalogs.active
    ): OriginalLedAssetProgramSelection? {
        val cleanPath = assetPath.trim().trimStart('/').take(512)
        if (cleanPath.isBlank()) return null
        val cleanKind = kind.ifBlank { OriginalLedAssetKinds.PAYLOAD_ASSET }.take(32)
        val asset = catalog.listAssets().firstOrNull { it.path == cleanPath }
            ?: OriginalLedAsset(kind = cleanKind, path = cleanPath, sizeBytes = 0L, sha256 = "")
        return selectionForAsset(asset.copy(kind = cleanKind), displayColumns, displayRows, speed, effect, programType, extraTypeByte)
    }

    fun selectionsForKind(
        kind: String,
        displayColumns: Int?,
        displayRows: Int?,
        limit: Int = 12,
        speed: Int = 255,
        effect: Int = 2,
        programType: Int? = null,
        extraTypeByte: Int? = null,
        catalog: OriginalLedAssetCatalog = OriginalLedAssetCatalogs.active
    ): List<OriginalLedAssetProgramSelection> {
        val cleanKind = kind.ifBlank { OriginalLedAssetKinds.PAYLOAD_ASSET }
        return catalog.preferredByKind(cleanKind, limit).map {
            selectionForAsset(it, displayColumns, displayRows, speed, effect, programType, extraTypeByte)
        }
    }

    private fun selectionForAsset(
        asset: OriginalLedAsset,
        displayColumns: Int?,
        displayRows: Int?,
        speed: Int,
        effect: Int,
        programType: Int?,
        extraTypeByte: Int?
    ): OriginalLedAssetProgramSelection = OriginalLedAssetProgramSelection(
        asset = asset,
        displayColumns = displayColumns,
        displayRows = displayRows,
        speed = speed.coerceIn(0, 255),
        effect = effect.coerceIn(0, 255),
        programType = programType,
        extraTypeByte = extraTypeByte,
        uploadCheck = OriginalLedAssetUploadRules.check(asset)
    )
}
