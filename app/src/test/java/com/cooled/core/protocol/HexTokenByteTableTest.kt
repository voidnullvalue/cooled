package com.cooled.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class HexTokenByteTableTest {
    @Test
    fun parsesCommaSeparatedHexBytesUnlikeTheDecimalCommaSeparatedTable() {
        assertArrayEquals(byteArrayOf(0x0F, 0x00, 0xA0.toByte()), HexTokenByteTable.parse("0F,00,A0"))
    }

    @Test
    fun trimsEmbeddedNewlinesFromMultilineTableLiterals() {
        assertArrayEquals(byteArrayOf(0x0F, 0x10, 0x20), HexTokenByteTable.parse("0F,\n10,\n20"))
    }

    @Test
    fun ignoresATrailingComma() {
        assertArrayEquals(byteArrayOf(0x01, 0x02), HexTokenByteTable.parse("01,02,"))
    }

    @Test
    fun emptyStringParsesToAnEmptyArray() {
        assertArrayEquals(ByteArray(0), HexTokenByteTable.parse(""))
    }
}
