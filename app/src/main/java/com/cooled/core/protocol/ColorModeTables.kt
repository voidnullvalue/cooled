package com.cooled.core.protocol

/**
 * Port of ILedClockUtils.setColorMode(int) (ILedClockUtils.java:5549-5709)
 * and its 20 named literal table constants (ILedClockUtils.java:37-66).
 * Hand-traced from the decompiled source's goto-chain, not guessed: every
 * style index below was individually followed through to its actual
 * (table, count, transitionType, repeatCount) tuple, including the cases
 * where two style indices intentionally share the *same* literal table
 * (jadx renders these as a `goto` into a shared block, e.g. styles 9/11/12
 * all reach the same `r1 = "0F,00,..."` assignment with only their
 * transitionType/repeatCount differing).
 *
 * An earlier version of this port modeled setColorMode as a single mode-
 * index byte (`[0x13, 0x03, modeIndex]`), which bore no resemblance to the
 * real protocol: every mode sends a full literal RGB444 color table, not
 * an index for the device to look up itself.
 */
private object ColorModeLiterals {
    // Table shared by modes 1, 2, 3, 4, 6 (colorMode1/2/4/5/6 in the
    // original are byte-identical constants - the dispatch never
    // distinguishes which name it "means", so one Kotlin constant covers
    // all of them).
    const val WHEEL_90 =
        "0F,00,0F,10,0F,20,0F,30,0F,40,0F,50,0F,60,0F,70,0F,80,0F,90,0F,A0,0F,B0,0F,C0,0F,D0,0F,E0," +
            "0F,F0,0E,F0,0D,F0,0C,F0,0B,F0,0A,F0,09,F0,08,F0,07,F0,06,F0,05,F0,04,F0,03,F0,02,F0,01,F0," +
            "00,F0,00,F1,00,F2,00,F3,00,F4,00,F5,00,F6,00,F7,00,F8,00,F9,00,FA,00,FB,00,FC,00,FD,00,FE," +
            "00,FF,00,EF,00,DF,00,CF,00,BF,00,AF,00,9F,00,8F,00,7F,00,6F,00,5F,00,4F,00,3F,00,2F,00,1F," +
            "00,0F,01,0F,02,0F,03,0F,04,0F,05,0F,06,0F,07,0F,08,0F,09,0F,0A,0F,0B,0F,0C,0F,0D,0F,0E,0F," +
            "0F,0F,0F,0E,0F,0D,0F,0C,0F,0B,0F,0A,0F,09,0F,08,0F,07,0F,06,0F,05,0F,04,0F,03,0F,02,0F,01"

    // Shared by modes 7, 8.
    const val PULSE_12 = "0F,00,0F,00,0F,00,0F,00,00,F0,00,F0,00,F0,00,F0,00,0F,00,0F,00,0F,00,0F"

    // Shared by modes 9, 11, 12, 15, 16 (also colorMode10 for mode 10, a
    // near-identical but not-quite-the-same table - see COLOR_MODE_10).
    const val RAINBOW_18 = "0F,00,0F,00,0F,00,\n0F,F0,0F,F0,0F,F0,\n00,F0,00,F0,00,F0,\n00,FF,00,FF,00,FF,\n00,0F,00,0F,00,0F,\n0F,0F,0F,0F,0F,0F"

    // Mode 10's own distinct table (differs from RAINBOW_18 in its second row: F0,F0,F0 vs F0,F0,F0 duplicated - confirmed byte-for-byte against colorMode10's literal).
    const val COLOR_MODE_10 = "0F,00,0F,00,0F,00,\n0F,F0,0F,F0,0F,F0,\n0F,F0,0F,F0,0F,F0,\n00,FF,00,FF,00,FF,\n00,0F,00,0F,00,0F,\n0F,0F,0F,0F,0F,0F"

    // Shared by mode 13 and mode 31 (colorMode31 is a separately-declared
    // but byte-identical constant to colorMode13 in the original).
    const val SIX_COLOR = "0F,00,00,F0,00,0F,0F,F0,00,FF,0F,0F"

    const val TWO_COLOR = "0F,00,00,0F" // mode 14

    // Shared by modes 17, 18.
    const val WIPE_30 = "0F,00,0F,00,0F,00,00,00,00,00,\n0F,F0,0F,F0,0F,F0,00,00,00,00,\n00,F0,00,F0,00,F0,00,00,00,00,\n00,FF,00,FF,00,FF,00,00,00,00,\n00,0F,00,0F,00,0F,00,00,00,00,\n0F,0F,0F,0F,0F,0F,00,00,00,00"

