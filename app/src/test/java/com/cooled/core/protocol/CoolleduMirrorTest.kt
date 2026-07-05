package com.cooled.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CoolleduMirrorTest {
    @Test
    fun mirrorReversesTwoByteColumnsKeepingInColumnByteOrder() {
        // Three 2-byte columns [C0,C1,C2] -> reversed to [C2,C1,C0], each
        // column's own two bytes untouched - hand-traced from FontUtils.mirror.
        val input = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
        val expected = byteArrayOf(0x05, 0x06, 0x03, 0x04, 0x01, 0x02)
        assertArrayEquals(expected, CoolleduMirror.mirror(input, bytesPerColumn = 2))
    }

    @Test
    fun mirrorReversesFourByteColumns() {
        val input = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val expected = byteArrayOf(5, 6, 7, 8, 1, 2, 3, 4)
        assertArrayEquals(expected, CoolleduMirror.mirror(input, bytesPerColumn = 4))
    }

    @Test
    fun splitIntoRowsZeroPadsTheFinalPartialRow() {
        // 3 columns of 2 bytes each, showWidth=2 -> one full row (2 cols) plus
        // one padded row (1 real col + 1 zero col).
        val input = byteArrayOf(1, 2, 3, 4, 5, 6)
        val rows = CoolleduMirror.splitIntoRows(input, showWidth = 2, bytesPerColumn = 2)
        assertArrayEquals(arrayOf(byteArrayOf(1, 2, 3, 4), byteArrayOf(5, 6, 0, 0)), rows.toTypedArray())
    }

    @Test
    fun mirrorCombineMirrorsEachSplitRowIndependently() {
        val input = byteArrayOf(1, 2, 3, 4, 5, 6)
        // Row 1 = [1,2,3,4] mirrored -> [3,4,1,2]; row 2 = [5,6,0,0] mirrored -> [0,0,5,6].
        val expected = byteArrayOf(3, 4, 1, 2, 0, 0, 5, 6)
        assertArrayEquals(expected, CoolleduMirror.mirrorCombine(input, showWidth = 2, bytesPerColumn = 2))
    }
}
