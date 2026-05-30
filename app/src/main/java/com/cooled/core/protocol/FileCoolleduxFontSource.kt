package com.cooled.core.protocol

import java.io.File

class FileCoolleduxFontSource(
    private val root: File
) : CoolleduxFontSource {
    override fun readGlyph32(codePoint: Int, bold: Boolean): ByteArray? {
        val file = if (bold) root.resolve("fonts/32_32_large") else root.resolve("fonts/32_32_small")
        return readRecord(file, codePoint, 128)
    }

    override fun readGlyph16(codePoint: Int, bold: Boolean): ByteArray? {
        val primary = if (bold) root.resolve("raw-assets/assets/UNICODE16_bold") else root.resolve("raw-assets/assets/UNICODE16")
        val flutterBold = root.resolve("raw-assets/assets/flutter_assets/assets/coolledux/font_library/unicode_16_bold")
        return readRecord(primary, codePoint, 32)
            ?: if (bold) readRecord(flutterBold, codePoint, 32) else null
    }

    override fun readGlyph14Bold(codePoint: Int): ByteArray? =
        readRecord(root.resolve("raw-assets/assets/flutter_assets/assets/coolledux/font_library/unicode_14_bold"), codePoint, 28)

    override fun readGlyph12(codePoint: Int, bold: Boolean): ByteArray? {
        val file = if (bold) root.resolve("raw-assets/assets/UNICODE12_BOLD") else root.resolve("raw-assets/assets/UNICODE12")
        return readRecord(file, codePoint, 24)
    }

    override fun readGlyph8(codePoint: Int): ByteArray? = readRecord(root.resolve("fonts/8_small"), codePoint, 8)

    private fun readRecord(file: File, index: Int, size: Int): ByteArray? {
        if (index < 0 || !file.isFile) return null
        val offset = index.toLong() * size.toLong()
        if (offset + size > file.length()) return null
        return file.inputStream().use { stream ->
            var remaining = offset
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
            var readTotal = 0
            while (readTotal < size) {
                val read = stream.read(out, readTotal, size - readTotal)
                if (read <= 0) return null
                readTotal += read
            }
            out
        }
    }
}
