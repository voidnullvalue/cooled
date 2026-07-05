package com.cooled.core.protocol

import com.cooled.core.model.DeviceFamily
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Hand-traced golden vectors for CoolledUXUtils.getDataWithTimeCountCombineProgram
 * wrapped in getDataWithProgram - see TimeCountDigitTables's doc comment for the
 * smali-verified row/column dispatch this exercises.
 *
 * The digit/colon table *bytes* below are taken by parsing the same literal
 * decimal strings that appear verbatim in the decompiled Java source (copied
 * directly from CoolledUXUtils.java, not re-derived from this port's own
 * production code) - per this repo's convention of extracting these dense
 * bitmap tables mechanically rather than eyeballing production output.
 * The framing bytes (type marker, reserved bytes, field order/sizes, color
 * encoding) are hand-computed byte-for-byte.
 */
class TimeCountTemplateTest {
    /** DIGIT_32x64 from CoolledUXUtils.java (32-row x 64-column device, hour digit table). */
    private val digit32x64 = CommaSeparatedByteTable.parse(
        "127, 252, 255, 254, 255, 254, 224, 14, 224, 14, 255, 254, 255, 254, 127, 252, 0, 0, 16, 14, 48, 14, 112, 14, 255, 254, 255, 254, 255, 254, 0, 14, 0, 14, 0, 0, 112, 62, 240, 126, 240, 254, 225, 206, 227, 142, 255, 14, 254, 14, 124, 14, 0, 0, 112, 28, 240, 30, 240, 30, 227, 142, 227, 142, 255, 254, 255, 254, 124, 124, 0, 0, 31, 192, 63, 192, 121, 192, 241, 192, 255, 254, 255, 254, 255, 254, 1, 192, 0, 0, 254, 28, 255, 30, 231, 30, 231, 14, 231, 14, 231, 254, 231, 254, 227, 252, 0, 0, 127, 252, 255, 254, 255, 254, 231, 14, 231, 14, 231, 254, 231, 254, 227, 252, 0, 0, 224, 0, 224, 0, 225, 254, 227, 254, 231, 254, 254, 0, 252, 0, 248, 0, 0, 0, 124, 124, 255, 254, 255, 254, 227, 142, 227, 142, 255, 254, 255, 254, 124, 124, 0, 0, 127, 28, 255, 158, 255, 158, 227, 142, 227, 142, 255, 254, 255, 254, 127, 252, 0, 0"
    )

    /** COLON_32x64 from CoolledUXUtils.java. */
    private val colon32x64 = CommaSeparatedByteTable.parse("12, 96, 12, 96")

    /** DEFAULT_DIGIT (the top-level default table CoolledUXUtils.java falls back to). */
    private val defaultDigit = CommaSeparatedByteTable.parse(
        "63, 255, 128, 127, 255, 192, 255, 255, 224, 224, 0, 224, 224, 0, 224, 224, 0, 224, 224, 0, 224, 224, 0, 224, 224, 0, 224, 255, 255, 224, 127, 255, 192, 63, 255, 128, 0, 0, 0, 0, 0, 224, 8, 0, 224, 24, 0, 224, 56, 0, 224, 120, 0, 224, 255, 255, 224, 255, 255, 224, 255, 255, 224, 0, 0, 224, 0, 0, 224, 0, 0, 224, 0, 0, 224, 0, 0, 0, 56, 3, 224, 120, 7, 224, 248, 15, 224, 224, 28, 224, 224, 56, 224, 224, 112, 224, 224, 224, 224, 225, 192, 224, 227, 128, 224, 255, 0, 224, 126, 0, 224, 60, 0, 224, 0, 0, 0, 56, 3, 128, 120, 3, 192, 248, 3, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 255, 255, 224, 127, 255, 192, 63, 31, 128, 0, 0, 0, 1, 248, 0, 3, 248, 0, 7, 248, 0, 14, 56, 0, 28, 56, 0, 56, 56, 0, 112, 56, 0, 255, 255, 224, 255, 255, 224, 255, 255, 224, 0, 56, 0, 0, 56, 0, 0, 0, 0, 255, 195, 128, 255, 195, 192, 255, 195, 224, 225, 192, 224, 225, 192, 224, 225, 192, 224, 225, 192, 224, 225, 192, 224, 225, 192, 224, 225, 255, 224, 224, 255, 192, 224, 127, 128, 0, 0, 0, 63, 255, 128, 127, 255, 192, 255, 255, 224, 225, 192, 224, 225, 192, 224, 225, 192, 224, 225, 192, 224, 225, 192, 224, 225, 192, 224, 225, 255, 224, 224, 255, 192, 224, 127, 128, 0, 0, 0, 224, 0, 0, 224, 0, 0, 224, 0, 0, 224, 0, 0, 224, 0, 0, 224, 127, 224, 224, 255, 224, 225, 255, 224, 227, 128, 0, 255, 0, 0, 254, 0, 0, 252, 0, 0, 0, 0, 0, 63, 31, 128, 127, 255, 192, 255, 255, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 255, 255, 224, 127, 255, 192, 63, 31, 128, 0, 0, 0, 63, 131, 128, 127, 195, 192, 255, 227, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 224, 255, 255, 224, 127, 255, 192, 63, 255, 128, 0, 0, 0"
    )

