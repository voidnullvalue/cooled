package com.cooled.core.protocol

import com.cooled.core.assets.OriginalLedAssetByteSources
import com.cooled.core.compression.LzssCodec
import com.cooled.core.model.DeviceFamily

sealed class ProgramContent {
    data class Text(
        val text: String,
        val speed: Int,
        val effect: Int,
        val displayColumns: Int? = null,
        val displayRows: Int? = null
    ) : ProgramContent()

    data class OriginalAsset(
        val assetPath: String,
        val kind: String,
        val speed: Int = 255,
        val effect: Int = 2,
        val displayColumns: Int? = null,
        val displayRows: Int? = null
    ) : ProgramContent()

    data class CoolLedUxText(
        val content: CoolLedUxTextContentProgramContent
    ) : ProgramContent()

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
    /** Font point size the glyph is read/rescaled at - FontUtils.CoolleduxTextContentProgramContent.textSize. */
    val textSize: Int = fontHeight,
    val textSpacing: Int = 1,
    val isTextBold: Boolean = false,
    val textRotate: Int = 0,
    val languageCode: String = "",
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

data class CoolLedUxDataResult(
    val body: ByteArray,
    val compressed: ByteArray,
    val chunks: List<ByteArray>
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
        val body = encodeContent(family, content)
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
                extraTypeByte = extraTypeByte,
                // The start header's CRC and length are computed over the
                // *uncompressed* body for every family, not just CoolLEDUX -
                // confirmed identical across CoolledMUtils/CoolledUUtils/
                // CoolledUDUtils/ILedClockUtils/CoolledUXUtils's
                // getStartDataForProgram (all call getDataResult on the
                // pre-compression body). Compression only ever applies to
                // the per-chunk data that follows. An earlier version of
                // this code only set startSource for COOLLEDUX and silently
                // fell back to hashing/measuring the *compressed* bytes for
                // every other family, which would fail the receiving
                // firmware's own CRC/length check on every real transfer.
                startSource = body
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

    internal fun encodeContentForTest(family: DeviceFamily, content: ProgramContent): ByteArray = encodeContent(family, content)

    // NOT byte-exact for any family except COOLLEDUX and COOLLEDU-within-
    // CoolleduGlyphPipeline's scope (showHeight=16, no rescale, text-only,
    // no mirror - see CoolleduProgramBytecode/CoolleduGlyphPipeline's class
    // docs). Every other family, and COOLLEDU outside that scope, has its
    // own real text-content builder in the APK
    // (CoolledMUtils/CoolledUDUtils/ILedClockUtils, plus CoolLEDU's own
    // rescale/emoji/mirror paths, none of which have been reverse-engineered
    // yet - each is a separate, sizable undertaking of the same shape as the
    // CoolLEDUX text/emoji pipeline, not a quick follow-up). The
    // `[0x54, speed, effect, len, text]` shape below is an unverified
    // placeholder that must not be assumed correct; do not "fix" it without
    // first doing the same smali-verified, golden-vector-tested reverse-
    // engineering pass CoolLEDUX (and now part of CoolLEDU) got.
    private fun encodeContent(family: DeviceFamily, content: ProgramContent): ByteArray = when (content) {
        is ProgramContent.Text -> if (family == DeviceFamily.COOLLEDUX) {
            CoolleduxProgramBytecode.text(
                text = content.text,
                speed = content.speed,
                effect = content.effect,
                displayColumns = content.displayColumns,
                displayRows = content.displayRows
            )
        } else if (family == DeviceFamily.COOLLEDU && CoolleduProgramBytecode.supports(content.text)) {
            // Real, verified encoding - but only within CoolleduGlyphPipeline's
            // scoped support (showHeight=16, no rescale, text-only, no
            // mirror - see its class doc). Falls through to the unverified
            // placeholder below for anything outside that scope rather than
            // guessing at the unported rescale/emoji/mirror paths.
            CoolleduProgramBytecode.text(
                text = content.text,
                speed = content.speed,
                effect = content.effect,
                displayColumns = content.displayColumns
            )
        } else {
            val textBytes = content.text.encodeToByteArray()
            byteArrayOf(0x54, content.speed.toByte(), content.effect.toByte(), textBytes.size.toByte()) + textBytes
        }

        is ProgramContent.CoolLedUxText -> {
            require(family == DeviceFamily.COOLLEDUX) { "CoolLedUxText content is only valid for CoolLEDUX" }
            getDataWithProgram(getDataForProgram(content.content))
        }

        is ProgramContent.OriginalAsset -> if (family == DeviceFamily.COOLLEDUX) {
            CoolleduxProgramBytecode.originalAsset(
                assetPath = content.assetPath,
                kind = content.kind,
                speed = content.speed,
                effect = content.effect,
                displayColumns = content.displayColumns,
                displayRows = content.displayRows
            )
        } else {
            val bytes = OriginalLedAssetByteSources.active.read(content.assetPath) ?: ByteArray(0)
            byteArrayOf(0x41, content.kind.take(1).encodeToByteArray().firstOrNull() ?: 0x00, bytes.size.coerceIn(0, 255).toByte()) + bytes
        }

        is ProgramContent.Drawing -> {
            require(content.rgbBytes.size == content.width * content.height * 3) { "Drawing rgbBytes must be width*height*3" }
            byteArrayOf(0x49, content.width.toByte(), content.height.toByte()) + content.rgbBytes
        }

        is ProgramContent.PresetMode -> byteArrayOf(0x4D, content.mode.toByte(), content.intensity.toByte())
    }

    /** FontUtils.getFontByteDataCoolleduxForEmoji(...) modes that use the shared word-wrapped, row-centered canvas (CoolleduxCombineText) instead of the per-glyph stream (CoolleduxStreamText). */
    private val combineCanvasModes = setOf(1, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)

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
        val textBytes = when {
            content.glyphBytes != null -> getFontByteDataCoolleduxForEmoji(content)
            content.mode in combineCanvasModes -> CoolleduxCombineText.encode(content)
            else -> CoolleduxStreamText.encode(content)
        }
        inner += textBytes.toList()
        return (four(inner.size + 4) + inner).toByteArray()
    }

