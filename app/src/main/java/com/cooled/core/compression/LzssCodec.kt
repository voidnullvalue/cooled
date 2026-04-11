package com.cooled.core.compression

/**
 * LZSS codec with parameters observed in reverse-engineering artifacts:
 * N=512, F=18, THRESHOLD=2.
 *
 * Ambiguity isolated to [FlagBitOrder]: static artifacts do not conclusively prove whether
 * token flags are consumed LSB-first or MSB-first. Default is LSB-first.
 */
object LzssCodec {
    private const val N = 512
    private const val F = 18
    private const val THRESHOLD = 2

    enum class FlagBitOrder { LSB_FIRST, MSB_FIRST }

    fun compress(input: ByteArray, bitOrder: FlagBitOrder = FlagBitOrder.LSB_FIRST): ByteArray {
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
                    flags = setFlag(flags, bitOrder, bitCount)
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

    fun decompress(input: ByteArray, bitOrder: FlagBitOrder = FlagBitOrder.LSB_FIRST): ByteArray {
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
                if (isLiteral(flags, bitOrder, bit)) {
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

    private fun setFlag(flags: Int, order: FlagBitOrder, bit: Int): Int {
        val shift = if (order == FlagBitOrder.LSB_FIRST) bit else (7 - bit)
        return flags or (1 shl shift)
    }

    private fun isLiteral(flags: Int, order: FlagBitOrder, bit: Int): Boolean {
        val shift = if (order == FlagBitOrder.LSB_FIRST) bit else (7 - bit)
        return (flags and (1 shl shift)) != 0
    }
}