    /** DEFAULT_COLON from CoolledUXUtils.java. */
    private val defaultColon = CommaSeparatedByteTable.parse("48, 192, 48, 192")

    private fun u16(v: Int) = byteArrayOf(((v ushr 8) and 0xFF).toByte(), (v and 0xFF).toByte())
    private fun u32(v: Int) = byteArrayOf(
        ((v ushr 24) and 0xFF).toByte(), ((v ushr 16) and 0xFF).toByte(),
        ((v ushr 8) and 0xFF).toByte(), (v and 0xFF).toByte()
    )

    /** color/16 per channel, byte0=(0<<4|r/16), byte1=((g/16)<<4|b/16) - TextEmojiManagerCoolLEDUX.getColorDataWithColor. */
    private fun rgbPlain(color: Int): ByteArray {
        val r = ((color ushr 16) and 0xFF) / 16
        val g = ((color ushr 8) and 0xFF) / 16
        val b = (color and 0xFF) / 16
        return byteArrayOf(r.toByte(), ((g shl 4) or b).toByte())
    }

    private fun expectedBytes(digit: ByteArray, colon: ByteArray, c: ProgramContent.TimeCount): ByteArray {
        var inner = byteArrayOf(0x0a) + ByteArray(7) +
            byteArrayOf(c.layerType.toByte(), c.timeCountMode.toByte()) +
            u16(c.numHeight) + u16(c.numWidth) +
            u16(digit.size) + digit +
            rgbPlain(c.hourColor) + u16(c.hourStartColumn) + u16(c.hourStartRow) + u16(c.hourWidth) + u16(c.hourHeight) +
            rgbPlain(c.spaceHourColor) + u16(c.spaceHourStartColumn) + u16(c.spaceHourStartRow) + u16(c.spaceHourWidth) + u16(c.spaceHourHeight) +
            u16(colon.size) + colon +
            rgbPlain(c.minuteColor) + u16(c.minuteStartColumn) + u16(c.minuteStartRow) + u16(c.minuteWidth) + u16(c.minuteHeight) +
            rgbPlain(c.spaceMinuteColor) + u16(c.spaceMinuteStartColumn) + u16(c.spaceMinuteStartRow) + u16(c.spaceMinuteWidth) + u16(c.spaceMinuteHeight) +
            u16(colon.size) + colon +
            rgbPlain(c.secondsColor) + u16(c.secondsStartColumn) + u16(c.secondsStartRow) + u16(c.secondsWidth) + u16(c.secondsHeight)
        val block = u32(inner.size + 4) + inner
        return ByteArray(8) + byteArrayOf(1, 0) + block
    }

