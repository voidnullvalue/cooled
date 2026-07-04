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

    @Test
    fun rotate90ClockwiseMatchesTheSameFormulaAsTheByteArrayRotation() {
        // Physical argument, independent of the implementation: rotating a
        // 2x2 grid 90 degrees clockwise moves top-left -> top-right,
        // top-right -> bottom-right, bottom-right -> bottom-left,
        // bottom-left -> top-left. Grid layout (row-major):
        //   row0: A C
        //   row1: B D
        // After a clockwise turn:
        //   row0: B A
        //   row1: D C
        // PixelGrid.get(x, y) uses x=column, y=row, so:
        //   source: (x=0,y=0)=A (x=1,y=0)=C (x=0,y=1)=B (x=1,y=1)=D
        //   expected rotated: (x=0,y=0)=B (x=1,y=0)=A (x=0,y=1)=D (x=1,y=1)=C
        val a = 0xFFAAAAAA.toInt()
        val b = 0xFFBBBBBB.toInt()
        val c = 0xFFCCCCCC.toInt()
        val d = 0xFFDDDDDD.toInt()
        val grid = PixelGrid(2, 2) { x, y ->
            when {
                x == 0 && y == 0 -> a
                x == 1 && y == 0 -> c
                x == 0 && y == 1 -> b
                else -> d
            }
        }
        val rotated = EmojiGlyphEncoder.rotate90Clockwise(grid)
        assertEquals(b, rotated[0, 0])
        assertEquals(a, rotated[1, 0])
        assertEquals(d, rotated[0, 1])
        assertEquals(c, rotated[1, 1])
    }

    @Test
    fun rotateImageFourTimesAtNinetyDegreesIsIdentity() {
        val grid = PixelGrid(3, 3) { x, y -> (x shl 8) or y }
        var current = grid
        repeat(4) { current = EmojiGlyphEncoder.rotate90Clockwise(current) }
        for (x in 0 until 3) for (y in 0 until 3) assertEquals(grid[x, y], current[x, y])
    }

    @Test
    fun rotateImageZeroAndThreeSixtyAreIdentity() {
        val grid = PixelGrid(2, 2) { x, y -> x * 10 + y }
        assertSame(grid, EmojiGlyphEncoder.rotateImage(0, grid))
        assertSame(grid, EmojiGlyphEncoder.rotateImage(360, grid))
    }

    @Test
    fun trimmedRgb444ColumnsDropsLeadingAndTrailingBlankColumnsButKeepsInteriorBlanks() {
        val black = 0xFF000000.toInt()
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        // 5 columns x 1 row: blank, red, blank(interior), blue, blank -> columns [1..3] survive
        // (red, interior-black KEPT because it's not touching an untrimmed boundary, blue).
        val grid = PixelGrid(5, 1) { x, _ ->
            when (x) {
                0 -> black
                1 -> red
                2 -> black
                3 -> blue
                else -> black
            }
        }
        val bytes = EmojiGlyphEncoder.toTrimmedRgb444Columns(grid)
        val expected = OriginalLedAssetPayloadEncoder.rgb444TransferColorBytes(red) +
            OriginalLedAssetPayloadEncoder.rgb444TransferColorBytes(black) +
            OriginalLedAssetPayloadEncoder.rgb444TransferColorBytes(blue)
        assertArrayEquals(expected, bytes)
    }

    @Test
    fun trimmedRgb444ColumnsOfAnAllBlankGridIsEmpty() {
        val grid = PixelGrid(3, 2) { _, _ -> 0xFF000000.toInt() }
        assertArrayEquals(ByteArray(0), EmojiGlyphEncoder.toTrimmedRgb444Columns(grid))
    }

    @Test
    fun trimmedRgb444ColumnsTreatsSemiTransparentBlackAsNonBlankUnlikeMonochromeColumns() {
        // 0x80000000: alpha=0x80 (not exactly 0 or 0xFF), RGB=(0,0,0). toMonochromeColumns
        // would call this "off" (RGB is zero); toTrimmedRgb444Columns must NOT treat it as
        // blank, since its blank check is an exact match against 0xFF000000 or 0 only -
        // a real, intentional APK inconsistency, see docs/APK_REVERSE_ENGINEERING_NOTES.md.
        val semiTransparentBlack = 0x80000000.toInt()
        val grid = PixelGrid(1, 1) { _, _ -> semiTransparentBlack }
        val bytes = EmojiGlyphEncoder.toTrimmedRgb444Columns(grid)
        assertArrayEquals(OriginalLedAssetPayloadEncoder.rgb444TransferColorBytes(semiTransparentBlack, alpha = 0x80), bytes)
    }

    private fun assertEquals(expected: Int, actual: Int) = org.junit.Assert.assertEquals(expected, actual)
}
