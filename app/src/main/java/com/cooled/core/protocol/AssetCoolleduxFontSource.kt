package com.cooled.core.protocol

import android.content.res.AssetManager

class AssetCoolleduxFontSource(
    private val assets: AssetManager,
    private val root: String = "coolled-original/fonts"
) : CoolleduxFontSource {
    override fun readGlyph32(codePoint: Int, bold: Boolean): ByteArray? {
        val file = if (bold) "32_32_large" else "32_32_small"
        return readRecord("$root/$file", codePoint, 128)
    }

    override fun readGlyph8(codePoint: Int): ByteArray? = readRecord("$root/8_small", codePoint, 8)

    private fun readRecord(path: String, index: Int, size: Int): ByteArray? {
        if (index < 0) return null
        return try {
            assets.open(path).use { stream ->
                var remaining = index.toLong() * size.toLong()
                while (remaining > 0) {
                    val skipped = stream.skip(remaining)
                    if (skipped <= 0) {
                        if (stream.read() < 0) return null
                        remaining--
                    } else {
                        remaining -= skipped
                    }
                }
                val out = ByteArray(size)
                var offset = 0
                while (offset < size) {
                    val read = stream.read(out, offset, size - offset)
                    if (read <= 0) return null
                    offset += read
                }
                out
            }
        } catch (_: Exception) {
            null
        }
    }
}
