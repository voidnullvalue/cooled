package com.cooled.core.protocol

object FrameCodec {
    fun encode(payload: ByteArray): ByteArray {
        val len = payload.size
        val inner = byteArrayOf(((len shr 8) and 0xFF).toByte(), (len and 0xFF).toByte()) + payload
        val escaped = ArrayList<Byte>()
        inner.forEach { b ->
            if (b == 0x01.toByte() || b == 0x02.toByte() || b == 0x03.toByte()) {
                escaped.add(BleProtocolConstants.frameEscape)
                escaped.add((b.toInt() xor 0x04).toByte())
            } else escaped.add(b)
        }
        return byteArrayOf(BleProtocolConstants.frameStart) + escaped.toByteArray() + byteArrayOf(BleProtocolConstants.frameEnd)
    }

    fun decode(frame: ByteArray): ByteArray {
        require(frame.first() == BleProtocolConstants.frameStart && frame.last() == BleProtocolConstants.frameEnd)
        val inBytes = frame.sliceArray(1 until frame.lastIndex)
        val unescaped = ArrayList<Byte>()
        var i = 0
        while (i < inBytes.size) {
            if (inBytes[i] == BleProtocolConstants.frameEscape) {
                unescaped.add((inBytes[i + 1].toInt() xor 0x04).toByte())
                i += 2
            } else {
                unescaped.add(inBytes[i])
                i++
            }
        }
        val payloadLen = ((unescaped[0].toInt() and 0xFF) shl 8) or (unescaped[1].toInt() and 0xFF)
        return unescaped.drop(2).take(payloadLen).toByteArray()
    }
}
