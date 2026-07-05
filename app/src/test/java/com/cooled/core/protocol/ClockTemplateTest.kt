package com.cooled.core.protocol

import com.cooled.core.model.DeviceFamily
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Hand-traced golden vectors for CoolledUXUtils.getDataWithClockCombineProgram
 * (reverse/jadx/sources/com/jtkj/led1248/light/utils/CoolledUXUtils.java:2594-3681),
 * scoped to the default (device-size-independent) digit-bitmap tables - see the
 * doc comment on ProgramContent.Clock for the exact scope and smali cross-checks.
 *
 * The default hour-digit table literal below is copied directly (independently
 * of app/src/main/.../ProgramContent.kt's copy) from CoolledUXUtils.java:2617,
 * so a transcription slip in one copy would surface as a test failure rather
 * than agreeing with itself. CommaSeparatedByteTable's comma-to-byte parsing is
 * separately unit-tested (CommaSeparatedByteTableTest) as a trivial, already-
 * verified primitive - reusing it here to expand the literal is not "computing
 * expected bytes by running the port and eyeballing it", it is the same kind of
 * composition BusinessHoursTemplateTest does with already-verified primitives.
 */
class ClockTemplateTest {
    // CoolledUXUtils.java:2617 - default hour-digit table (10 digits x 14 bytes = 140 bytes).
    private val defaultHourDigitTable = CommaSeparatedByteTable.parse(
        "127, 224, 255, 240, 192, 48, 192, 48, 255, 240, 127, 224, 0, 0, 32, 48, 96, 48, 255, 240, 255, 240, 0, 48, 0, 48, 0, 0, 96, 240, 225, 240, 195, 48, 198, 48, 252, 48, 120, 48, 0, 0, 96, 96, 224, 112, 198, 48, 198, 48, 255, 240, 121, 224, 0, 0, 31, 128, 63, 128, 97, 128, 255, 240, 255, 240, 1, 128, 0, 0, 252, 96, 252, 112, 204, 48, 204, 48, 207, 240, 199, 224, 0, 0, 127, 224, 255, 240, 204, 48, 204, 48, 207, 240, 199, 224, 0, 0, 192, 0, 192, 0, 199, 240, 207, 240, 248, 0, 240, 0, 0, 0, 123, 224, 255, 240, 198, 48, 198, 48, 255, 240, 123, 224, 0, 0, 124, 96, 254, 112, 198, 48, 198, 48, 255, 240, 127, 224, 0, 0"
    )

    // CoolledUXUtils.java:2680 - default hour/minute separator table.
    private val defaultSeparatorTable = CommaSeparatedByteTable.parse("51, 0, 51, 0")

    @Test
    fun defaultHourDigitTableIsTenDigitsOfFourteenBytes() {
        // Sanity check on the literal itself, independent of the encoder.
        assertArrayEquals(intArrayOf(140), intArrayOf(defaultHourDigitTable.size))
    }

    @Test
    fun goldenVectorAllZeroFieldsExceptHourColorAnd24HourSpaceShingMode() {
        // Device size 8x8 is not one of CoolledUXUtils's special-cased sizes
        // (16/20/24/32 rows only), so this exercises the default-table path.
        // is24HourShowMode=true + isSpaceShing=true -> mode byte 0x03
        // (CoolledUXUtils.java:2607-2612, smali :cond_1 branch "03").
        // hourColor = 0x804020 -> TextEmojiManagerCoolLEDUX.getColorDataWithColor:
        //   r=0x80/16=8, g=0x40/16=4, b=0x20/16=2 -> byte1=0x08, byte2=(4<<4)|2=0x42.
        val content = ProgramContent.Clock(
            styleIndex = 1,
            deviceRows = 8,
            deviceColumns = 8,
            layerType = 0,
            is24HourShowMode = true,
            isSpaceShing = true,
            showTime = 10,
            numHeight = 1,
            numWidth = 1,
            hourColor = 0x804020,
            showSpaceMinuteColor = false
        )
        val encoded = ProgramComposer.encodeContentForTest(DeviceFamily.COOLLEDUX, content)

        val body = mutableListOf<Byte>()
        body += 0x07.toByte() // content type CLOCK
        repeat(7) { body += 0x00.toByte() } // reserved
        body += 0x00.toByte() // layerType
        body += 0x03.toByte() // mode byte: is24=true, isSpaceShing=true
        body += byteArrayOf(0x00, 0x0A).toList() // showTime = 10
        body += byteArrayOf(0x00, 0x01).toList() // numHeight = 1
        body += byteArrayOf(0x00, 0x01).toList() // numWidth = 1
        // hour block: table, color, position
        body += byteArrayOf(0x00, 0x8C.toByte()).toList() // table byte count = 140
        body += defaultHourDigitTable.toList()
        body += byteArrayOf(0x08, 0x42).toList() // hourColor quantized
        body += byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0).toList() // hourStartColumn/Row/Width/Height = 0
        // spaceHour block: color, position (table comes after, shared w/ separator)
        body += byteArrayOf(0, 0).toList() // spaceHourColor = 0
        body += byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0).toList() // spaceHourStartColumn/Row/Width/Height = 0
        // separator table (shared for hour/minute gap)
        body += byteArrayOf(0x00, 0x04).toList() // table byte count = 4
        body += defaultSeparatorTable.toList()
        // minute block: color, position
        body += byteArrayOf(0, 0).toList() // minuteColor
        body += byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0).toList()
        // spaceMinute block: color, position
        body += byteArrayOf(0, 0).toList() // spaceMinuteColor
        body += byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0).toList()
        // showSpaceMinuteColor == false -> zero-length table marker
        body += byteArrayOf(0x00, 0x00).toList()
        // seconds block: color, position
        body += byteArrayOf(0, 0).toList() // secondsColor
        body += byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0).toList()
        // ampm block: color, position
        body += byteArrayOf(0, 0).toList() // ampmColor
        body += byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0).toList()
        // trailing am/pm glyph table: default "" -> zero-length marker
        body += byteArrayOf(0x00, 0x00).toList()

        val lengthPrefix = intToU32(body.size + 4)
        val block = lengthPrefix + body
        val expected = ByteArray(8) + byteArrayOf(1, 0) + block.toByteArray() // wrapProgram: 8 zero bytes, contentCount=1, reserved

        assertArrayEquals(expected, encoded)
    }

    @Test
    fun showSpaceMinuteColorTrueReemitsTheSameSeparatorTableASecondTime() {
        val base = ProgramContent.Clock(
            styleIndex = 1,
            deviceRows = 8,
            deviceColumns = 8,
            is24HourShowMode = false,
            isSpaceShing = false, // -> mode byte 0x00
            showSpaceMinuteColor = true
        )
        val encoded = ProgramComposer.encodeContentForTest(DeviceFamily.COOLLEDUX, base)

        // Only assert the two separator-table occurrences are present and that
        // the second one replaces the "zero-length" marker from the previous
        // test - full-body re-derivation would just duplicate the test above.
        val separatorTableBytes = byteArrayOf(0x00, 0x04) + defaultSeparatorTable
        val occurrences = countOccurrences(encoded, separatorTableBytes)
        org.junit.Assert.assertEquals(2, occurrences)
    }

    @Test
    fun specialCasedDeviceSizeThrowsInsteadOfGuessing() {
        // 16x32 is CoolledUXUtils.java:2618-2622's first explicit override
        // (styleIndex==1 swaps in a different hour-digit table) - out of this
        // port's scope, must throw rather than silently emit the default table.
        val content = ProgramContent.Clock(styleIndex = 1, deviceRows = 16, deviceColumns = 32)
        assertThrows(IllegalArgumentException::class.java) {
            ProgramComposer.encodeContentForTest(DeviceFamily.COOLLEDUX, content)
        }
    }

    @Test
    fun nonClockDeviceFamilyRejected() {
        val content = ProgramContent.Clock(styleIndex = 1, deviceRows = 8, deviceColumns = 8)
        assertThrows(IllegalArgumentException::class.java) {
            ProgramComposer.encodeContentForTest(DeviceFamily.COOLLEDU, content)
        }
    }

    private fun intToU32(value: Int): List<Byte> = listOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    private fun countOccurrences(haystack: ByteArray, needle: ByteArray): Int {
        var count = 0
        var i = 0
        outer@ while (i <= haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) {
                    i++
                    continue@outer
                }
            }
            count++
            i += needle.size
        }
        return count
    }
}
