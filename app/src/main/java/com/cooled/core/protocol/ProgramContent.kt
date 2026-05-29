package com.cooled.core.protocol

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
    data class Drawing(val width: Int, val height: Int, val rgbBytes: ByteArray) : ProgramContent()
    data class PresetMode(val mode: Int, val intensity: Int) : ProgramContent()
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
                startSource = if (family == DeviceFamily.COOLLEDUX) body else null
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

    private fun encodeContent(family: DeviceFamily, content: ProgramContent): ByteArray = when (content) {
        is ProgramContent.Text -> if (family == DeviceFamily.COOLLEDUX) {
            CoolleduxProgramBytecode.text(
                text = content.text,
                speed = content.speed,
                effect = content.effect,
                displayColumns = content.displayColumns,
                displayRows = content.displayRows
            )
        } else {
            val textBytes = content.text.encodeToByteArray()
            byteArrayOf(0x54, content.speed.toByte(), content.effect.toByte(), textBytes.size.toByte()) + textBytes
        }

        is ProgramContent.Drawing -> {
            require(content.rgbBytes.size == content.width * content.height * 3) { "Drawing rgbBytes must be width*height*3" }
            byteArrayOf(0x49, content.width.toByte(), content.height.toByte()) + content.rgbBytes
        }

        is ProgramContent.PresetMode -> byteArrayOf(0x4D, content.mode.toByte(), content.intensity.toByte())
    }
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
)

data class CoolleduxCombineProgram(
    val layers: List<CoolleduxTextLayer>
)

data class CoolleduxProgram(
    val combinePrograms: List<CoolleduxCombineProgram>
)

object CoolleduxProgramBytecode {
    fun text(text: String, speed: Int, effect: Int, displayColumns: Int?, displayRows: Int?): ByteArray {
        val rows = displayRows?.coerceIn(8, 128) ?: 32
        val columns = displayColumns?.coerceIn(8, 512) ?: 128
        val textSize = when {
            rows >= 32 -> 32
            rows >= 24 -> 24
            rows >= 16 -> 16
            else -> 8
        }
        val content = CoolleduxTextProgramContent(
            text = text.ifBlank { "HELLO" }.take(128),
            showHeight = rows,
            showWidth = columns,
            mode = effect.coerceIn(0, 255),
            speed = speed.coerceIn(0, 255),
            textSize = textSize
        )
        return getDataResult(getDataForProgram(textContentProgram(content)))
    }

    private fun textContentProgram(content: CoolleduxTextProgramContent): CoolleduxProgram {
        val glyphBytes = CoolleduxFontByteBuilder.renderText(content)
        val layer = CoolleduxTextLayer(
            layerType = content.layerType,
            startRow = content.startRow,
            startColumn = content.startColumn,
            showHeight = content.showHeight,
            showWidth = content.showWidth,
            mode = content.mode,
            speed = content.speed,
            stayTime = content.stayTime,
            textRotate = content.textRotate,
            isAutoTextSize = content.isAutoTextSize,
            textSize = content.textSize,
            textSpacing = content.textSpacing,
            isTextBold = content.isTextBold,
            glyphBytes = glyphBytes
        )
        return CoolleduxProgram(listOf(CoolleduxCombineProgram(listOf(layer))))
    }

    private fun getDataForProgram(program: CoolleduxProgram): ByteArray {
        val out = mutableListOf<Byte>()
        repeat(8) { out += 0x00.toByte() }
        out += program.combinePrograms.size.coerceIn(0, 255).toByte()
        out += 0x00.toByte()
        program.combinePrograms.forEach { combine -> out += getDataForCombineProgram(combine).toList() }
        return out.toByteArray()
    }

    private fun getDataForCombineProgram(combine: CoolleduxCombineProgram): ByteArray {
        val body = mutableListOf<Byte>()
        combine.layers.forEach { layer -> body += getDataWithTextCombineProgram(layer).toList() }
        return (u32(body.size + 4) + body).toByteArray()
    }

    private fun getDataWithTextCombineProgram(layer: CoolleduxTextLayer): ByteArray = getDataWithTextContentProgramContent(layer)

    private fun getDataWithTextContentProgramContent(layer: CoolleduxTextLayer): ByteArray {
        val out = mutableListOf<Byte>()
        out += layer.layerType.coerceIn(0, 255).toByte()
        out += layer.textRotate.coerceIn(0, 255).toByte()
        out += (if (layer.isAutoTextSize) 1 else 0).toByte()
        out += layer.textSize.coerceIn(0, 255).toByte()
        out += layer.textSpacing.coerceIn(0, 255).toByte()
        out += (if (layer.isTextBold) 1 else 0).toByte()
        out += 0x00.toByte()
        out += 0x00.toByte()
        out += 0x01.toByte()
        out += u16(layer.startColumn)
        out += u16(layer.startRow)
        out += u16(layer.showWidth)
        out += u16(layer.showHeight)
        out += layer.mode.coerceIn(0, 255).toByte()
        out += layer.speed.coerceIn(0, 255).toByte()
        out += layer.stayTime.coerceIn(0, 255).toByte()
        out += u16(layer.textSpacing)
        out += u32(layer.glyphBytes.size)
        out += layer.glyphBytes.toList()
        return out.toByteArray()
    }

    private fun getDataResult(programBytes: ByteArray): ByteArray = programBytes

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
            val glyph = if (content.textSize >= 32) {
                CoolleduxFontSources.active.readGlyph32(cp, content.isTextBold) ?: BuiltinCoolleduxFontSource.readGlyph32(cp, content.isTextBold)!!
            } else {
                CoolleduxFontSources.active.readGlyph8(cp) ?: BuiltinCoolleduxFontSource.readGlyph8(cp)!!
            }
            out += glyph.toList()
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
