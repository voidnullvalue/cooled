package com.cooled.core.protocol

import com.cooled.core.model.DeviceFamily
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        assertPayload(byteArrayOf(0x13, 0x03, 255.toByte()), CommandBuilders.setColorMode(999))
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
        assertPayload(byteArrayOf(0x11, 0x02, 255.toByte(), 0), CommandBuilders.resetScoreboard(left = 500, right = -10))
        assertPayload(byteArrayOf(0x1E, 0x03, 100), CommandBuilders.setVolume(900))
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
    fun coolleduxProgramStartHeaderIncludesAdvancedTrailer() {
        val compressed = byteArrayOf(0x10, 0x20, 0x30, 0x40)
        val payload = FrameCodec.decode(
            CommandBuilders.buildProgramStartHeader(
                family = DeviceFamily.COOLLEDUX,
                request = ProgramStartRequest(
                    compressed = compressed,
                    index = 2,
                    count = 1,
                    showCount = 1,
                    programType = 14,
                    extraTypeByte = 1
                )
            )
        )

        assertEquals(0x02, payload[0].toInt() and 0xFF)
        assertEquals(2, payload[9].toInt() and 0xFF)
        assertEquals(1, payload[10].toInt() and 0xFF)
        assertEquals(1, payload[11].toInt() and 0xFF)
        assertEquals(0x00, payload[12].toInt() and 0xFF)
        assertTrue(payload.takeLast(2).map { it.toInt() and 0xFF } == listOf(0x05, 0x01))
    }

    private fun assertPayload(expected: ByteArray, frame: ByteArray) {
        assertArrayEquals(expected, FrameCodec.decode(frame))
    }
}
