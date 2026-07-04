package com.cooled.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CommaSeparatedByteTableTest {
    @Test
    fun parsesCommaSeparatedDecimalBytesWithWhitespaceTrimming() {
        assertArrayEquals(byteArrayOf(127, -32, 0, 1), CommaSeparatedByteTable.parse("127, 224, 0, 1"))
    }

    @Test
    fun parsesAFullByteRangeIncludingValuesAbove127() {
        // 255 as an unsigned byte value is -1 as a signed Kotlin Byte.
        assertArrayEquals(byteArrayOf(0, 255.toByte(), 128.toByte()), CommaSeparatedByteTable.parse("0,255,128"))
    }

    @Test
    fun parsesASingleValueWithNoCommas() {
        assertArrayEquals(byteArrayOf(42), CommaSeparatedByteTable.parse("42"))
    }
}
