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
        textContent += 0x01.toByte()
        repeat(7) { textContent += 0x00.toByte() }
        textContent += 0x01.toByte()
        textContent += u16(0)
        textContent += u16(0)
        textContent += u16(showWidth)
        textContent += u16(showHeight)
        textContent += mode.toByte()
        textContent += speedByte.toByte()
        textContent += 0x00.toByte()
        textContent += u16(0)
        textContent += glyphBytes.toList()

        val combineBlock = mutableListOf<Byte>()
        combineBlock += u32(textContent.size + 4)
        combineBlock += textContent

        val program = mutableListOf<Byte>()
        repeat(8) { program += 0x00.toByte() }
        program += 0x01.toByte()
        program += 0x00.toByte()
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
    private val apkGlyphs = mapOf(
        'H' to byteArrayOf(0xFF.toByte(), 0x10.toByte(), 0x10.toByte(), 0x10.toByte(), 0x10.toByte(), 0x10.toByte(), 0xFF.toByte(), 0x00.toByte()),
        'E' to byteArrayOf(0x81.toByte(), 0xFF.toByte(), 0x91.toByte(), 0x91.toByte(), 0xB9.toByte(), 0x81.toByte(), 0xC3.toByte(), 0x00.toByte()),
        'L' to byteArrayOf(0x81.toByte(), 0xFF.toByte(), 0x81.toByte(), 0x01.toByte(), 0x01.toByte(), 0x01.toByte(), 0x03.toByte(), 0x00.toByte()),
        'O' to byteArrayOf(0x7E.toByte(), 0x81.toByte(), 0x81.toByte(), 0x81.toByte(), 0x81.toByte(), 0x81.toByte(), 0x7E.toByte(), 0x00.toByte()),
        ' ' to ByteArray(8) { 0x00 }
    )

    fun renderColumns(text: String): ByteArray {
        val out = mutableListOf<Byte>()
        text.uppercase().forEach { ch -> out += (apkGlyphs[ch] ?: fallback5x7(ch)).toList() }
        return out.toByteArray()
    }

    private fun fallback5x7(ch: Char): ByteArray {
        val rows = fallbackRows[ch] ?: fallbackRows['?']!!
        val columns = ByteArray(8) { 0x00 }
        for (x in 0 until 5) {
            var value = 0
            for (y in rows.indices) {
                if (rows[y][x] == '1') value = value or (1 shl (y + 1))
            }
            columns[x + 1] = value.toByte()
        }
        return columns
    }

    private val fallbackRows = mapOf(
        'A' to listOf("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
        'B' to listOf("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
        'C' to listOf("01111", "10000", "10000", "10000", "10000", "10000", "01111"),
        'D' to listOf("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
        'F' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
        'G' to listOf("01111", "10000", "10000", "10011", "10001", "10001", "01111"),
        'I' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "11111"),
        'M' to listOf("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
        'N' to listOf("10001", "11001", "10101", "10011", "10001", "10001", "10001"),
        'S' to listOf("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
        'T' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
        'U' to listOf("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
        '0' to listOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
        '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        '3' to listOf("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
        '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        '5' to listOf("11111", "10000", "10000", "11110", "00001", "00001", "11110"),
        '6' to listOf("01110", "10000", "10000", "11110", "10001", "10001", "01110"),
        '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        '9' to listOf("01110", "10001", "10001", "01111", "00001", "00001", "01110"),
        '?' to listOf("01110", "10001", "00001", "00010", "00100", "00000", "00100")
    )
}