    /** Named shim matching the APK's text-combine branch inside getDataForCombineProgram(...). */
    fun getDataWithTextCombineProgram(program: CoolLedUxCombineProgram.TextCombine): ByteArray =
        getDataWithTextContentProgramContent(program.textContentProgramContent)

    /** Port of CoolledUXUtils.getDataForCombineProgram(...) for known LED-facing content types. */
    fun getDataForCombineProgram(program: CoolLedUxCombineProgram): ByteArray = when (program) {
        is CoolLedUxCombineProgram.TextCombine -> getDataWithTextCombineProgram(program)
    }

    /** Convenience wrapper for a single APK text combine program. */
    fun getDataForProgram(content: CoolLedUxTextContentProgramContent): CoolLedUxProgram =
        CoolLedUxProgram(listOf(CoolLedUxCombineProgram.TextCombine(content)))

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
     * Plain-text port of FontUtils.getFontByteDataCoolleduxForEmoji(...).
     *
     * APK font records are read from recovered font-library assets. Emoji/non-BMP
     * input is deliberately rejected until that APK branch is fully ported; the
     * production upload path must never synthesize fake glyph bytes.
     */
    fun getFontByteDataCoolleduxForEmoji(content: CoolLedUxTextContentProgramContent): ByteArray {
        content.glyphBytes?.let { return it.copyOf() }
        val codePoints = content.text.codePoints().toArray()
        require(codePoints.none { it > 0xFFFF }) {
            "CoolLEDUX emoji/non-BMP text is not yet ported from FontUtils.getFontByteDataCoolleduxForEmoji"
        }
        val widthPerGlyph = content.fontWidth.coerceAtLeast(1)
        val height = content.fontHeight.coerceAtLeast(1)
        val bytesPerColumn = (height + 7) / 8
        val bytesPerGlyph = widthPerGlyph * bytesPerColumn
        val glyphs = codePoints.map { cp ->
            // Matches FontUtils.readFontData(...): the APK returns a zero-filled
            // glyph (not an error) whenever the font table can't produce one -
            // see docs/APK_REVERSE_ENGINEERING_NOTES.md, "readFontData ... blank-glyph fallback".
            readFontGlyph(cp, widthPerGlyph, height, bytesPerGlyph) ?: ByteArray(bytesPerGlyph)
        }
        val out = mutableListOf<Byte>()
        out += two(codePoints.size)
        out += two(codePoints.size * widthPerGlyph)
        glyphs.forEach { out += two(widthPerGlyph) }
        glyphs.forEach { out += it.toList() }
        return out.toByteArray()
    }

