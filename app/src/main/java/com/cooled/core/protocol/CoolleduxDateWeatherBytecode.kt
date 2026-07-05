package com.cooled.core.protocol

/**
 * Port of `CoolledUXUtils.getDataWithDateCombineProgram(DeviceManager.CoolleduxDateProgramContent)`
 * (`reverse/jadx/sources/com/jtkj/led1248/light/utils/CoolledUXUtils.java:3684-4054`, ~372 lines)
 * and its field layout, `DeviceManager.CoolleduxDateProgramContent`
 * (`reverse/jadx/sources/com/jtkj/led1248/light/device/DeviceManager.java:1432-1492`).
 *
 * Control flow was hand-traced against the smali
 * (`reverse/apktool/smali_classes3/com/jtkj/led1248/light/utils/CoolledUXUtils.smali:9151-10754`,
 * method `getDataWithDateCombineProgram`), not just the jadx pseudocode - this
 * method's jadx `-m simple` output turned out to be control-flow-accurate here
 * (unlike some other methods in this same file - see
 * docs/APK_REVERSE_ENGINEERING_NOTES.md), but was cross-checked anyway given
 * this file's history of decompiler bugs.
 *
 * ## Overall shape
 *
 * The function is a sequence of independent per-display-element blocks (date
 * number/digit table, year, space-year, month, space-month, day, space-day,
 * week, week table), exactly as already documented for the sibling
 * `getDataWithClockCombineProgram` in `docs/APK_REVERSE_ENGINEERING_NOTES.md`.
 * Each numeric/color field is a plain 1-, 2-, or 4-byte big-endian value; each
 * bitmap-table field is `[2-byte byte count][table bytes]`, with the table
 * itself selected by a long if/else chain keyed on
 * `DeviceManager.DEVICE_ROW`/`DEVICE_COLUMN` (the physical LED matrix size)
 * from dozens of literal comma-separated-decimal-byte string constants
 * (`dateNumberData1632`, `weekData1632`, `dateSpaceMonthData1632`, etc. -
 * `CommaSeparatedByteTable.parse` does the actual decimal-string-to-bytes
 * conversion, matching `getSplitDataStringByDot`/`getHexDataStringByDot`).
 *
 * ## Scope of this port: byte-exact for ONE physical device size only
 *
 * Hand-transcribing (or even scripting the extraction of) all ~15 device-size
 * variants across all ~4 table-bearing elements in this function is real,
 * substantial follow-up work - each device size needs its own dispatch
 * condition traced from smali, not just its literal string extracted. Per
 * this repo's "verified or explicit placeholder" rule, this port instead does
 * ONE physical device size completely and correctly - `deviceRows=16`,
 * `deviceColumns=32` - end-to-end hand-traced against smali (every dispatch
 * chain in the method was walked for this specific (row, column) pair, not
 * assumed from the jadx variable names), and throws for every other size
 * rather than guessing at its tables. The three literal tables below
 * (`dateNumberData1632`, `weekData1632`, `dateSpaceMonthData1632`) were
 * extracted programmatically (regex over the exact static-field declaration
 * in `CoolledUXUtils.java`), not hand-retyped, to eliminate transcription risk.
 *
 * Extending this to additional device sizes means: for each new (row, column)
 * pair, re-walk the same four dispatch chains in the smali (dateNumberTable,
 * yearTable, weekTable, and the shared "space" glyph table used for
 * space-year/space-month/space-day) to find which literal constant applies,
 * then extract that constant the same programmatic way.
 */