    const val MODE_19 = "0F,00,0D,00,0B,00,09,00,07,00,05,00,03,00,01,00"
    const val MODE_20 = "01,00,03,00,05,00,07,00,09,00,0B,00,0D,00,0F,00"
    const val MODE_21 = "00,F0,00,D0,00,B0,00,90,00,70,00,50,00,30,00,10,"
    const val MODE_22 = "00,10,00,30,00,50,00,70,00,90,00,B0,00,D0,00,F0"
    const val MODE_23 = "00,0F,00,0D,00,0B,00,09,00,07,00,05,00,03,00,01"
    const val MODE_24 = "00,01,00,03,00,05,00,07,00,09,00,0B,00,0D,00,0F"
    const val MODE_25 = "0F,F0,0D,D0,0B,B0,09,90,07,70,05,50,03,30,01,10"
    const val MODE_26 = "01,10,03,30,05,50,07,70,09,90,0B,B0,0D,D0,0F,F0"
    const val MODE_27 = "0F,0F,0D,0D,0B,0B,09,09,07,07,05,05,03,03,01,01"
    const val MODE_28 = "01,01,03,03,05,05,07,07,09,09,0B,0B,0D,0D,0F,0F"
    const val MODE_29 =
        "0F,00,0D,00,0B,00,09,00,07,00,05,00,03,00,01,00,\n00,F0,00,D0,00,B0,00,90,00,70,00,50,00,30,00,10,\n" +
            "00,0F,00,0D,00,0B,00,09,00,07,00,05,00,03,00,01,\n0F,F0,0D,D0,0B,B0,09,90,07,70,05,50,03,30,01,10,\n" +
            "00,FF,00,DD,00,BB,00,99,00,77,00,55,00,33,00,11,\n0F,0F,0D,0D,0B,0B,09,09,07,07,05,05,03,03,01,01"
    const val MODE_30 =
        "01,00,03,00,05,00,07,00,09,00,0B,00,0D,00,0F,00,\n00,10,00,30,00,50,00,70,00,90,00,B0,00,D0,00,F0,\n" +
            "00,01,00,03,00,05,00,07,00,09,00,0B,00,0D,00,0F,\n01,10,03,30,05,50,07,70,09,90,0B,B0,0D,D0,0F,F0,\n" +
            "00,11,00,33,00,55,00,77,00,99,00,BB,00,DD,00,FF,\n01,01,03,03,05,05,07,07,09,09,0B,0B,0D,0D,0F,0F"
}

/**
 * @param repeatCount null means the original omits the repeat-count byte
 * entirely for this mode (the `r4 < 0` case in ILedClockUtils.setColorMode).
 */
data class ColorModeSelection(val hexTable: String, val count: Int, val transitionType: Int, val repeatCount: Int?)

