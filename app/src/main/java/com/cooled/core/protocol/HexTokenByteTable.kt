package com.cooled.core.protocol

/**
 * Some literal color-mode tables in ILedClockUtils.setColorMode embed their
 * bytes as comma-separated *hex* string tokens directly (e.g.
 * `"0F,00,0F,10,...` - each token IS the byte, parsed with radix 16 and
 * added straight to the output token list with no further conversion. This
 * is a different convention from CommaSeparatedByteTable (used by the
 * clock/date template builders), whose tokens are *decimal* strings needing
 * a decimal-to-hex conversion step. Tokens may contain embedded newlines
 * (the Java source wraps long tables across multiple lines); trim() strips
 * those along with any other whitespace.
 */
object HexTokenByteTable {
    fun parse(commaSeparatedHexBytes: String): ByteArray =
        commaSeparatedHexBytes.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.toInt(16).toByte() }
            .toByteArray()
}