object CoolleduxDateWeatherBytecode {
    /**
     * `CoolledUXUtils.dateNumberData1632` - digit glyph table for month/day
     * numerals at the 16x32 device size (extracted verbatim via regex from
     * `CoolledUXUtils.java`'s static field declaration - 50 decimal byte
     * values). Selected for `deviceRows=16, deviceColumns=32` at the very
     * first branch of the function's leading dispatch chain
     * (smali `:cond_1`/`goto_3`, matches `numHeight`/`numWidth` digits).
     */
    private const val DATE_NUMBER_16X32 =
        "254, 130, 130, 254, 0, 0, 0, 254, 0, 0, 158, 146, 146, 242, 0, 146, 146, 146, 254, 0, 240, 16, 16, 254, 0, " +
            "242, 146, 146, 158, 0, 254, 146, 146, 158, 0, 128, 128, 128, 254, 0, 254, 146, 146, 254, 0, 242, 146, 146, 254, 0"

    /**
     * `CoolledUXUtils.weekData1632` - day-of-week glyph table at 16x32 (119
     * decimal byte values), selected at the function's final dispatch chain
     * (smali `:cond_33`, right before the closing length-prefixed return).
     */
    private const val WEEK_16X32 =
        "248, 64, 32, 64, 248, 0, 112, 136, 136, 136, 112, 0, 248, 64, 32, 16, 248, 128, 128, 248, 128, 128, 0, 240, 8, 8, 8, 240, 0, " +
            "248, 168, 168, 168, 168, 248, 16, 32, 16, 248, 0, 248, 168, 168, 168, 168, 0, 248, 136, 136, 136, 112, 128, 128, 248, 128, 128, 0, " +
            "248, 32, 32, 32, 248, 0, 240, 8, 8, 8, 240, 248, 160, 160, 160, 160, 0, 0, 248, 160, 176, 168, 64, 0, 0, 248, 0, 0, 72, 168, 168, " +
            "168, 144, 0, 56, 80, 144, 80, 56, 0, 128, 128, 248, 128, 128, 72, 168, 168, 168, 144, 0, 240, 8, 8, 8, 240, 0, 248, 64, 32, 16, 248"

    /**
     * `CoolledUXUtils.dateSpaceMonthData1632` - the shared "space" separator
     * glyph (2 decimal byte values: `16, 16`) reused for the space-year,
     * space-month, and space-day optional table fields alike at 16x32 (smali
     * register `r46`/local `v4`->`v5`->`v7` chain converges on this same
     * value for all three `showSpaceX` branches - confirmed by tracing each
     * one separately, not assumed from shared naming).
     */
    private const val SPACE_16X32 = "16, 16"

