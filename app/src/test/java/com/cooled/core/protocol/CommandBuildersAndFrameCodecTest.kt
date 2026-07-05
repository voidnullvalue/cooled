package com.cooled.core.protocol

import com.cooled.core.model.DeviceFamily
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CommandBuildersAndFrameCodecTest {
    @Test
    fun frameCodecRoundTripsEscapedBytes() {
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val frame = FrameCodec.encode(payload)

        assertEquals(BleProtocolConstants.frameStart, frame.first())
        assertEquals(BleProtocolConstants.frameEnd, frame.last())
        assertArrayEquals(payload, FrameCodec.decode(frame))
    }

    @Test
    fun frameCodecRejectsMalformedFrames() {
        assertArrayEquals(byteArrayOf(), FrameCodec.decode(byteArrayOf()))
        assertArrayEquals(byteArrayOf(), FrameCodec.decode(byteArrayOf(0x00, 0x01)))
        assertArrayEquals(byteArrayOf(), FrameCodec.decode(byteArrayOf(BleProtocolConstants.frameStart, BleProtocolConstants.frameEscape, BleProtocolConstants.frameEnd)))
    }

    @Test
    fun simpleControlCommandsHaveExpectedPayloads() {
        assertPayload(byteArrayOf(0x1F), CommandBuilders.queryDeviceInfo())
        assertPayload(byteArrayOf(0xFD.toByte()), CommandBuilders.queryOtaVersion())
        assertPayload(byteArrayOf(0x05, 0x01), CommandBuilders.setPower(true))
        assertPayload(byteArrayOf(0x05, 0x00), CommandBuilders.setPower(false))
        assertPayload(byteArrayOf(0x04, 100), CommandBuilders.setBrightness(999))
        assertPayload(byteArrayOf(0x04, 0), CommandBuilders.setBrightness(-50))
        // 999 is not a real style index (valid range is 1-31); the real
        // dispatch falls through to an empty table for any unrecognized
        // index, not a clamped-to-255 mode byte (there's no such thing as
        // a raw "mode index" on the wire at all - see ColorModeTables).
        assertPayload(byteArrayOf(0x13, 0x03, 0, 0, 0), CommandBuilders.setColorMode(999))
    }

    @Test
    fun timeAndTimerSwitchCommandsHaveExpectedPayloads() {
        assertPayload(
            byteArrayOf(0x09, 26, 5, 29, 5, 13, 14, 15),
            CommandBuilders.syncTime(yearTwoDigit = 26, month = 5, day = 29, weekday = 5, hour = 13, minute = 14, second = 15)
        )

        assertPayload(
            byteArrayOf(0x0A, 1, 1, 8, 30, 127, 1, 0),
            CommandBuilders.setTimerSwitches(
                listOf(TimerSwitchCommand(enabled = true, hour = 8, minute = 30, weekdayMask = 127, turnDeviceOn = true))
            )
        )
    }

    @Test
    fun appFeatureCommandsClampPayloadFields() {
        assertPayload(byteArrayOf(0x0F, 0x02, 0, 59, 59), CommandBuilders.resetCountdown(hour = -1, minute = 99, second = 88))
        assertPayload(byteArrayOf(0x10, 0x03, 1), CommandBuilders.setStopwatchRunning(true))
        // hostScore=500 clamps into range (0x01F4, 2 bytes) rather than truncating to 1 byte;
        // guestScore=-10 clamps to 0 (2 bytes); hostSets/guestSets default to 0 (1 byte each).
        assertPayload(byteArrayOf(0x11, 0x02, 0x01, 0xF4.toByte(), 0, 0, 0, 0), CommandBuilders.resetScoreboard(hostScore = 500, guestScore = -10))
        assertPayload(byteArrayOf(0x1E, 0x06, 100), CommandBuilders.setVolume(900))
        assertPayload(byteArrayOf(0x19, 255.toByte()), CommandBuilders.queryTemperatureHumidity(300))
    }

    @Test
    fun alarmListPayloadUsesBigEndianDurationAndClampsFields() {
        val frame = CommandBuilders.setAlarmList(
            listOf(
                AlarmCommand(
                    enabled = true,
                    hour = 99,
                    minute = 99,
                    repeatMask = 999,
                    durationSeconds = 999999,
                    reminderDurationMinutes = 999
                )
            )
        )

        assertPayload(
            byteArrayOf(0x16, 0x01, 1, 1, 23, 59, 127, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            frame
        )
    }

    @Test
    fun dataChunkPayloadIncludesLengthIndexSizeAndXor() {
        val chunk = byteArrayOf(0x10, 0x20, 0x30)
        val payload = FrameCodec.decode(CommandBuilders.buildDataChunk(messageType = 0x03, totalCompressedLength = 0x01020304, chunkIndex = 0x0203, chunk = chunk))

        assertEquals(0x03, payload[0].toInt() and 0xFF)
        assertEquals(0x00, payload[1].toInt() and 0xFF)
        assertEquals(0x01, payload[2].toInt() and 0xFF)
        assertEquals(0x02, payload[3].toInt() and 0xFF)
        assertEquals(0x03, payload[4].toInt() and 0xFF)
        assertEquals(0x04, payload[5].toInt() and 0xFF)
        assertEquals(0x02, payload[6].toInt() and 0xFF)
        assertEquals(0x03, payload[7].toInt() and 0xFF)
        assertEquals(0x00, payload[8].toInt() and 0xFF)
        assertEquals(0x03, payload[9].toInt() and 0xFF)
        assertEquals(0x10, payload[10].toInt() and 0xFF)
        assertEquals(0x20, payload[11].toInt() and 0xFF)
        assertEquals(0x30, payload[12].toInt() and 0xFF)

        val expectedXor = payload.sliceArray(1 until payload.lastIndex).fold(0) { acc, byte -> acc xor (byte.toInt() and 0xFF) }
        assertEquals(expectedXor, payload.last().toInt() and 0xFF)
    }

    @Test
    fun splitChunksUsesApkProgramChunkSize() {
        val chunks = CommandBuilders.splitChunks(ByteArray(2050) { it.toByte() })

        assertEquals(3, chunks.size)
        assertEquals(1024, chunks[0].size)
        assertEquals(1024, chunks[1].size)
        assertEquals(2, chunks[2].size)
    }

    @Test
    fun coolleduxProgramStartHeaderUsesBasicApkFieldsAndUncompressedSource() {
        val compressed = byteArrayOf(0x10, 0x20, 0x30, 0x40)
        val uncompressed = byteArrayOf(0x01, 0x02, 0x03)
        val payload = FrameCodec.decode(
            CommandBuilders.buildProgramStartHeader(
                family = DeviceFamily.COOLLEDUX,
                request = ProgramStartRequest(
                    compressed = compressed,
                    index = 2,
                    count = 1,
                    showCount = 3,
                    programType = 14,
                    extraTypeByte = 1,
                    startSource = uncompressed
                )
            )
        )

        assertEquals(0x02, payload[0].toInt() and 0xFF)
        assertEquals(2, payload[9].toInt() and 0xFF)
        assertEquals(1, payload[10].toInt() and 0xFF)
        assertEquals(3, payload[11].toInt() and 0xFF)
        assertEquals(12, payload.size)
        assertEquals(uncompressed.size, readU32(payload, 5))
    }

    private fun readU32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun assertPayload(expected: ByteArray, frame: ByteArray) {
        assertArrayEquals(expected, FrameCodec.decode(frame))
    }

    @Test
    fun setColorModeSendsARealLiteralColorTableNotABareIndexByte() {
        // Mode 14 is the smallest real table: TWO_COLOR = 0x0F,0x00,0x00,0x0F
        // (4 bytes), count=2, transitionType=1, and - unlike most modes - no
        // repeat-count byte at all (ILedClockUtils.setColorMode's `r4 = -1`
        // case, which the original explicitly omits from the wire format).
        assertPayload(
            byteArrayOf(0x13, 0x03, 1, 2, 0x0F, 0x00, 0x00, 0x0F),
            CommandBuilders.setColorMode(14)
        )

        // Mode 13 and mode 31 share the exact same 6-color/12-byte table but
        // differ in transitionType (1 vs 4) - both also omit the
        // repeat-count byte.
        val sixColorBytes = byteArrayOf(0x0F, 0x00, 0x00, 0xF0.toByte(), 0x00, 0x0F, 0x0F, 0xF0.toByte(), 0x00, 0xFF.toByte(), 0x0F, 0x0F)
        assertPayload(byteArrayOf(0x13, 0x03, 1, 6) + sixColorBytes, CommandBuilders.setColorMode(13))
        assertPayload(byteArrayOf(0x13, 0x03, 4, 6) + sixColorBytes, CommandBuilders.setColorMode(31))
    }

    @Test
    fun setColorModeStyles3And4FallThroughToAnEmptyTableInTheRealApk() {
        // Confirmed by grepping every `r14 !=`/`r14 ==` comparison in
        // ILedClockUtils.setColorMode: there is no case for style index 3
        // or 4 at all, so both hit the same empty-table default as any
        // out-of-range index (see ColorModeTables's doc comment).
        assertPayload(byteArrayOf(0x13, 0x03, 0, 0, 0), CommandBuilders.setColorMode(3))
        assertPayload(byteArrayOf(0x13, 0x03, 0, 0, 0), CommandBuilders.setColorMode(4))
    }
}
