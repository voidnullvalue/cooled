package com.cooled.core.assets

import java.io.File

class FileOriginalLedAssetBytes(
    private val root: File
) : OriginalLedAssetBytes {
    override fun read(path: String): ByteArray? {
        val normalized = path.trim().trimStart('/')
        if (normalized.isBlank()) return null
        val candidates = listOf(
            root.resolve(normalized),
            root.resolve("raw-assets").resolve(normalized)
        ).distinctBy { it.path }
        return candidates.firstOrNull { it.isFile }?.readBytes()
    }
}
