package com.cooled.core.protocol

/**
 * Port of CoolledUUtils.getDataWithTextContentProgramContent - the CoolLEDU
 * (plain "U") family's text-program content framing, wrapping whichever of
 * CoolleduStreamText/CoolleduCombineText the content's mode selects.
 *
 * Field layout (all integer fields big-endian), confirmed by direct read of
 * CoolledUUtils.java:1031-1057 - much flatter than CoolLEDUX's equivalent,
 * with no per-glyph framing at all:
 *   [4-byte total length = inner size + 4]
 *   ["01" - 1 byte, always this literal value]
 *   [7 reserved zero bytes]
 *   [layerType - 1 byte]
 *   [startColumn - 2 bytes] [startRow - 2 bytes]
 *   [showWidth - 2 bytes] [showHeight - 2 bytes]
 *   [mode - 1 byte] [speed - 1 byte] [stayTime - 1 byte]
 *   [4-byte length of the rendered text bytes]
 *   [rendered text bytes - CoolleduStreamText or CoolleduCombineText's output]
 */
object CoolleduProgramBytecode {
    /** FontUtils.getFontByteDataCoolleduForEmoji's combine-vs-stream mode split - the same set CoolLEDUX uses. */
    private val combineCanvasModes = setOf(1, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)

    /**
     * Convenience wrapper matching CoolleduxProgramBytecode.text's signature
     * for uniform dispatch from ProgramContent.encodeContent. showHeight is
     * forced to 16 - the only height CoolleduGlyphPipeline supports so far
     * (see its class doc) - regardless of displayRows, since there's no
     * rescale path yet to honor a different real display height faithfully.
     */
    fun text(text: String, speed: Int, effect: Int, displayColumns: Int?): ByteArray = text(
        CoolleduTextContentProgramContent(
            text = text.ifBlank { "HELLO" },
            showWidth = displayColumns?.coerceIn(8, 512) ?: 32,
            showHeight = 16,
            mode = effect.coerceIn(0, 255),
            speed = speed.coerceIn(0, 255),
            textSize = 16
        )
    )

    /** True if [text]/[effect] would hit CoolleduGlyphPipeline/CoolleduTextTokenizer's supported scope (no rescale, no emoji tokens, no mirror) rather than throwing. */
    fun supports(text: String): Boolean = runCatching { CoolleduTextTokenizer.tokenize(text) }.isSuccess

    fun text(content: CoolleduTextContentProgramContent): ByteArray {
        val rendered = if (content.mode in combineCanvasModes) {
            CoolleduCombineText.encode(content)
        } else {
            CoolleduStreamText.encode(content)
        }

        val inner = mutableListOf<Byte>()
        inner += 0x01.toByte()
        repeat(7) { inner += 0x00.toByte() }
        inner += content.layerType.toByte()
        inner += twoByteBE(content.startColumn)
        inner += twoByteBE(content.startRow)
        inner += twoByteBE(content.showWidth)
        inner += twoByteBE(content.showHeight)
        inner += content.mode.toByte()
        inner += content.speed.toByte()
        inner += content.stayTime.toByte()
        inner += fourByteBE(rendered.size)
        inner += rendered.toList()

        val out = mutableListOf<Byte>()
        out += fourByteBE(inner.size + 4)
        out += inner
        return out.toByteArray()
    }

    private fun twoByteBE(value: Int): List<Byte> = listOf(((value ushr 8) and 0xFF).toByte(), (value and 0xFF).toByte())
    private fun fourByteBE(value: Int): List<Byte> = listOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )
}
