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
    override fun readGlyph32(codePoint: Int, bold: Boolean): ByteArray = ByteArray(128)
    override fun readGlyph8(codePoint: Int): ByteArray = ByteArray(8)
}
