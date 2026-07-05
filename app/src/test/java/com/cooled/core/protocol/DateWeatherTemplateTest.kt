package com.cooled.core.protocol

import com.cooled.core.model.DeviceFamily
import org.junit.Assert.assertArrayEquals
import org.junit.Test

/**
 * Hand-traced golden vector for CoolledUXUtils.getDataWithDateCombineProgram,
 * hand-traced against the smali control flow
 * (reverse/apktool/smali_classes3/.../CoolledUXUtils.smali:9151-10754) for the
 * one physical device size this port covers: deviceRows=16, deviceColumns=32.
 * See CoolleduxDateWeatherBytecode's class doc for the full scope rationale.
 *
 * All content fields below use DeviceManager.CoolleduxDateProgramContent's own
 * Java-constructor defaults (layerType=1, monthFlag=0, showTime=5, numHeight=1,
 * numWidth=1, yearNumHeight=1, yearNumWidth=1, showSpaceYear=false,
 * showSpaceMonth=true, showSpaceDay=false) with every color/position/size
 * field at 0 (Java's implicit int default) - deliberately the simplest
 * concrete instance, not a synthetic edge case, so the expected bytes below
 * are a direct hand-trace of the smali rather than a stress test.
 */
class DateWeatherTemplateTest {
    @Test
    fun sixteenByThirtyTwoGoldenVectorWithDefaultFields() {
        val content = ProgramContent.DateWeather(deviceRows = 16, deviceColumns = 32)
        val encoded = ProgramComposer.encodeContentForTest(DeviceFamily.COOLLEDUX, content)

        // dateNumberData1632 (50 bytes) - month/day digit glyph table.
        val dateNumberTable = CommaSeparatedByteTable.parse(
            "254, 130, 130, 254, 0, 0, 0, 254, 0, 0, 158, 146, 146, 242, 0, 146, 146, 146, 254, 0, 240, 16, 16, 254, 0, " +
                "242, 146, 146, 158, 0, 254, 146, 146, 158, 0, 128, 128, 128, 254, 0, 254, 146, 146, 254, 0, 242, 146, 146, 254, 0"
        )
        // weekData1632 (119 bytes) - day-of-week glyph table.
        val weekTable = CommaSeparatedByteTable.parse(
            "248, 64, 32, 64, 248, 0, 112, 136, 136, 136, 112, 0, 248, 64, 32, 16, 248, 128, 128, 248, 128, 128, 0, 240, 8, 8, 8, 240, 0, " +
                "248, 168, 168, 168, 168, 248, 16, 32, 16, 248, 0, 248, 168, 168, 168, 168, 0, 248, 136, 136, 136, 112, 128, 128, 248, 128, 128, 0, " +
                "248, 32, 32, 32, 248, 0, 240, 8, 8, 8, 240, 248, 160, 160, 160, 160, 0, 0, 248, 160, 176, 168, 64, 0, 0, 248, 0, 0, 72, 168, 168, " +
                "168, 144, 0, 56, 80, 144, 80, 56, 0, 128, 128, 248, 128, 128, 72, 168, 168, 168, 144, 0, 240, 8, 8, 8, 240, 0, 248, 64, 32, 16, 248"
        )
        // dateSpaceMonthData1632 (2 bytes: 0x10, 0x10) - the shared "space" glyph,
        // only actually emitted here for showSpaceMonth (the one true-by-default flag).
        val spaceTable = CommaSeparatedByteTable.parse("16, 16")
        check(dateNumberTable.size == 50)
        check(weekTable.size == 119)
        check(spaceTable.size == 2)

        val zero2 = byteArrayOf(0, 0)
        val zeroColor = byteArrayOf(0, 0) // colorPlain(0) = red>>4, (green>>4<<4)|(blue>>4) = 0,0

        val inner = mutableListOf<Byte>()
        inner += 0x09 // content type: date/weather
        inner += ByteArray(7).toList() // 7 reserved bytes
        inner += 1 // layerType default
        inner += 0 // monthFlag default
        inner += byteArrayOf(0, 5).toList() // showTime default = 5
        inner += byteArrayOf(0, 1).toList() // numHeight default = 1
        inner += byteArrayOf(0, 1).toList() // numWidth default = 1
        inner += byteArrayOf(0, dateNumberTable.size.toByte()).toList() // date-number table count
        inner += dateNumberTable.toList()
        inner += byteArrayOf(0, 1).toList() // yearNumHeight default = 1
        inner += byteArrayOf(0, 1).toList() // yearNumWidth default = 1
        inner += zero2.toList() // year table: empty at 16x32 -> bare 0 count
        inner += zeroColor.toList() // yearColor=0
        repeat(4) { inner += zero2.toList() } // yearStartColumn/StartRow/Width/Height = 0
        inner += zeroColor.toList() // spaceYearColor=0
        repeat(4) { inner += zero2.toList() } // spaceYear pos/size = 0
        inner += zero2.toList() // showSpaceYear=false -> 0 count, no table
        inner += zeroColor.toList() // monthColor=0
        repeat(4) { inner += zero2.toList() } // month pos/size = 0
        inner += zero2.toList() // monthFlag==0 -> append 0
        inner += zeroColor.toList() // spaceMonthColor=0
        repeat(4) { inner += zero2.toList() } // spaceMonth pos/size = 0
        inner += byteArrayOf(0, spaceTable.size.toByte()).toList() // showSpaceMonth=true (default) -> space table
        inner += spaceTable.toList()
        inner += zeroColor.toList() // dayColor=0
        repeat(4) { inner += zero2.toList() } // day pos/size = 0
        inner += zeroColor.toList() // spaceDayColor=0
        repeat(4) { inner += zero2.toList() } // spaceDay pos/size = 0
        inner += zero2.toList() // showSpaceDay=false -> 0 count, no table
        inner += zeroColor.toList() // weekColor=0
        repeat(4) { inner += zero2.toList() } // week pos/size = 0
        inner += byteArrayOf(0, weekTable.size.toByte()).toList() // week table count
        inner += weekTable.toList()

        val innerBytes = inner.map { it }.toByteArray()
        val lengthPrefix = (innerBytes.size + 4).let {
            byteArrayOf(((it ushr 24) and 0xFF).toByte(), ((it ushr 16) and 0xFF).toByte(), ((it ushr 8) and 0xFF).toByte(), (it and 0xFF).toByte())
        }
        val block = lengthPrefix + innerBytes

        // wrapProgram: 8 zero bytes, contentCount=1, reserved byte, then the block.
        val expected = ByteArray(8) + byteArrayOf(1, 0) + block
        assertArrayEquals(expected, encoded)
    }

    @Test
    fun unsupportedDeviceSizeThrowsRatherThanGuessing() {
        val content = ProgramContent.DateWeather(deviceRows = 32, deviceColumns = 64)
        try {
            ProgramComposer.encodeContentForTest(DeviceFamily.COOLLEDUX, content)
            error("expected IllegalArgumentException for unsupported device size")
        } catch (e: IllegalArgumentException) {
            // expected: only 16x32 is byte-exact-verified so far.
        }
    }
}
