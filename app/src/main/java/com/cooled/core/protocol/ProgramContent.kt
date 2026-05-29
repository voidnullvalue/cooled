package com.cooled.core.protocol

import com.cooled.core.compression.LzssCodec
import com.cooled.core.model.DeviceFamily

sealed class ProgramContent {
    data class Text(val text: String, val speed: Int, val effect: Int) : ProgramContent()
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
            CoolleduxTextPayload.encode(content.text, content.speed, content.effect)
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

private object CoolleduxTextPayload {
    fun encode(text: String, speed: Int, effect: Int): ByteArray {
        val clean = text.ifBlank { "HELLO" }.take(128)
        val glyphBytes = Apk8SmallFont.renderColumns(clean)
        val showWidth = maxOf(8, glyphBytes.size)
        val showHeight = 8
        val mode = effect.coerceIn(0, 255)
        val speedByte = speed.coerceIn(0, 255)

        val textContent = mutableListOf<Byte>()
        textContent += 0x01
        repeat(7) { textContent += 0x00 }
        textContent += 0x01
        textContent += u16(0)
        textContent += u16(0)
        textContent += u16(showWidth)
        textContent += u16(showHeight)
        textContent += mode.toByte()
        textContent += speedByte.toByte()
        textContent += 0x00
        textContent += u16(0)
        textContent += glyphBytes.toList()

        val combineBlock = mutableListOf<Byte>()
        combineBlock += u32(textContent.size + 4)
        combineBlock += textContent

        val program = mutableListOf<Byte>()
        repeat(8) { program += 0x00 }
        program += 0x01
        program += 0x00
        program += combineBlock
        return program.toByteArray()
    }

    private fun u16(value: Int): List<Byte> = listOf(((value ushr 8) and 0xFF).toByte(), (value and 0xFF).toByte())
    private fun u32(value: Int): List<Byte> = listOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )
}