    private fun readFontGlyph(codePoint: Int, width: Int, height: Int, bytesPerGlyph: Int): ByteArray? {
        val source = CoolleduxFontSources.active
        val glyph = when {
            height >= 32 || bytesPerGlyph >= 128 -> source.readGlyph32(codePoint, bold = true)
            height >= 16 || bytesPerGlyph >= 32 -> source.readGlyph16(codePoint, bold = false)
            height >= 14 && bytesPerGlyph >= 28 -> source.readGlyph14Bold(codePoint)
            height >= 12 || bytesPerGlyph >= 24 -> source.readGlyph12(codePoint, bold = false)
            else -> source.readGlyph8(codePoint)
        }
        return glyph?.fitTo(bytesPerGlyph)
    }

    private fun ByteArray.fitTo(size: Int): ByteArray = when {
        this.size == size -> copyOf()
        this.size > size -> copyOf(size)
        else -> copyOf(size)
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

data class CoolleduxTextProgramContent(
    val text: String,
    val layerType: Int = 1,
    val startRow: Int = 0,
    val startColumn: Int = 0,
    val showHeight: Int = 32,
    val showWidth: Int = 128,
    val mode: Int = 2,
    val speed: Int = 255,
    val stayTime: Int = 3,
    val isTextBold: Boolean = true,
    val textRotate: Int = 0,
    val isAutoTextSize: Boolean = true,
    val textSize: Int = 32,
    val textSpacing: Int = 1
)

sealed interface CoolleduxLayer

data class CoolleduxTextLayer(
    val layerType: Int,
    val startRow: Int,
    val startColumn: Int,
    val showHeight: Int,
    val showWidth: Int,
    val mode: Int,
    val speed: Int,
    val stayTime: Int,
    val textRotate: Int,
    val isAutoTextSize: Boolean,
    val textSize: Int,
    val textSpacing: Int,
    val isTextBold: Boolean,
    val glyphBytes: ByteArray
) : CoolleduxLayer

data class CoolleduxAssetLayer(
    val layerType: Int,
    val assetKind: String,
    val startRow: Int,
    val startColumn: Int,
    val showHeight: Int,
    val showWidth: Int,
    val mode: Int,
    val speed: Int,
    val stayTime: Int,
    val payloadBytes: ByteArray
) : CoolleduxLayer

data class CoolleduxCombineProgram(
    val layers: List<CoolleduxLayer>
)

data class CoolleduxProgram(
    val combinePrograms: List<CoolleduxCombineProgram>
)

object CoolleduxProgramBytecode {
    fun text(text: String, speed: Int, effect: Int, displayColumns: Int?, displayRows: Int?): ByteArray {
        val rows = displayRows?.coerceIn(8, 128) ?: 32
        val columns = displayColumns?.coerceIn(8, 512) ?: 128
        val fontSize = when {
            rows >= 32 -> 32
            rows >= 16 -> 16
            rows >= 12 -> 12
            else -> 8
        }
        val content = CoolLedUxTextContentProgramContent(
            text = text.ifBlank { "HELLO" }.take(128),
            layerType = 0,
            startColumn = 0,
            startRow = 0,
            showWidth = columns,
            showHeight = rows,
            mode = effect.coerceIn(0, 255),
            speed = speed.coerceIn(0, 255),
            stayTime = 0,
            moveSpace = 0,
            fontWidth = fontSize,
            fontHeight = fontSize
        )
        return ProgramComposer.getDataWithProgram(ProgramComposer.getDataForProgram(content))
    }

    fun originalAsset(assetPath: String, kind: String, speed: Int, effect: Int, displayColumns: Int?, displayRows: Int?): ByteArray {
        val rows = displayRows?.coerceIn(8, 128) ?: 32
        val columns = displayColumns?.coerceIn(8, 512) ?: 128
        val encoded = OriginalLedAssetPayloadEncoder.encode(assetPath, kind, columns, rows)
        val block = when (encoded.format) {
            "raw-gif" -> rawGifContentBlock(
                payload = encoded.bytes,
                layerType = assetLayerType(kind),
                startColumn = 0,
                startRow = 0,
                showWidth = columns,
                showHeight = rows
            )
            else -> graffitiContentBlock(
                payload = encoded.bytes,
                layerType = assetLayerType(kind),
                startColumn = 0,
                startRow = 0,
                showWidth = encoded.width ?: columns,
                showHeight = encoded.height ?: rows,
                mode = effect.coerceIn(0, 255),
                speed = speed.coerceIn(0, 255),
                stayTime = 3
            )
        }
        return wrapProgram(listOf(block))
    }

    private fun wrapProgram(blocks: List<ByteArray>): ByteArray {
        val out = mutableListOf<Byte>()
        repeat(8) { out += 0x00.toByte() }
        out += blocks.size.coerceIn(0, 255).toByte()
        out += 0x00.toByte()
        blocks.forEach { out += it.toList() }
        return out.toByteArray()
    }

    private fun rawGifContentBlock(payload: ByteArray, layerType: Int, startColumn: Int, startRow: Int, showWidth: Int, showHeight: Int): ByteArray {
        val inner = mutableListOf<Byte>()
        inner += 0x0c.toByte()
        repeat(7) { inner += 0x00.toByte() }
        inner += layerType.coerceIn(0, 255).toByte()
        inner += 0x00.toByte()
        inner += u16(startColumn)
        inner += u16(startRow)
        inner += u16(showWidth)
        inner += u16(showHeight)
        inner += u32(payload.size)
        inner += payload.toList()
        return (u32(inner.size + 4) + inner).toByteArray()
    }

    private fun graffitiContentBlock(payload: ByteArray, layerType: Int, startColumn: Int, startRow: Int, showWidth: Int, showHeight: Int, mode: Int, speed: Int, stayTime: Int): ByteArray {
        val inner = mutableListOf<Byte>()
        inner += 0x02.toByte()
        repeat(7) { inner += 0x00.toByte() }
        inner += layerType.coerceIn(0, 255).toByte()
        inner += u16(startColumn)
        inner += u16(startRow)
        inner += u16(showWidth)
        inner += u16(showHeight)
        inner += mode.coerceIn(0, 255).toByte()
        inner += speed.coerceIn(0, 255).toByte()
        inner += stayTime.coerceIn(0, 255).toByte()
        inner += u32(payload.size)
        inner += payload.toList()
        return (u32(inner.size + 4) + inner).toByteArray()
    }

    private fun assetLayerType(kind: String): Int = when (kind.lowercase()) {
        "animation" -> 3
        "emoji" -> 4
        "icon" -> 5
        "image" -> 6
        "clock-template" -> 7
        "sensor-template" -> 8
        else -> 9
    }

    private fun u16(value: Int): List<Byte> = listOf(((value ushr 8) and 0xFF).toByte(), (value and 0xFF).toByte())
    private fun u32(value: Int): List<Byte> = listOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )
}


private object CoolleduxFontByteBuilder {
    fun renderText(content: CoolleduxTextProgramContent): ByteArray {
        val out = mutableListOf<Byte>()
        val codePoints = content.text.codePoints().toArray()
        codePoints.forEachIndexed { idx, cp ->
            val glyph = when {
                content.textSize >= 32 -> CoolleduxFontSources.active.readGlyph32(cp, content.isTextBold)
                content.textSize >= 16 -> CoolleduxFontSources.active.readGlyph16(cp, content.isTextBold)
                content.textSize >= 14 && content.isTextBold -> CoolleduxFontSources.active.readGlyph14Bold(cp)
                content.textSize >= 12 -> CoolleduxFontSources.active.readGlyph12(cp, content.isTextBold)
                else -> CoolleduxFontSources.active.readGlyph8(cp)
            }
            // Matches FontUtils.readFontData(...): the APK returns a zero-filled
            // glyph (not an error) whenever the font table can't produce one -
            // see docs/APK_REVERSE_ENGINEERING_NOTES.md, "readFontData ... blank-glyph fallback".
            out += (glyph ?: ByteArray(bytesPerColumn(content.textSize) * content.textSize)).toList()
            if (idx != codePoints.lastIndex && content.textSpacing > 0) {
                repeat(content.textSpacing) {
                    repeat(bytesPerColumn(content.textSize)) { out += 0x00.toByte() }
                }
            }
        }
        return out.toByteArray()
    }

    private fun bytesPerColumn(textSize: Int): Int = when {
        textSize >= 32 -> 4
        textSize >= 24 -> 3
        textSize >= 16 -> 2
        else -> 1
    }
}
