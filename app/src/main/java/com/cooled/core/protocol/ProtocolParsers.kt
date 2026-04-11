package com.cooled.core.protocol

sealed class ParsedPayload {
    data class Ack(val opcode: Int, val data: ByteArray) : ParsedPayload()
    data class Unknown(val data: ByteArray) : ParsedPayload()
}

object ProtocolParsers {
    fun parseFrame(frame: ByteArray): ParsedPayload {
        val payload = FrameCodec.decode(frame)
        if (payload.isEmpty()) return ParsedPayload.Unknown(payload)
        return ParsedPayload.Ack(payload[0].toInt() and 0xFF, payload)
    }
}
