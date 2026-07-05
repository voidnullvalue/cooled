package com.cooled.core.protocol

import com.cooled.core.compression.LzssCodec
import com.cooled.core.crc.CoolLedCrc
import com.cooled.core.model.DeviceFamily
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolCoreTest {
    @Test
    fun frameEncodeDecode_roundTrip() {
        val payload = byteArrayOf(0x1F, 0x01, 0x02, 0x03)
        val frame = FrameCodec.encode(payload)
        val out = FrameCodec.decode(frame)
        assertArrayEquals(payload, out)
    }

    @Test
    fun crc_isDeterministic() {
        val a = CoolLedCrc.crc32Like(byteArrayOf(1, 2, 3, 4))
        val b = CoolLedCrc.crc32Like(byteArrayOf(1, 2, 3, 4))
        assertEquals(a, b)
    }

    @Test
    fun splitChunks_works() {
        val data = ByteArray(2050) { it.toByte() }
        val chunks = CommandBuilders.splitChunks(data, 1024)
        assertEquals(3, chunks.size)
        assertEquals(1024, chunks[0].size)
        assertEquals(2, chunks[2].size)
    }

    @Test
    fun chunkPacket_hasMessageTypeAndXor() {
        val frame = CommandBuilders.buildDataChunk(0x03, 2000, 1, byteArrayOf(1, 2, 3))
        val payload = FrameCodec.decode(frame)
        assertEquals(0x03, payload[0].toInt() and 0xFF)
        val xor = payload.sliceArray(1 until payload.lastIndex).fold(0) { acc, byte -> acc xor (byte.toInt() and 0xFF) }
        assertEquals(xor and 0xFF, payload.last().toInt() and 0xFF)
    }

    @Test
    fun parser_passwordAndDeviceInfoAndTransfer() {
        val p = ProtocolParsers.parseFrame(FrameCodec.encode(byteArrayOf(0x0D, 0x00)))
        assertTrue(p is ParsedPayload.PasswordCheckResult)

        val info = ProtocolParsers.parseFrame(
            FrameCodec.encode(
                byteArrayOf(
                    0x1F, 0x12, 0x01, 0x09, 0x10, 0x20, 0x04,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0x04, 0x00
                )
            )
        )
        assertTrue(info is ParsedPayload.DeviceInfo)
        val deviceInfo = info as ParsedPayload.DeviceInfo
        // Field layout from DeviceManager.java's getCoolLEDUXDeviceInfo handler,
        // not the model/firmware-version/matrix-size tuple this parser used to
        // fabricate (that layout actually belongs to the BLE scan record).
        assertEquals(true, deviceInfo.switchOnOff)
        assertEquals(1, deviceInfo.brightness)
        assertEquals(9, deviceInfo.rotate)
        assertEquals(true, deviceInfo.localMicSupported)
        assertEquals(true, deviceInfo.localMicOnOff)
        assertEquals(4, deviceInfo.localMicMode)
        assertEquals(1024, deviceInfo.packageSize)

        val transfer = ProtocolParsers.parseFrame(FrameCodec.encode(byteArrayOf(0x03, 0x00, 0x00, 0x02, 0x00)))
        assertTrue(transfer is ParsedPayload.TransferChunkResponse)
        assertEquals(2, (transfer as ParsedPayload.TransferChunkResponse).chunkIndex)
    }

    @Test
    fun parser_timerSwitchesUsesSixByteStridePerEntry() {
        // Two entries, 6 bytes each starting at offset 2: [enable, hour,
        // minute, weekdayMask, isSetDeviceOn, reserved]. An earlier version
        // of this parser read 1 byte/item, which for 2+ switches misread
        // later switches' fields as bogus extra "values".
        val frame = FrameCodec.encode(
            byteArrayOf(
                0x0B, 2,
                1, 8, 30, 127, 1, 0, // entry 0: on, 08:30, every day, turns device on
                0, 20, 0, 0, 0, 0 // entry 1: disabled, 20:00, no repeat, doesn't turn device on
            )
        )
        val parsed = ProtocolParsers.parseFrame(frame)
        assertTrue(parsed is ParsedPayload.TimerSwitches)
        val entries = (parsed as ParsedPayload.TimerSwitches).entries
        assertEquals(2, entries.size)
        assertEquals(ParsedPayload.TimerSwitchEntry(enabled = true, hour = 8, minute = 30, weekdayMask = 127, isSetDeviceOn = true), entries[0])
        assertEquals(ParsedPayload.TimerSwitchEntry(enabled = false, hour = 20, minute = 0, weekdayMask = 0, isSetDeviceOn = false), entries[1])
    }

    @Test
    fun parser_reminderDetailYearIsOneByteNotTwo() {
        // DeviceManager.java's reminder-detail handler: id, sound, year(1
        // byte!), month, day, hour, minute, repeatType, reserved(1 byte,
        // discarded), duration(2 bytes), contentLen(1 byte), content. An
        // earlier version of this parser read year as 2 bytes, shifting
        // every later field by one.
        val content = "Hi".encodeToByteArray()
        val frame = FrameCodec.encode(
            byteArrayOf(0x1A, 0x02, 5, 1, 26, 6, 15, 9, 30, 0x7F.toByte(), 0, 0x01, 0x2C, content.size.toByte()) + content
        )
        val parsed = ProtocolParsers.parseFrame(frame)
        assertTrue(parsed is ParsedPayload.ReminderDetail)
        val detail = parsed as ParsedPayload.ReminderDetail
        assertEquals(5, detail.id)
        assertEquals(1, detail.sound)
        assertEquals(26, detail.year)
        assertEquals(6, detail.month)
        assertEquals(15, detail.day)
        assertEquals(9, detail.hour)
        assertEquals(30, detail.minute)
        assertEquals(0x7F, detail.repeatType)
        assertEquals(0x012C, detail.durationSeconds)
        assertEquals("Hi", detail.content)
    }

    @Test
    fun parser_clockVectors() {
        val t = ProtocolParsers.parseFrame(FrameCodec.encode(byteArrayOf(0x19, 0x01, 0x01, 0x95.toByte(), 55)))
        assertTrue(t is ParsedPayload.TemperatureHumidity)

        val alarms = ProtocolParsers.parseFrame(
            FrameCodec.encode(
                byteArrayOf(
                    0x16, 0x02, 0x01,
                    0x01, 0x07, 0x30, 0x7E, 0x02, 0x58, 0x05
                )
            )
        )
        assertTrue(alarms is ParsedPayload.AlarmList)
        assertEquals(1, (alarms as ParsedPayload.AlarmList).alarms.size)

        val reminder = ProtocolParsers.parseFrame(FrameCodec.encode(byteArrayOf(0x1A, 0x01, 0x02, 0x03, 0x07)))
        assertTrue(reminder is ParsedPayload.ReminderList)
        assertEquals(listOf(3, 7), (reminder as ParsedPayload.ReminderList).ids)
    }

    @Test
    fun parser_unknownAndMalformedFrames() {
        assertTrue(ProtocolParsers.parseFrame(FrameCodec.encode(byteArrayOf(0x66, 0x01))) is ParsedPayload.Unknown)
        assertTrue(ProtocolParsers.parseFrame(FrameCodec.encode(byteArrayOf(0x03, 0x00, 0x00))) is ParsedPayload.ParseError)
    }

    @Test
    fun lzss_roundTrip() {
        val src = "HELLO HELLO HELLO COOLLED".encodeToByteArray()
        val compressed = LzssCodec.compress(src)
        val decompressed = LzssCodec.decompress(compressed)
        assertArrayEquals(src, decompressed)
    }

    @Test
    fun lzss_knownLiteralVector_lsbConfirmed() {
        val compressed = byteArrayOf(0x07, 'A'.code.toByte(), 'B'.code.toByte(), 'C'.code.toByte())
        val decompressed = LzssCodec.decompress(compressed)
        assertArrayEquals("ABC".encodeToByteArray(), decompressed)
    }


    @Test
    fun coolLedUxProgramHeaderUsesUncompressedLengthAndCrcFromApk() {
        val body = ByteArray(64) { (it * 3).toByte() }
        val compressed = com.cooled.core.compression.LzssCodec.compress(body)
        val request = ProgramStartRequest(compressed = compressed, index = 0, count = 1, showCount = 1, startSource = body)
        val payload = FrameCodec.decode(CommandBuilders.buildProgramStartHeader(DeviceFamily.COOLLEDUX, request))

        assertEquals(0x02, payload[0].toInt() and 0xFF)
        assertEquals(com.cooled.core.crc.CoolLedCrc.crc32Like(body), readBe32(payload, 1))
        assertEquals(body.size, readBe32(payload, 5))
        assertEquals(0, payload[9].toInt() and 0xFF)
        assertEquals(1, payload[10].toInt() and 0xFF)
        assertEquals(1, payload[11].toInt() and 0xFF)
    }

    @Test
    fun familySpecificProgramHeader_hasTypedTrailer() {
        val req = ProgramStartRequest(compressed = ByteArray(20) { it.toByte() }, index = 1, count = 2, showCount = 3, programType = 14, extraTypeByte = 9)
        val frame = CommandBuilders.buildProgramStartHeader(DeviceFamily.ILEDCLOCK, req)
        val payload = FrameCodec.decode(frame)
        assertEquals(0x02, payload[0].toInt() and 0xFF)
        assertEquals(0x05, payload[payload.lastIndex - 1].toInt() and 0xFF)
        assertEquals(0x09, payload[payload.lastIndex].toInt() and 0xFF)
    }

    @Test
    fun familySpecific_altOpcodeForUFamily() {
        val req = ProgramStartRequest(compressed = ByteArray(8) { 1 }, index = 2, useAlternateOpcode = true)
        val payload = FrameCodec.decode(CommandBuilders.buildProgramStartHeader(DeviceFamily.COOLLEDU, req))
        assertEquals(0x1A, payload[0].toInt() and 0xFF)
    }

    @Test
    fun commandBuilders_advancedClockAndModeOpcodes() {
        assertEquals(0x13, FrameCodec.decode(CommandBuilders.setColorMode(5))[0].toInt() and 0xFF)
        assertEquals(0x0F, FrameCodec.decode(CommandBuilders.resetCountdown())[0].toInt() and 0xFF)
        assertEquals(0x10, FrameCodec.decode(CommandBuilders.resetStopwatch())[0].toInt() and 0xFF)
    }

    @Test
    fun transferStateMachine_transitions() {
        val sm = TransferStateMachine()
        sm.startSession(chunks = 2)
        sm.onParsed(ParsedPayload.TransferStartResponse(0x02, 0x00))
        assertTrue(sm.state.value is TransferState.SendingChunk)
        sm.onParsed(ParsedPayload.TransferChunkResponse(0x03, 0, 0x00))
        assertTrue(sm.state.value is TransferState.SendingChunk)
        sm.onParsed(ParsedPayload.TransferChunkResponse(0x03, 1, 0x00))
        assertTrue(sm.state.value is TransferState.Completed)
    }

    @Test
    fun transferStateMachine_chunkNackFailsImmediately() {
        // A single chunk NACK is terminal in the real protocol - there's no
        // "exhaustion" to reach, since it never retries in the first place.
        // See TransferStateMachineTest.chunkNackIsTerminalNotARetryTrigger
        // for the fuller explanation of why (traced against the APK's ack
        // dispatcher).
        val sm = TransferStateMachine(maxChunkRetries = 2)
        sm.startSession(chunks = 1)
        sm.onParsed(ParsedPayload.TransferStartResponse(0x02, 0x00))
        sm.onParsed(ParsedPayload.TransferChunkResponse(0x03, 0, 0x01))
        assertTrue(sm.state.value is TransferState.Failed)
    }

    @Test
    fun programComposer_buildsCompressedProgramAndChunks() {
        val pack = ProgramComposer.compose(
            family = DeviceFamily.COOLLEDX,
            content = ProgramContent.Text("ABCD", speed = 1, effect = 2),
            index = 0,
            count = 1,
            showCount = 1
        )
        assertTrue(pack.metadata.usedCompression)
        assertTrue(pack.chunkFrames.isNotEmpty())
        val start = FrameCodec.decode(pack.startHeaderFrame)
        assertEquals(0x02, start[0].toInt() and 0xFF)
    }

    @Test
    fun programComposer_startHeaderCrcAndLengthAreOverUncompressedBodyForEveryFamily() {
        // Regression test: every family's getStartDataForProgram (CoolledMUtils,
        // CoolledUUtils, CoolledUDUtils, ILedClockUtils, CoolledUXUtils) computes
        // the start header's CRC/length over the *uncompressed* program body -
        // compression only ever applies to the chunk payloads that follow. An
        // earlier version of ProgramComposer.compose only did this for
        // COOLLEDUX and silently hashed/measured the compressed bytes for
        // every other family, which would fail real hardware's own CRC/length
        // check on every non-CoolLEDUX transfer.
        for (family in listOf(DeviceFamily.COOLLEDM, DeviceFamily.COOLLEDU, DeviceFamily.COOLLEDX, DeviceFamily.COOLLEDS, DeviceFamily.ILEDCLOCK)) {
            val pack = ProgramComposer.compose(
                family = family,
                content = ProgramContent.Text("ABCDEFGHIJ", speed = 1, effect = 2),
                index = 0,
                count = 1,
                showCount = 1
            )
            val uncompressedBody = LzssCodec.decompress(pack.compressed)
            val start = FrameCodec.decode(pack.startHeaderFrame)
            assertEquals("family=$family CRC", CoolLedCrc.crc32Like(uncompressedBody), readBe32(start, 1))
            assertEquals("family=$family length", uncompressedBody.size, readBe32(start, 5))
        }
    }

    @Test
    fun coolLedUxTextProgram_matchesRecoveredApkBlockLayout() {
        val glyphBytes = byteArrayOf(0x00, 0x01, 0x00, 0x08, 0x00, 0x08, 0x12, 0x34)
        val content = CoolLedUxTextContentProgramContent(
            text = "A",
            layerType = 2,
            startColumn = 3,
            startRow = 4,
            showWidth = 32,
            showHeight = 16,
            mode = 5,
            speed = 6,
            stayTime = 7,
            moveSpace = 8,
            glyphBytes = glyphBytes
        )

        val block = ProgramComposer.getDataWithTextContentProgramContent(content)

        assertEquals(block.size, readBe32(block, 0))
        assertEquals(0x01, block[4].toInt() and 0xFF)
        assertEquals(0x02, block[12].toInt() and 0xFF)
        assertEquals(3, readBe16(block, 13))
        assertEquals(4, readBe16(block, 15))
        assertEquals(32, readBe16(block, 17))
        assertEquals(16, readBe16(block, 19))
        assertEquals(5, block[21].toInt() and 0xFF)
        assertEquals(6, block[22].toInt() and 0xFF)
        assertEquals(7, block[23].toInt() and 0xFF)
        assertEquals(8, readBe16(block, 24))
        assertArrayEquals(glyphBytes, block.copyOfRange(26, block.size))
    }

    @Test
    fun coolLedUxTextProgram_wrapsContentLikeApkGetDataWithProgram() {
        val content = ProgramContent.CoolLedUxText(
            CoolLedUxTextContentProgramContent(text = "A", glyphBytes = byteArrayOf(0x00, 0x00, 0x00, 0x00))
        )
        val body = ProgramComposer.encodeContentForTest(DeviceFamily.COOLLEDUX, content)

        assertEquals(0x00, body[0].toInt() and 0xFF)
        assertEquals(0x01, body[8].toInt() and 0xFF)
        assertEquals(0x00, body[9].toInt() and 0xFF)
        assertEquals(0x01, body[14].toInt() and 0xFF)
        assertEquals(body.size - 10, readBe32(body, 10))
    }

    private fun readBe16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun readBe32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    @Test
    fun passwordAndClockBuilders_emitExpectedOpcodes() {
        assertEquals(0x16, FrameCodec.decode(CommandBuilders.setAlarmList(listOf(AlarmCommand(true, 6, 30, 0x7F, 120, 2))))[0].toInt() and 0xFF)
        assertEquals(0x14, FrameCodec.decode(CommandBuilders.setNightMode(true, 22, 0, 6, 0))[0].toInt() and 0xFF)
        assertEquals(0x15, FrameCodec.decode(CommandBuilders.queryTomato())[0].toInt() and 0xFF)
        assertEquals(0x19, FrameCodec.decode(CommandBuilders.queryTemperatureHumidity())[0].toInt() and 0xFF)
    }

    @Test
    fun setNightModeEncodesAllTenFieldsFromIledClockUtilsGetSetNightMode() {
        // ILedClockUtils.getSetNightMode(nightModeEnabled, startTimeHour,
        // startTimeMinute, endTimeHour, endTimeMinute, deviceStateEnabled,
        // brightness, voiceControlEnabled, wakeUpDuration, voiceSensitivity):
        // 10 single-byte fields. An earlier version of this builder only
        // exposed the first 5 and hardcoded the rest to 0.
        val payload = FrameCodec.decode(
            CommandBuilders.setNightMode(
                enabled = true, startHour = 22, startMinute = 15, endHour = 6, endMinute = 45,
                deviceStateEnabled = true, brightness = 80, voiceControlEnabled = true, wakeUpDuration = 30, voiceSensitivity = 5
            )
        )
        assertArrayEquals(byteArrayOf(0x14, 0x01, 1, 22, 15, 6, 45, 1, 80, 1, 30, 5), payload)
    }

    @Test
    fun queryNightModeMatchesIledClockUtilsGetNightMode() {
        assertArrayEquals(byteArrayOf(0x14, 0x02), FrameCodec.decode(CommandBuilders.queryNightMode()))
    }

    @Test
    fun parseNightModeOnlyExtractsAllTenFieldsForTheGetSubcommand() {
        // subcommand 2 (GET response) carries the full 10-field record from
        // offset 2; subcommand 1 (SET ack) is just a status byte and must
        // not be misread as a data record.
        val getResponse = ProtocolParsers.parseFrame(
            FrameCodec.encode(byteArrayOf(0x14, 0x02, 1, 22, 15, 6, 45, 1, 80, 1, 30, 5))
        )
        assertTrue(getResponse is ParsedPayload.NightModeState)
        val state = getResponse as ParsedPayload.NightModeState
        assertEquals(true, state.enabled)
        assertEquals(22, state.startHour)
        assertEquals(80, state.brightness)
        assertEquals(true, state.voiceControlEnabled)
        assertEquals(30, state.wakeUpDuration)
        assertEquals(5, state.voiceSensitivity)

        val setAck = ProtocolParsers.parseFrame(FrameCodec.encode(byteArrayOf(0x14, 0x01, 0)))
        assertTrue(setAck is ParsedPayload.NightModeState)
        assertEquals(null, (setAck as ParsedPayload.NightModeState).enabled)
    }

    @Test
    fun buildOtaStartHeaderComputesCrcAndLengthOverUncompressedFirmware() {
        // CoolledUXUtils/ILedClockUtils.getStartDataForOtaUpgrade: CRC and
        // length are always over the *uncompressed* firmware - compression
        // only ever applies to the chunk body sent separately. An earlier
        // version of this builder took a parameter literally named
        // "compressedFirmware" and computed everything from it directly,
        // the same mistake ProgramComposer.compose had for program uploads.
        val firmware = ByteArray(200) { (it * 7).toByte() }
        val withPreamble = FrameCodec.decode(CommandBuilders.buildOtaStartHeader(firmware, includePreamble64 = true))
        assertEquals(CoolLedCrc.crc32Like(firmware), readBe32(withPreamble, 1))
        assertEquals(firmware.size, readBe32(withPreamble, 5))
        assertEquals(64, withPreamble[9].toInt() and 0xFF)
        assertArrayEquals(firmware.copyOfRange(0, 64), withPreamble.copyOfRange(10, 74))

        // CoolledUUtils.getStartDataForOtaUpgrade has no 64-byte preamble at all.
        val withoutPreamble = FrameCodec.decode(CommandBuilders.buildOtaStartHeader(firmware, includePreamble64 = false))
        assertEquals(9, withoutPreamble.size)
    }
}
