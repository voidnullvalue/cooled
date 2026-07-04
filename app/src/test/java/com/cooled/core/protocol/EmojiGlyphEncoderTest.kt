package com.cooled.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Test

class EmojiGlyphEncoderTest {
    @Test
    fun centerOnBlackCanvasReturnsSourceUnchangedWhenAlreadyTargetSize() {
        val source = PixelGrid(4, 4) { _, _ -> 0xFFFFFFFF.toInt() }
        assertSame(source, EmojiGlyphEncoder.centerOnBlackCanvas(source, 4))
    }

    @Test
    fun centerOnBlackCanvasPadsSmallerSourceWithOpaqueBlack() {
        // 2x2 all-white source centered into a 4x4 canvas -> offset (1,1).
        val source = PixelGrid(2, 2) { _, _ -> 0xFFFFFFFF.toInt() }
        val centered = EmojiGlyphEncoder.centerOnBlackCanvas(source, 4)
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                val expectedColor = if (x in 1..2 && y in 1..2) white else black
                org.junit.Assert.assertEquals("pixel($x,$y)", expectedColor, centered[x, y])
            }
        }
    }

    @Test
    fun monochromeColumnsMarksOnlyNonBlackPixelsAsSet() {
        // 2 columns x 3 rows (bytesPerColumn=1): col0 = [white, black, white], col1 = [black, black, black]
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        val grid = PixelGrid(2, 3) { x, y ->
            when {
                x == 0 && y == 1 -> black
                x == 0 -> white
                else -> black
            }
        }
        val bytes = EmojiGlyphEncoder.toMonochromeColumns(grid)
        // col0: row0=on(bit0,MSB->0x80), row1=off, row2=on(bit2->0x20) => 0x80|0x20=0xA0
        assertArrayEquals(byteArrayOf(0xA0.toByte(), 0x00), bytes)
    }

    @Test
    fun monochromeColumnsIgnoresAlphaChannelLikeTheApk() {
        // A fully-transparent but RGB-non-zero pixel still counts as "on" (matches
        // getImageData checking only Color.red/green/blue, never alpha).
        val transparentButColored = 0x00FF0000
        val grid = PixelGrid(1, 1) { _, _ -> transparentButColored }
        assertArrayEquals(byteArrayOf(0x80.toByte()), EmojiGlyphEncoder.toMonochromeColumns(grid))
    }

    @Test
    fun rgb444ColumnsMatchesKnownThresholdsColumnMajor() {
        // 2x1 grid (2 columns, 1 row): pure red then pure blue.
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val grid = PixelGrid(2, 1) { x, _ -> if (x == 0) red else blue }
        val bytes = EmojiGlyphEncoder.toRgb444Columns(grid)
        assertArrayEquals(
            OriginalLedAssetPayloadEncoder.rgb444TransferColorBytes(red) + OriginalLedAssetPayloadEncoder.rgb444TransferColorBytes(blue),
            bytes
        )
    }
}
