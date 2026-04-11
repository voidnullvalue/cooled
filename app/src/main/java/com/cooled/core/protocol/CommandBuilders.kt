package com.cooled.core.protocol

import com.cooled.core.crc.CoolLedCrc
import kotlin.random.Random

object CommandBuilders {
    fun queryDeviceInfo(): ByteArray = FrameCodec.encode(byteArrayOf(0x1F.toByte()))
    fun setBrightness(value: Int): ByteArray = FrameCodec.encode(byteArrayOf(0x04.toByte(), value.toByte()))
    fun setPower(on: Boolean): ByteArray = FrameCodec.encode(byteArrayOf(0x05.toByte(), (if (on) 0x01 else 0x00).toByte()))
    fun setRhythm(type: Int): ByteArray = FrameCodec.encode(byteArrayOf(0x06.toByte(), type.toByte()))
    fun setMirror(value: Int): ByteArray = FrameCodec.encode(byteArrayOf(0x0C.toByte(), value.toByte()))
    fun queryTimerSwitches(): ByteArray = FrameCodec.encode(byteArrayOf(0x0B.toByte()))

    fun checkPassword(password: String): ByteArray = passwordPayload(0x0D, password)
    fun setPassword(password: String): ByteArray = passwordPayload(0x0E, password)

    private fun passwordPayload(opcode: Int, password: String): ByteArray {
        val rand = Random.nextInt(0, 256)
        val masked = password.map { ch -> (("0$ch".toInt(16)) xor rand).toByte() }
        val xor = masked.fold(0) { acc, b -> acc xor (b.toInt() and 0xFF) }
        return FrameCodec.encode(byteArrayOf(opcode.toByte(), rand.toByte()) + masked.toByteArray() + byteArrayOf(xor.toByte()))
    }

    fun buildStartProgram(opcode: Int, compressed: ByteArray, index: Int, count: Int? = null, showCount: Int? = null): ByteArray {
        val crc = CoolLedCrc.crc32Like(compressed)
        val base = mutableListOf(opcode.toByte())
        base += intToBytes(crc)
        base += intToBytes(compressed.size)
        base += (index and 0xFF).toByte()
        count?.let { base += (it and 0xFF).toByte() }
        showCount?.let { base += (it and 0xFF).toByte() }
        return FrameCodec.encode(base.toByteArray())
    }

    fun buildDataChunk(messageType: Int, totalCompressedLength: Int, chunkIndex: Int, chunk: ByteArray): ByteArray {
        val body = mutableListOf(messageType.toByte(), 0x00)
        body += intToBytes(totalCompressedLength)
        body += shortToBytes(chunkIndex)
        body += shortToBytes(chunk.size)
        body += chunk.toList()
        val xor = body.fold(0) { acc, b -> acc xor (b.toInt() and 0xFF) }
        body += xor.toByte()
        return FrameCodec.encode(body.toByteArray())
    }

    fun splitChunks(data: ByteArray, chunkSize: Int = 1024): List<ByteArray> = data.toList().chunked(chunkSize).map { it.toByteArray() }

    private fun intToBytes(value: Int) = listOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    private fun shortToBytes(value: Int) = listOf(((value ushr 8) and 0xFF).toByte(), (value and 0xFF).toByte())
}
