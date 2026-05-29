package com.cooled.core.assets

import android.content.res.AssetManager

data class OriginalLedAsset(
    val kind: String,
    val path: String,
    val sizeBytes: Long,
    val sha256: String
)

data class OriginalLedAssetSummary(
    val total: Int = 0,
    val byKind: Map<String, Int> = emptyMap(),
    val examples: List<OriginalLedAsset> = emptyList()
) {
    fun asSingleLine(): String {
        if (total == 0) return "No original APK LED asset catalog loaded"
        val counts = byKind.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value}" }
        val sample = examples.take(4).joinToString { "${it.kind}:${it.path.substringAfterLast('/')}" }
        return "Original APK LED assets total=$total [$counts] examples=[$sample]"
    }
}

interface OriginalLedAssetCatalog {
    fun listAssets(): List<OriginalLedAsset>
    fun summary(): OriginalLedAssetSummary
    fun byKind(kind: String): List<OriginalLedAsset> = listAssets().filter { it.kind == kind }
}

object OriginalLedAssetCatalogs {
    @Volatile
    var active: OriginalLedAssetCatalog = EmptyOriginalLedAssetCatalog
}

object EmptyOriginalLedAssetCatalog : OriginalLedAssetCatalog {
    override fun listAssets(): List<OriginalLedAsset> = emptyList()
    override fun summary(): OriginalLedAssetSummary = OriginalLedAssetSummary()
}

interface OriginalLedAssetBytes {
    fun read(path: String): ByteArray?
}

object OriginalLedAssetByteSources {
    @Volatile
    var active: OriginalLedAssetBytes = EmptyOriginalLedAssetBytes
}

object EmptyOriginalLedAssetBytes : OriginalLedAssetBytes {
    override fun read(path: String): ByteArray? = null
}

class AssetOriginalLedAssetBytes(
    private val assets: AssetManager,
    private val root: String = "coolled-original"
) : OriginalLedAssetBytes {
    override fun read(path: String): ByteArray? {
        val normalized = path.trim().trimStart('/')
        val candidates = listOf(
            "$root/$normalized",
            "$root/raw-assets/$normalized",
            normalized
        ).distinct()
        for (candidate in candidates) {
            try {
                return assets.open(candidate).use { it.readBytes() }
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }
}

class AssetManifestOriginalLedAssetCatalog(
    private val assets: AssetManager,
    private val manifestPath: String = "coolled-original/LED_ASSET_MANIFEST.tsv"
) : OriginalLedAssetCatalog {
    @Volatile
    private var cached: List<OriginalLedAsset>? = null

    override fun listAssets(): List<OriginalLedAsset> {
        cached?.let { return it }
        val loaded = loadManifest()
        cached = loaded
        return loaded
    }

    override fun summary(): OriginalLedAssetSummary {
        val assets = listAssets()
        return OriginalLedAssetSummary(
            total = assets.size,
            byKind = assets.groupingBy { it.kind }.eachCount(),
            examples = assets.sortedWith(compareBy<OriginalLedAsset> { it.kind }.thenBy { it.path }).take(12)
        )
    }

    private fun loadManifest(): List<OriginalLedAsset> {
        return try {
            assets.open(manifestPath).bufferedReader().useLines { lines ->
                lines.drop(1).mapNotNull { line ->
                    val parts = line.split('\t')
                    if (parts.size < 4) return@mapNotNull null
                    OriginalLedAsset(
                        kind = parts[0],
                        path = parts[1],
                        sizeBytes = parts[2].toLongOrNull() ?: 0L,
                        sha256 = parts[3]
                    )
                }.toList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
