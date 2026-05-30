package com.cooled.core.protocol

import com.cooled.core.compression.LzssCodec
import com.cooled.core.model.DeviceFamily

/**
 * Auditable content packaging layer for program uploads.
 *
 * The CoolLEDUX text path mirrors the staged functions recovered from
 * `CoolledUXUtils` in `base_apk_protocol_sources.zip`:
 *
 * - `getDataWithTextContentProgramContent(...)`
 * - `getDataWithTextCombineProgram(...)` / `getDataForCombineProgram(...)`
 * - `getDataForProgram(...)`
 * - `getDataWithProgram(...)`
 * - `getDataResult(...)`
 *
 * Glyph bytes are still provided by a local deterministic fallback until the
 * APK's `FontUtils` implementation/assets are fully reconstructed.
 */
sealed class ProgramContent {
    data class Text(val text: String, val speed: Int, val effect: Int) : ProgramContent()
    data class CoolLedUxText(val spec: CoolLedUxTextContentProgramContent) : ProgramContent()
    data class Drawing(val width: Int, val height: Int, val rgbBytes: ByteArray) : ProgramContent()
    data class PresetMode(val mode: Int, val intensity: Int) : ProgramContent()
}

data class CoolLedUxTextContentProgramContent(
    val text: String,
    val layerType: Int = 0,
    val startColumn: Int = 0,
    val startRow: Int = 0,
    val showWidth: Int = 32,
    val showHeight: Int = 16,
    val mode: Int = 0,
    val speed: Int = 1,
    val stayTime: Int = 0,
    val moveSpace: Int = 0,
    val fontWidth: Int = 8,
    val fontHeight: Int = 16,
    val glyphBytes: ByteArray? = null
)

data class CoolLedUxProgram(
    val combinePrograms: List<CoolLedUxCombineProgram>,
    val showCount: Int = 1
) {
    fun getContentNumber(): Int = combinePrograms.size
}

sealed class CoolLedUxCombineProgram {
    abstract val type: Int

    data class TextCombine(
        val textContentProgramContent: CoolLedUxTextContentProgramContent
    ) : CoolLedUxCombineProgram() {
        override val type: Int = 3
    }
}

data class ProgramPackage(
    val metadata: ProgramMetadata,
    val compressed: ByteArray,
    val startHeaderFrame: ByteArray,
    val chunkFrames: List<ByteArray>
)

data class ProgramMetadata(
    val family: DeviceFamily,
    val messageType: Int,
    val chunkCount: Int,
    val uncompressedSize: Int,
    val compressedSize: Int,
    val usedCompression: Boolean
)

object ProgramComposer {
    fun compose(
        family: DeviceFamily,
        content: ProgramContent,
        index: Int,
        count: Int,
        showCount: Int,
        programType: Int? = null,
        extraTypeByte: Int? = null
    ): ProgramPackage {
        val body = encodeContent(family, content, showCount)
        val compressed = LzssCodec.compress(body)
        val start = CommandBuilders.buildProgramStartHeader(
            family = family,
            request = ProgramStartRequest(
                compressed = compressed,
                index = index,
                count = count,
                showCount = showCount,
                useAlternateOpcode = family == DeviceFamily.COOLLEDU,
                programType = programType,
                extraTypeByte = extraTypeByte
            )
        )
        val chunks = CommandBuilders.splitChunks(compressed).mapIndexed { i, c ->
            CommandBuilders.buildDataChunk(messageType = 0x03, totalCompressedLength = compressed.size, chunkIndex = i, chunk = c)
        }
        return ProgramPackage(
            metadata = ProgramMetadata(
                family = family,
                messageType = 0x03,
                chunkCount = chunks.size,
                uncompressedSize = body.size,
                compressedSize = compressed.size,
                usedCompression = true
            ),
            compressed = compressed,
            startHeaderFrame = start,
            chunkFrames = chunks
        )
    }

    fun encodeContentForTest(family: DeviceFamily, content: ProgramContent, showCount: Int = 1): ByteArray =
        encodeContent(family, content, showCount)

    private fun encodeContent(family: DeviceFamily, content: ProgramContent, showCount: Int): ByteArray = when (content) {
        is ProgramContent.Text -> if (family.usesCoolLedUxProgramLayout()) {
            val spec = CoolLedUxTextContentProgramContent(
                text = content.text,
                speed = content.speed,
                mode = content.effect,
                showWidth = 32,
                showHeight = 16
            )
            getDataWithProgram(CoolLedUxProgram(listOf(CoolLedUxCombineProgram.TextCombine(spec)), showCount))
        } else {
            val textBytes = content.text.encodeToByteArray()
            byteArrayOf(0x54, content.speed.toByte(), content.effect.toByte(), textBytes.size.toByte()) + textBytes
        }

        is ProgramContent.CoolLedUxText ->
            getDataWithProgram(CoolLedUxProgram(listOf(CoolLedUxCombineProgram.TextCombine(content.spec)), showCount))

        is ProgramContent.Drawing -> {
            require(content.rgbBytes.size == content.width * content.height * 3) {
                "Drawing rgbBytes must be width*height*3"
            }
            byteArrayOf(0x49, content.width.toByte(), content.height.toByte()) + content.rgbBytes
        }

        is ProgramContent.PresetMode -> byteArrayOf(0x4D, content.mode.toByte(), content.intensity.toByte())
    }

