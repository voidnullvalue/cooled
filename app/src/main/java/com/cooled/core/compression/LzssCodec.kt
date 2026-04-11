package com.cooled.core.compression

/**
 * LZSS codec with parameters observed in reverse-engineering artifacts:
 * N=512, F=18, THRESHOLD=2.
 *
 * Flag bit order is confirmed as LSB-first from the decompiled compressor loop
 * (`mask = 1; mask <<= 1`) in base_apk_protocol_sources.zip
 * `CoolledUUtils$LzssCompress.lazssCompress` and sibling family compressors.
 */
object LzssCodec {
    private const val N = 512
    private const val F = 18
    private const val THRESHOLD = 2

    fun compress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return byteArrayOf()
        val ring = ByteArray(N)
        var r = N - F
        var src = 0
        val out = ArrayList<Byte>()

        while (src < input.size) {
            val flagPos = out.size
            out += 0
            var flags = 0
            var bitCount = 0

            while (bitCount < 8 && src < input.size) {
                var bestLen = 0
                var bestPos = 0
                for (pos in 0 until N) {
                    var len = 0
                    while (len < F && src + len < input.size && ring[(pos + len) and (N - 1)] == input[src + len]) {
                        len++
                    }
                    if (len > bestLen) {
                        bestLen = len
                        bestPos = pos
                    }
                }

                if (bestLen <= THRESHOLD) {
                    flags = flags or (1 shl bitCount)
                    val b = input[src]
                    out += b
                    ring[r] = b
                    r = (r + 1) and (N - 1)
                    src++
                } else {
                    out += (bestPos and 0xFF).toByte()
                    out += (((bestPos ushr 4) and 0xF0) or ((bestLen - 3) and 0x0F)).toByte()
                    repeat(bestLen) {
                        val b = input[src + it]
                        ring[r] = b
                        r = (r + 1) and (N - 1)
                    }
                    src += bestLen
                }
                bitCount++
            }
            out[flagPos] = flags.toByte()
        }
        return out.toByteArray()
    }

    fun decompress(input: ByteArray): ByteArray {
        if (input.isEmpty()) return byteArrayOf()
        val ring = ByteArray(N)
        var r = N - F
        var index = 0
        val out = ArrayList<Byte>()

        while (index < input.size) {
            val flags = input[index].toInt() and 0xFF
            index++
            for (bit in 0 until 8) {
                if (index >= input.size) break
                val isLiteral = (flags and (1 shl bit)) != 0
                if (isLiteral) {
                    val b = input[index++]
                    out += b
                    ring[r] = b
                    r = (r + 1) and (N - 1)
                } else {
                    if (index + 1 >= input.size) break
                    val b1 = input[index++].toInt() and 0xFF
                    val b2 = input[index++].toInt() and 0xFF
                    var pos = b1 or ((b2 and 0xF0) shl 4)
                    val len = (b2 and 0x0F) + 3
                    repeat(len) {
                        val b = ring[pos and (N - 1)]
                        out += b
                        ring[r] = b
                        r = (r + 1) and (N - 1)
                        pos++
                    }
                }
            }
        }
        return out.toByteArray()
    }
}
