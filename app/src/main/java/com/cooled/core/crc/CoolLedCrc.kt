package com.cooled.core.crc

object CoolLedCrc {
    private const val POLY = 0x04C11DB7

    fun crc32Like(input: ByteArray): Int {
        var crc = -1
        input.forEach { b ->
            var mask = 0x80000000.toInt()
            repeat(32) {
                crc = if (crc and 0x80000000.toInt() != 0) (crc shl 1) xor POLY else crc shl 1
                if ((b.toInt() and 0xFF and mask) != 0) crc = crc xor POLY
                mask = mask ushr 1
            }
        }
        return crc
    }
}
