package com.cooled.core.compression

/**
 * Conservative literal-only fallback.
 * TODO/UNRESOLVED: replace with exact encoder token stream parity from decompiled classes.
 */
object LzssCodec {
    fun compress(input: ByteArray): ByteArray = input
    fun decompress(input: ByteArray): ByteArray = input
}