object ColorModeTables {
    fun resolve(styleIndex: Int): ColorModeSelection = when (styleIndex) {
        1 -> ColorModeSelection(ColorModeLiterals.WHEEL_90, count = 90, transitionType = 2, repeatCount = 0)
        2 -> ColorModeSelection(ColorModeLiterals.WHEEL_90, count = 90, transitionType = 2, repeatCount = 1)
        // 3 and 4 are not typos or omissions in this port: the original
        // dispatch chain has no `r14 == 3`/`r14 == 4` check at all (verified
        // by grepping every `r14 !=`/`r14 ==` comparison in the decompiled
        // source) - both fall all the way through to the same empty-table
        // default as any out-of-range style index. A first draft of this
        // table guessed these were aliases of other modes; re-tracing the
        // goto chain line-by-line (rather than from memory) caught the
        // mistake before it shipped.
        3 -> ColorModeSelection("", count = 0, transitionType = 0, repeatCount = 0)
        4 -> ColorModeSelection("", count = 0, transitionType = 0, repeatCount = 0)
        5 -> ColorModeSelection(ColorModeLiterals.WHEEL_90, count = 90, transitionType = 2, repeatCount = 4)
        6 -> ColorModeSelection(ColorModeLiterals.WHEEL_90, count = 90, transitionType = 2, repeatCount = 5)
        7 -> ColorModeSelection(ColorModeLiterals.PULSE_12, count = 12, transitionType = 2, repeatCount = 0)
        8 -> ColorModeSelection(ColorModeLiterals.PULSE_12, count = 12, transitionType = 2, repeatCount = 1)
        9 -> ColorModeSelection(ColorModeLiterals.RAINBOW_18, count = 18, transitionType = 2, repeatCount = 0)
        10 -> ColorModeSelection(ColorModeLiterals.COLOR_MODE_10, count = 18, transitionType = 2, repeatCount = 1)
        11 -> ColorModeSelection(ColorModeLiterals.RAINBOW_18, count = 18, transitionType = 2, repeatCount = 2)
        12 -> ColorModeSelection(ColorModeLiterals.RAINBOW_18, count = 18, transitionType = 2, repeatCount = 3)
        13 -> ColorModeSelection(ColorModeLiterals.SIX_COLOR, count = 6, transitionType = 1, repeatCount = null)
        14 -> ColorModeSelection(ColorModeLiterals.TWO_COLOR, count = 2, transitionType = 1, repeatCount = null)
        15 -> ColorModeSelection(ColorModeLiterals.RAINBOW_18, count = 18, transitionType = 3, repeatCount = 4)
        16 -> ColorModeSelection(ColorModeLiterals.RAINBOW_18, count = 18, transitionType = 3, repeatCount = 5)
        17 -> ColorModeSelection(ColorModeLiterals.WIPE_30, count = 30, transitionType = 2, repeatCount = 0)
        18 -> ColorModeSelection(ColorModeLiterals.WIPE_30, count = 30, transitionType = 2, repeatCount = 1)
        19 -> ColorModeSelection(ColorModeLiterals.MODE_19, count = 8, transitionType = 2, repeatCount = 0)
        20 -> ColorModeSelection(ColorModeLiterals.MODE_20, count = 8, transitionType = 2, repeatCount = 1)
        21 -> ColorModeSelection(ColorModeLiterals.MODE_21, count = 8, transitionType = 2, repeatCount = 0)
        22 -> ColorModeSelection(ColorModeLiterals.MODE_22, count = 8, transitionType = 2, repeatCount = 1)
        23 -> ColorModeSelection(ColorModeLiterals.MODE_23, count = 8, transitionType = 2, repeatCount = 0)
        24 -> ColorModeSelection(ColorModeLiterals.MODE_24, count = 8, transitionType = 2, repeatCount = 1)
        25 -> ColorModeSelection(ColorModeLiterals.MODE_25, count = 8, transitionType = 2, repeatCount = 0)
        26 -> ColorModeSelection(ColorModeLiterals.MODE_26, count = 8, transitionType = 2, repeatCount = 1)
        27 -> ColorModeSelection(ColorModeLiterals.MODE_27, count = 8, transitionType = 2, repeatCount = 0)
        28 -> ColorModeSelection(ColorModeLiterals.MODE_28, count = 8, transitionType = 2, repeatCount = 1)
        29 -> ColorModeSelection(ColorModeLiterals.MODE_29, count = 48, transitionType = 2, repeatCount = 0)
        30 -> ColorModeSelection(ColorModeLiterals.MODE_30, count = 48, transitionType = 2, repeatCount = 1)
        31 -> ColorModeSelection(ColorModeLiterals.SIX_COLOR, count = 6, transitionType = 4, repeatCount = null)
        else -> ColorModeSelection("", count = 0, transitionType = 0, repeatCount = 0)
    }

    /**
     * Short human-readable label for a style index, for surfacing in the UI
     * instead of a bare number - the APK's own UI doesn't name these either
     * (they're just numbered slots in a picker), so these names describe
     * the actual color pattern rather than translating an official name.
     */
    fun describe(styleIndex: Int): String = when (styleIndex) {
        1, 2, 5, 6 -> "Rainbow wheel"
        3, 4 -> "Unused (no effect on real hardware)"
        7, 8 -> "Pulse (red, green, blue)"
        9, 10, 11, 12 -> "Rainbow cycle"
        13 -> "Six-color cycle"
        14 -> "Two-color cycle"
        15, 16 -> "Rainbow cycle (fast transition)"
        17, 18 -> "Wipe (rainbow, partial-width)"
        19, 20 -> "Fade in/out (red)"
        21, 22 -> "Fade in/out (green)"
        23, 24 -> "Fade in/out (blue)"
        25, 26 -> "Fade in/out (yellow)"
        27, 28 -> "Fade in/out (magenta)"
        29, 30 -> "Multi-color fade sequence"
        31 -> "Six-color cycle (fast transition)"
        else -> "Unrecognized - falls back to no effect on real hardware"
    }
}