    fun encode(content: ProgramContent.DateWeather): ByteArray {
        require(content.deviceRows == 16 && content.deviceColumns == 32) {
            "CoolleduxDateWeatherBytecode.encode is only byte-exact-verified for " +
                "deviceRows=16/deviceColumns=32 so far (see docs/APK_REVERSE_ENGINEERING_NOTES.md); " +
                "got deviceRows=${content.deviceRows}, deviceColumns=${content.deviceColumns}"
        }

        val dateNumberTable = CommaSeparatedByteTable.parse(DATE_NUMBER_16X32)
        val weekTable = CommaSeparatedByteTable.parse(WEEK_16X32)
        // Shared "space" glyph (jadx's r46 / smali's converging v4->v5->v7 chain):
        // one shared table reused for whichever of space-year/space-month/space-day
        // is enabled, confirmed identical across all three branches at this device size.
        val spaceTable = CommaSeparatedByteTable.parse(SPACE_16X32)

        val r2 = mutableListOf<Byte>()
        r2 += 0x09.toByte() // content type: date/weather
        repeat(7) { r2 += 0x00.toByte() } // 7 reserved bytes
        r2 += one(content.layerType)
        r2 += one(content.monthFlag)
        r2 += two(content.showTime)
        r2 += two(content.numHeight)
        r2 += two(content.numWidth)

        // Date-number (month/day digit) table: [2-byte count][table bytes].
        r2 += two(dateNumberTable.size)
        r2 += dateNumberTable.toList()

        r2 += two(content.yearNumHeight)
        r2 += two(content.yearNumWidth)

        // Year digit table: the 16x32 dispatch resolves to "" (empty) - smali
        // :cond_10 sets it directly, and TextUtils.isEmpty(...) is true, so
        // only a bare zero count is appended (:cond_1f), no table bytes at all.
        r2 += two(0)

        r2 += colorPlain(content.yearColor)
        r2 += two(content.yearStartColumn)
        r2 += two(content.yearStartRow)
        r2 += two(content.yearWidth)
        r2 += two(content.yearHeight)

        r2 += colorPlain(content.spaceYearColor)
        r2 += two(content.spaceYearStartColumn)
        r2 += two(content.spaceYearStartRow)
        r2 += two(content.spaceYearWidth)
        r2 += two(content.spaceYearHeight)
        if (content.showSpaceYear) {
            r2 += two(spaceTable.size)
            r2 += spaceTable.toList()
        } else {
            r2 += two(0)
        }

        r2 += colorPlain(content.monthColor)
        r2 += two(content.monthStartColumn)
        r2 += two(content.monthStartRow)
        r2 += two(content.monthWidth)
        r2 += two(content.monthHeight)
        if (content.monthFlag == 0) {
            r2 += two(0)
        }
        // else: nothing appended here at all (not even a placeholder byte) -
        // matches CoolledUXUtils.smali :cond_30, which reads monthFlag into a
        // register but never appends it; a genuine dead-code branch in the
        // original, faithfully reproduced rather than "fixed".

        r2 += colorPlain(content.spaceMonthColor)
        r2 += two(content.spaceMonthStartColumn)
        r2 += two(content.spaceMonthStartRow)
        r2 += two(content.spaceMonthWidth)
        r2 += two(content.spaceMonthHeight)
        if (content.showSpaceMonth) {
            r2 += two(spaceTable.size)
            r2 += spaceTable.toList()
        } else {
            r2 += two(0)
        }

        r2 += colorPlain(content.dayColor)
        r2 += two(content.dayStartColumn)
        r2 += two(content.dayStartRow)
        r2 += two(content.dayWidth)
        r2 += two(content.dayHeight)

        r2 += colorPlain(content.spaceDayColor)
        r2 += two(content.spaceDayStartColumn)
        r2 += two(content.spaceDayStartRow)
        r2 += two(content.spaceDayWidth)
        r2 += two(content.spaceDayHeight)
        if (content.showSpaceDay) {
            r2 += two(spaceTable.size)
            r2 += spaceTable.toList()
        } else {
            r2 += two(0)
        }

        r2 += colorPlain(content.weekColor)
        r2 += two(content.weekStartColumn)
        r2 += two(content.weekStartRow)
        r2 += two(content.weekWidth)
        r2 += two(content.weekHeight)

        // Week (day-of-week) glyph table: [2-byte count][table bytes].
        r2 += two(weekTable.size)
        r2 += weekTable.toList()

        return (four(r2.size + 4) + r2).toByteArray()
    }

    /**
     * Port of `TextEmojiManagerCoolLEDUX.getColorDataWithColor(int)`
     * (`TextEmojiManagerCoolLEDUX.java:393`) - a plain 2-byte RGB444 encoding
     * (each channel simply shifted right 4 bits, no threshold quantization).
     * Deliberately distinct from `CoolleduxProgramBytecode`'s private
     * `colorDataWithColorWithRgb444Transfer` (port of the *other* APK
     * function, `getColorDataWithColorWithRGB444Transfer`, which applies
     * `rgb444Transfer`'s 238/47/14 thresholds) - this template builder calls
     * the plain variant for every color field, confirmed from the smali's
     * `getColorDataWithColor` invocations at every `...Color` field.
     */
    private fun colorPlain(color: Int): List<Byte> {
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        return listOf((r ushr 4).toByte(), (((g ushr 4) shl 4) or (b ushr 4)).toByte())
    }

    private fun one(value: Int): Byte = (value and 0xFF).toByte()

    private fun two(value: Int): List<Byte> = listOf(
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    private fun four(value: Int): List<Byte> = listOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )
}