private object Apk8SmallFont {
    private val glyphs = mapOf(
        'A' to byteArrayOf(0x1F.toByte(), 0x24.toByte(), 0x44.toByte(), 0x84.toByte(), 0x44.toByte(), 0x24.toByte(), 0x1F.toByte(), 0x00.toByte()),
        'B' to byteArrayOf(0x81.toByte(), 0xFF.toByte(), 0x91.toByte(), 0x91.toByte(), 0x91.toByte(), 0x91.toByte(), 0x6E.toByte(), 0x00.toByte()),
        'C' to byteArrayOf(0x3C.toByte(), 0x42.toByte(), 0x81.toByte(), 0x81.toByte(), 0x81.toByte(), 0x81.toByte(), 0x42.toByte(), 0x00.toByte()),
        'D' to byteArrayOf(0x81.toByte(), 0xFF.toByte(), 0x81.toByte(), 0x81.toByte(), 0x81.toByte(), 0x81.toByte(), 0x7E.toByte(), 0x00.toByte()),
        'E' to byteArrayOf(0x81.toByte(), 0xFF.toByte(), 0x91.toByte(), 0x91.toByte(), 0xB9.toByte(), 0x81.toByte(), 0xC3.toByte(), 0x00.toByte()),
        'F' to byteArrayOf(0x81.toByte(), 0xFF.toByte(), 0x91.toByte(), 0x90.toByte(), 0xB8.toByte(), 0x80.toByte(), 0xC0.toByte(), 0x00.toByte()),
        'G' to byteArrayOf(0x3C.toByte(), 0x42.toByte(), 0x81.toByte(), 0x81.toByte(), 0x81.toByte(), 0x89.toByte(), 0x4E.toByte(), 0x00.toByte()),
        'H' to byteArrayOf(0xFF.toByte(), 0x10.toByte(), 0x10.toByte(), 0x10.toByte(), 0x10.toByte(), 0x10.toByte(), 0xFF.toByte(), 0x00.toByte()),
        'I' to byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x81.toByte(), 0xFF.toByte(), 0x81.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()),
        'J' to byteArrayOf(0x02.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0x81.toByte(), 0xFE.toByte(), 0x80.toByte(), 0x00.toByte()),
        'K' to byteArrayOf(0xFF.toByte(), 0x08.toByte(), 0x14.toByte(), 0x22.toByte(), 0x42.toByte(), 0x81.toByte(), 0x81.toByte(), 0x00.toByte()),
        'L' to byteArrayOf(0x81.toByte(), 0xFF.toByte(), 0x81.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0x03.toByte(), 0x00.toByte()),
        'M' to byteArrayOf(0xFF.toByte(), 0x40.toByte(), 0x20.toByte(), 0x10.toByte(), 0x20.toByte(), 0x40.toByte(), 0xFF.toByte(), 0x00.toByte()),
        'N' to byteArrayOf(0xFF.toByte(), 0x40.toByte(), 0x20.toByte(), 0x10.toByte(), 0x08.toByte(), 0x04.toByte(), 0xFF.toByte(), 0x00.toByte()),
        'O' to byteArrayOf(0x7E.toByte(), 0x81.toByte(), 0x81.toByte(), 0x81.toByte(), 0x81.toByte(), 0x81.toByte(), 0x7E.toByte(), 0x00.toByte()),
        'P' to byteArrayOf(0x81.toByte(), 0xFF.toByte(), 0x89.toByte(), 0x88.toByte(), 0x88.toByte(), 0x88.toByte(), 0x70.toByte(), 0x00.toByte()),
        'Q' to byteArrayOf(0x7E.toByte(), 0x81.toByte(), 0x81.toByte(), 0x81.toByte(), 0x83.toByte(), 0x7E.toByte(), 0x01.toByte(), 0x00.toByte()),
        'R' to byteArrayOf(0x81.toByte(), 0xFF.toByte(), 0x91.toByte(), 0x98.toByte(), 0x94.toByte(), 0x92.toByte(), 0x61.toByte(), 0x00.toByte()),
        'S' to byteArrayOf(0x62.toByte(), 0x91.toByte(), 0x91.toByte(), 0x89.toByte(), 0x89.toByte(), 0x89.toByte(), 0x46.toByte(), 0x00.toByte()),
        'T' to byteArrayOf(0x00.toByte(), 0xC0.toByte(), 0x81.toByte(), 0xFF.toByte(), 0x81.toByte(), 0xC0.toByte(), 0x00.toByte(), 0x00.toByte()),
        'U' to byteArrayOf(0xFE.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0xFE.toByte(), 0x00.toByte()),
        'V' to byteArrayOf(0xF8.toByte(), 0x04.toByte(), 0x02.toByte(), 0x01.toByte(), 0x02.toByte(), 0x04.toByte(), 0xF8.toByte(), 0x00.toByte()),
        'W' to byteArrayOf(0xFC.toByte(), 0x02.toByte(), 0x03.toByte(), 0x0E.toByte(), 0x03.toByte(), 0x02.toByte(), 0xFC.toByte(), 0x00.toByte()),
        'X' to byteArrayOf(0xC1.toByte(), 0x22.toByte(), 0x14.toByte(), 0x08.toByte(), 0x14.toByte(), 0x22.toByte(), 0xC1.toByte(), 0x00.toByte()),
        'Y' to byteArrayOf(0x00.toByte(), 0xE0.toByte(), 0x11.toByte(), 0x0F.toByte(), 0x11.toByte(), 0xE0.toByte(), 0x00.toByte(), 0x00.toByte()),
        'Z' to byteArrayOf(0xC3.toByte(), 0x85.toByte(), 0x85.toByte(), 0x89.toByte(), 0x91.toByte(), 0xA1.toByte(), 0xC1.toByte(), 0x00.toByte()),
        '0' to byteArrayOf(0x7E.toByte(), 0x81.toByte(), 0x81.toByte(), 0x81.toByte(), 0x81.toByte(), 0x81.toByte(), 0x7E.toByte(), 0x00.toByte()),
        '1' to byteArrayOf(0x00.toByte(), 0x21.toByte(), 0x41.toByte(), 0xFF.toByte(), 0x01.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte()),
        '2' to byteArrayOf(0x63.toByte(), 0x85.toByte(), 0x89.toByte(), 0x89.toByte(), 0x91.toByte(), 0x91.toByte(), 0x61.toByte(), 0x00.toByte()),
        '3' to byteArrayOf(0x42.toByte(), 0xC3.toByte(), 0x81.toByte(), 0x81.toByte(), 0x91.toByte(), 0x91.toByte(), 0x6E.toByte(), 0x00.toByte()),
        '4' to byteArrayOf(0x1C.toByte(), 0x24.toByte(), 0x44.toByte(), 0x85.toByte(), 0xFF.toByte(), 0x05.toByte(), 0x04.toByte(), 0x00.toByte()),
        '5' to byteArrayOf(0xF2.toByte(), 0x93.toByte(), 0x91.toByte(), 0x91.toByte(), 0x91.toByte(), 0x91.toByte(), 0x8E.toByte(), 0x00.toByte()),
        '6' to byteArrayOf(0x7E.toByte(), 0x91.toByte(), 0x91.toByte(), 0x91.toByte(), 0x91.toByte(), 0x91.toByte(), 0x8E.toByte(), 0x00.toByte()),
        '7' to byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x87.toByte(), 0x88.toByte(), 0x90.toByte(), 0xE0.toByte(), 0x00.toByte()),
        '8' to byteArrayOf(0x76.toByte(), 0x89.toByte(), 0x89.toByte(), 0x89.toByte(), 0x89.toByte(), 0x89.toByte(), 0x76.toByte(), 0x00.toByte()),
        '9' to byteArrayOf(0x72.toByte(), 0x89.toByte(), 0x89.toByte(), 0x89.toByte(), 0x89.toByte(), 0x89.toByte(), 0x7E.toByte(), 0x00.toByte()),
        ' ' to byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()),
        '!' to byteArrayOf(0x00.toByte(), 0x00.toByte(), 0xF9.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()),
        '-' to byteArrayOf(0x10.toByte(), 0x10.toByte(), 0x10.toByte(), 0x10.toByte(), 0x10.toByte(), 0x10.toByte(), 0x00.toByte(), 0x00.toByte()),
        '.' to byteArrayOf(0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()),
        '?' to byteArrayOf(0x60.toByte(), 0x80.toByte(), 0x80.toByte(), 0x8D.toByte(), 0x90.toByte(), 0x60.toByte(), 0x00.toByte(), 0x00.toByte())
    )

    fun renderColumns(text: String): ByteArray {
        val out = mutableListOf<Byte>()
        text.uppercase().forEach { ch -> out += (glyphs[ch] ?: glyphs['?']!!).toList() }
        return out.toByteArray()
    }
}