    private fun DeviceFamily.usesCoolLedUxProgramLayout(): Boolean =
        this == DeviceFamily.COOLLEDUX || this == DeviceFamily.COOLLEDX || this == DeviceFamily.COOLLEDS || this == DeviceFamily.ILEDCLOCK

    /** Port of CoolledUXUtils.getDataWithTextContentProgramContent(...). */
    fun getDataWithTextContentProgramContent(content: CoolLedUxTextContentProgramContent): ByteArray {
        val inner = mutableListOf<Byte>()
        inner += 0x01
        repeat(7) { inner += 0x00 }
        inner += one(content.layerType)
        inner += two(content.startColumn)
        inner += two(content.startRow)
        inner += two(content.showWidth)
        inner += two(content.showHeight)
        inner += one(content.mode)
        inner += one(content.speed)
        inner += one(content.stayTime)
        inner += two(content.moveSpace)
        inner += getFontByteDataCoolleduxForEmoji(content).toList()
        return (four(inner.size + 4) + inner).toByteArray()
    }

    /** Named shim matching the APK's text-combine branch inside getDataForCombineProgram(...). */
    fun getDataWithTextCombineProgram(program: CoolLedUxCombineProgram.TextCombine): ByteArray =
        getDataWithTextContentProgramContent(program.textContentProgramContent)

    /** Port of CoolledUXUtils.getDataForCombineProgram(...) for known LED-facing content types. */
    fun getDataForCombineProgram(program: CoolLedUxCombineProgram): ByteArray = when (program) {
        is CoolLedUxCombineProgram.TextCombine -> getDataWithTextCombineProgram(program)
    }

    /** Port of CoolledUXUtils.getDataForProgram(...). */
    fun getDataForProgram(program: CoolLedUxProgram): ByteArray =
        program.combinePrograms.flatMap { getDataForCombineProgram(it).asIterable() }.toByteArray()

    /** Port of CoolledUXUtils.getDataWithProgram(...). */
    fun getDataWithProgram(program: CoolLedUxProgram): ByteArray {
        val out = mutableListOf<Byte>()
        repeat(8) { out += 0x00 }
        out += one(program.getContentNumber())
        out += 0x00
        out += getDataForProgram(program).toList()
        return out.toByteArray()
    }

    /** Port-shaped helper for the APK's getDataResult(...) body/chunk result. */
    fun getDataResult(program: CoolLedUxProgram, packageSize: Int = 1024): CoolLedUxDataResult {
        val body = getDataWithProgram(program)
        val compressed = LzssCodec.compress(body)
        return CoolLedUxDataResult(body, compressed, CommandBuilders.splitChunks(compressed, packageSize))
    }

    /**
     * Partial port-shaped FontUtils.getFontByteDataCoolleduxForEmoji(...).
     *
     * If recovered APK font bytes are supplied in `glyphBytes`, they are emitted
     * verbatim. Otherwise this produces a deterministic monochrome column payload
     * with the same framing fields used by the APK text-content block: text count,
     * total width, per-glyph widths, and glyph bitmap bytes.
     */
    fun getFontByteDataCoolleduxForEmoji(content: CoolLedUxTextContentProgramContent): ByteArray {
        content.glyphBytes?.let { return it.copyOf() }
        val glyphCount = content.text.codePointCount(0, content.text.length).coerceAtLeast(0)
        val widthPerGlyph = content.fontWidth.coerceAtLeast(1)
        val height = content.fontHeight.coerceAtLeast(1)
        val bytesPerColumn = (height + 7) / 8
        val out = mutableListOf<Byte>()
        out += two(glyphCount)
        out += two(glyphCount * widthPerGlyph)
        repeat(glyphCount) { out += two(widthPerGlyph) }
        content.text.codePoints().forEach { cp ->
            repeat(widthPerGlyph) { x ->
                repeat(bytesPerColumn) { yByte ->
                    out += fallbackGlyphColumnByte(cp, x, yByte).toByte()
                }
            }
        }
        return out.toByteArray()
    }

    private fun fallbackGlyphColumnByte(codePoint: Int, x: Int, yByte: Int): Int {
        val mix = codePoint xor (x * 0x45) xor (yByte * 0x9D)
        return mix and 0xFF
    }

    private fun one(value: Int): Byte = (value and 0xFF).toByte()

    private fun two(value: Int): List<Byte> = listOf(
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    private fun four(value: Int): List<Byte> = listOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )
}

data class CoolLedUxDataResult(
    val uncompressedProgram: ByteArray,
    val compressedProgram: ByteArray,
    val chunks: List<ByteArray>
)
