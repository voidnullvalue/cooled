package com.cooled.core.protocol

interface CoolleduxFontSource {
    fun readGlyph32(codePoint: Int, bold: Boolean): ByteArray?
    fun readGlyph8(codePoint: Int): ByteArray?
}

object CoolleduxFontSources {
    @Volatile
    var active: CoolleduxFontSource = BuiltinCoolleduxFontSource
}

object BuiltinCoolleduxFontSource : CoolleduxFontSource {
    override fun readGlyph32(codePoint: Int, bold: Boolean): ByteArray {
        val seed = codePoint and 0xFF
        return ByteArray(128) { i ->
            val col = i / 4
            val rowByte = i % 4
            val edge = col == 0 || col == 31 || rowByte == 0
            if (edge || ((col + seed) % 7 == 0)) 0x7F.toByte() else 0x00.toByte()
        }
    }

    override fun readGlyph8(codePoint: Int): ByteArray {
        val seed = codePoint and 0xFF
        return ByteArray(8) { i -> if ((i + seed) % 2 == 0) 0x7E.toByte() else 0x18.toByte() }
    }
}
