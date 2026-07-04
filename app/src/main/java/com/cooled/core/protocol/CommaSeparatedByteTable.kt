package com.cooled.core.protocol

/**
 * Port of CoolledUXUtils.getSplitDataStringByDot(String)/getHexDataStringByDot(List<String>):
 * the clock/date/scoreboard template builders (getDataWithClockCombineProgram,
 * getDataWithDateCombineProgram, getDataWithTimeCountCombineProgram) embed
 * their per-style/per-device-size digit bitmaps as literal comma-separated
 * decimal byte strings directly in Java source (e.g.
 * `"127, 224, 255, 240, 192, 48, ..."`), one huge string per
 * (display element x device row/column x style index) combination, selected
 * by a long if/else dispatch chain before being parsed by this pair of
 * functions and appended to the output.
 *
 * This function itself is trivial; the real port work for the template
 * builders is (1) the *data* - potentially dozens of these literal tables
 * across hour/minute/colon/date/etc. display elements, each hundreds of
 * comma-separated values, which should be extracted from the decompiled
 * source programmatically rather than hand-transcribed (a single mistyped
 * byte would silently corrupt a digit's shape on the real LED matrix, and
 * that kind of error isn't something a smali cross-check or a unit test
 * would catch the way it would for algorithmic code) - and (2) the
 * per-element output framing this repeats for, documented in
 * docs/APK_REVERSE_ENGINEERING_NOTES.md.
 */
object CommaSeparatedByteTable {
    fun parse(commaSeparatedDecimalBytes: String): ByteArray =
        commaSeparatedDecimalBytes.split(",").map { it.trim().toInt().toByte() }.toByteArray()
}