    @Test
    fun row32Column64GoldenVector() {
        val content = ProgramContent.TimeCount(
            layerType = 1,
            timeCountMode = 0,
            numHeight = 7,
            numWidth = 3,
            displayRows = 32,
            displayColumns = 64,
            hourColor = 0xFF0000,
            hourStartColumn = 1, hourStartRow = 2, hourWidth = 3, hourHeight = 4,
            spaceHourColor = 0x00FF00,
            spaceHourStartColumn = 5, spaceHourStartRow = 6, spaceHourWidth = 7, spaceHourHeight = 8,
            minuteColor = 0x0000FF,
            minuteStartColumn = 9, minuteStartRow = 10, minuteWidth = 11, minuteHeight = 12,
            spaceMinuteColor = 0xFFFFFF,
            spaceMinuteStartColumn = 13, spaceMinuteStartRow = 14, spaceMinuteWidth = 15, spaceMinuteHeight = 16,
            secondsColor = 0x123456,
            secondsStartColumn = 17, secondsStartRow = 18, secondsWidth = 19, secondsHeight = 20
        )
        val encoded = ProgramComposer.encodeContentForTest(DeviceFamily.COOLLEDUX, content)
        assertArrayEquals(expectedBytes(digit32x64, colon32x64, content), encoded)
    }

    @Test
    fun timeCountModeOneUsesTheSameTablesAsModeZero() {
        // Confirmed via smali (see TimeCountDigitTables doc): the mode==1 dispatch
        // chain is a byte-identical duplicate of mode==0's for every device size,
        // so the same digit32x64/colon32x64 tables are expected for both modes.
        val base = ProgramContent.TimeCount(displayRows = 32, displayColumns = 64)
        val content0 = base.copy(timeCountMode = 0)
        val content1 = base.copy(timeCountMode = 1)
        val mode0 = ProgramComposer.encodeContentForTest(DeviceFamily.COOLLEDUX, content0)
        val mode1 = ProgramComposer.encodeContentForTest(DeviceFamily.COOLLEDUX, content1)
        assertArrayEquals(expectedBytes(digit32x64, colon32x64, content0), mode0)
        assertArrayEquals(expectedBytes(digit32x64, colon32x64, content1), mode1)
    }

    @Test
    fun unrecognizedDeviceSizeFallsBackToDefaultTables() {
        val content = ProgramContent.TimeCount(displayRows = 99, displayColumns = 99)
        val encoded = ProgramComposer.encodeContentForTest(DeviceFamily.COOLLEDUX, content)
        assertArrayEquals(expectedBytes(defaultDigit, defaultColon, content), encoded)
    }

    @Test
    fun row16DeviceAlsoFallsBackToDefaultTables() {
        // Confirmed via smali (see TimeCountDigitTables doc, note 3): the
        // elaborate row-16-specific dispatch is computed but never consumed by
        // this function - a row-16 device gets the generic default tables
        // regardless of its column count, same as any unrecognized size.
        val content = ProgramContent.TimeCount(displayRows = 16, displayColumns = 64)
        val encoded = ProgramComposer.encodeContentForTest(DeviceFamily.COOLLEDUX, content)
        assertArrayEquals(expectedBytes(defaultDigit, defaultColon, content), encoded)
    }

    @Test
    fun unsupportedTimeCountModeThrowsLikeTheApkDoes() {
        // timeCountMode not in {0, 1}: both the digit and colon table strings
        // stay empty, and CommaSeparatedByteTable.parse("") throws - matching
        // the real APK's Integer.valueOf("".trim()) crash on this input.
        val content = ProgramContent.TimeCount(timeCountMode = 5, displayRows = 32, displayColumns = 64)
        assertThrows(NumberFormatException::class.java) {
            ProgramComposer.encodeContentForTest(DeviceFamily.COOLLEDUX, content)
        }
    }

    @Test
    fun row24UnderNinetySixColumnsAlsoThrowsLikeTheApkDoes() {
        // ROW==24 && COLUMN<96 (and not matching the 48/64 special cases)
        // resolves to empty digit/colon tables too (see TimeCountDigitTables
        // doc, note 4) - same crash-on-parse behavior as an unsupported mode.
        val content = ProgramContent.TimeCount(displayRows = 24, displayColumns = 50)
        assertThrows(NumberFormatException::class.java) {
            ProgramComposer.encodeContentForTest(DeviceFamily.COOLLEDUX, content)
        }
    }
}
