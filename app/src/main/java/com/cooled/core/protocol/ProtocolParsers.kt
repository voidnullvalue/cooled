package com.cooled.core.protocol

sealed class ParsedPayload {
    data class Unknown(val data: ByteArray) : ParsedPayload()
    data class ParseError(val opcode: Int, val reason: String, val data: ByteArray) : ParsedPayload()

    data class BrightnessState(val value: Int) : ParsedPayload()
    data class PowerState(val on: Boolean) : ParsedPayload()
    data class MirrorState(val value: Int) : ParsedPayload()
    data class PasswordCheckResult(val success: Boolean, val code: Int) : ParsedPayload()
    data class PasswordSetResult(val success: Boolean, val code: Int) : ParsedPayload()
    data class DriveState(val opcode: Int, val state: Int) : ParsedPayload()

    /**
     * Port of the CoolLEDUX 0x1F response field layout, read directly from
     * DeviceManager.java's inline handler (search "getCoolLEDUXDeviceInfo") -
     * NOT a model/firmware-version/matrix-size tuple as an earlier version of
     * this port guessed (that layout was actually the BLE scan-record
     * layout, copied here by mistake). The real fields, in order:
     * switchOnOff, brightness, rotate/mirror, localMicSupported,
     * localMicOnOff, localMicMode, showDeviceId, maxProgramNumber,
     * remoteEnable, and - only when the payload is exactly 21 bytes -
     * packageSize as a 2-byte value at offset 19.
     */
    data class DeviceInfo(
        val switchOnOff: Boolean,
        val brightness: Int,
        val rotate: Int,
        val localMicSupported: Boolean,
        val localMicOnOff: Boolean,
        val localMicMode: Int,
        val showDeviceId: Boolean,
        val maxProgramNumber: Int,
        val remoteEnable: Boolean,
        val packageSize: Int?
    ) : ParsedPayload()

    data class OtaInfo(val supported: Boolean, val versionMajor: Int, val versionMinor: Int, val remoteFile: String) : ParsedPayload()

    /** weekdayMask bit layout matches CommandBuilders.setTimerSwitches: bit0=Monday...bit6=Sunday (DeviceManager's getTimerSwitch response handler, getBit(repeatValue, 0..6)). */
    data class TimerSwitchEntry(val enabled: Boolean, val hour: Int, val minute: Int, val weekdayMask: Int, val isSetDeviceOn: Boolean)
    data class TimerSwitches(val entries: List<TimerSwitchEntry>) : ParsedPayload()
    data class TimeSyncAck(val status: Int) : ParsedPayload()
    data class TimerAck(val status: Int) : ParsedPayload()
    data class VolumeState(val value: Int) : ParsedPayload()

    /**
     * Port of DeviceManager.java's 0x0F response handler (search "coutdown>>>statusValue").
     * An earlier version of this parser modeled a much smaller (minute,
     * second, running) shape at fixed offsets that don't match any real
     * sub-response. The actual layout, keyed by subcommand:
     *  - 0x01 (query status) or 0x03 (start/stop ack): both carry the same
     *    9-byte record - isStartOrStop, then the *configured* setHour/
     *    setMinute/setSeconds and the *currently remaining*
     *    leftHour/leftMinute/leftSeconds (six separate time fields, not one).
     *  - 0x02 (reset ack): a bare success/failure byte (0 = success).
     *  - 0x04: a bare ack with no data at all - real APK behavior (no
     *    corresponding request is currently built by CommandBuilders).
     */
    data class CountdownState(
        val subcommand: Int,
        val isStartOrStop: Boolean? = null,
        val setHour: Int? = null,
        val setMinute: Int? = null,
        val setSeconds: Int? = null,
        val leftHour: Int? = null,
        val leftMinute: Int? = null,
        val leftSeconds: Int? = null,
        val acknowledged: Boolean? = null
    ) : ParsedPayload()

    /**
     * Port of DeviceManager.java's 0x10 response handler (search "stopwatch>>>statusValue").
     * Same correction as CountdownState: an earlier version of this parser
     * didn't match any real sub-response shape.
     *  - 0x01 (query status) or 0x03 (start/stop ack): isStartOrStop, hour,
     *    minute, seconds (6-byte record).
     *  - 0x02 (reset ack): a bare success/failure byte (0 = success).
     */
    data class StopwatchState(
        val subcommand: Int,
        val isStartOrStop: Boolean? = null,
        val hour: Int? = null,
        val minute: Int? = null,
        val seconds: Int? = null,
        val acknowledged: Boolean? = null
    ) : ParsedPayload()

    /**
     * Port of DeviceManager.java's 0x11 response handler (search "scoreBoard>>>hostScoreValue").
     * An earlier version of this parser modeled a 4-field (left, right,
     * mode, running) shape at fixed offsets that don't match the real
     * 14-byte query response at all.
     *  - 0x01 (query status): hostScore/visitScore (2-byte fields),
     *    hostTotalScore/visitTotalScore (1-byte set/game-win counters),
     *    deviceMinute/deviceSeconds (the scoreboard's own running clock),
     *    isStartOrStop, setMinute/setSeconds (the configured clock time),
     *    isCountDown (whether that clock counts down vs. up).
     *  - 0x02 (reset ack), 0x03 (set-time ack), 0x04 (start/stop ack): all
     *    a bare success/failure byte (0 = success).
     */
    data class ScoreboardState(
        val subcommand: Int,
        val hostScore: Int? = null,
        val visitScore: Int? = null,
        val hostTotalScore: Int? = null,
        val visitTotalScore: Int? = null,
        val deviceMinute: Int? = null,
        val deviceSeconds: Int? = null,
        val isStartOrStop: Boolean? = null,
        val setMinute: Int? = null,
        val setSeconds: Int? = null,
        val isCountDown: Boolean? = null,
        val acknowledged: Boolean? = null
    ) : ParsedPayload()

    data class NightModeState(
        val enabled: Boolean?,
        val startHour: Int?,
        val startMinute: Int?,
        val endHour: Int?,
        val endMinute: Int?,
        val deviceStateEnabled: Boolean? = null,
        val brightness: Int? = null,
        val voiceControlEnabled: Boolean? = null,
        val wakeUpDuration: Int? = null,
        val voiceSensitivity: Int? = null
    ) : ParsedPayload()

    data class TomatoItem(val value: Int)
    data class TomatoClockState(val items: List<TomatoItem>) : ParsedPayload()

    data class AlarmEntry(
        val enabled: Boolean,
        val hour: Int,
        val minute: Int,
        val repeatMask: Int,
        val durationSeconds: Int,
        val reminderDurationMinutes: Int
    )

    data class AlarmList(val alarms: List<AlarmEntry>) : ParsedPayload()
    data class ReminderList(val ids: List<Int>) : ParsedPayload()
    data class ReminderDetail(
        val id: Int,
        val sound: Int,
        val year: Int,
        val month: Int,
        val day: Int,
        val hour: Int,
        val minute: Int,
        val repeatType: Int,
        val durationSeconds: Int,
        val content: String
    ) : ParsedPayload()

    data class TemperatureHumidity(val temperatureCelsius: Double, val humidity: Int) : ParsedPayload()

    data class TransferStartResponse(val opcode: Int, val status: Int) : ParsedPayload()
    data class TransferChunkResponse(val opcode: Int, val chunkIndex: Int, val status: Int) : ParsedPayload()
}

object ProtocolParsers {
    fun parseFrame(frame: ByteArray): ParsedPayload {
        val payload = FrameCodec.decode(frame)
        if (payload.isEmpty()) return ParsedPayload.Unknown(payload)
        val opcode = payload.u8(0)
        return when (opcode) {
            0x04 -> ParsedPayload.BrightnessState(payload.u8OrZero(1))
            0x05 -> ParsedPayload.PowerState(payload.u8OrZero(1) != 0)
            0x0C -> ParsedPayload.MirrorState(payload.u8OrZero(1))
            0x0D -> ParsedPayload.PasswordCheckResult(success = payload.u8OrZero(1) == 0, code = payload.u8OrZero(1))
            0x0E -> ParsedPayload.PasswordSetResult(success = payload.u8OrZero(1) == 0, code = payload.u8OrZero(1))
            0x09 -> ParsedPayload.TimeSyncAck(payload.u8OrZero(1))
            0x0A -> ParsedPayload.TimerAck(payload.u8OrZero(1))
            0x0B -> parseTimerSwitches(payload)
            0x0F -> parseCountdown(payload)
            0x10 -> parseStopwatch(payload)
            0x11 -> parseScoreboard(payload)
            0x14 -> parseNightMode(payload)
            0x15 -> parseTomato(payload)
            0x16 -> parseAlarms(payload)
            0x19 -> parseTemperatureHumidity(payload)
            0x1A -> parseReminder(payload)
            0x1C -> ParsedPayload.DriveState(opcode = 0x1C, state = payload.u8OrZero(1))
            0x1E -> if (payload.u8OrZero(1) == 0x03) parseVolume(payload) else ParsedPayload.DriveState(opcode = 0x1E, state = payload.u8OrZero(1))
            0x1F -> parseDeviceInfo(payload)
            0xFD -> parseOtaInfo(payload)
            0x02, 0xFE -> parseTransferStart(payload)
            0x03, 0xFF -> parseTransferChunk(payload)
            else -> ParsedPayload.Unknown(payload)
        }
    }

    // DeviceManager.java's getTimerSwitch response handler (search "getTimerSwitch  number"):
    // count at offset 1, then one 6-byte record per item starting at offset 2:
    // [enable][hour][minute][weekdayMask][isSetDeviceOn][reserved, discarded].
    // An earlier version of this parser read 1 byte/item, which for 2+
    // switches misread the next switch's own fields as if they were
    // independent flat "values".
    private fun parseTimerSwitches(payload: ByteArray): ParsedPayload {
        val count = payload.u8OrZero(1)
        val entries = (0 until count).mapNotNull { idx ->
            val offset = 2 + idx * 6
            if (payload.size < offset + 6) return@mapNotNull null
            ParsedPayload.TimerSwitchEntry(
                enabled = payload.u8(offset) != 0,
                hour = payload.u8(offset + 1),
                minute = payload.u8(offset + 2),
                weekdayMask = payload.u8(offset + 3),
                isSetDeviceOn = payload.u8(offset + 4) != 0
            )
        }
        return ParsedPayload.TimerSwitches(entries)
    }

    private fun parseCountdown(payload: ByteArray): ParsedPayload {
        val sub = payload.u8OrZero(1)
        return when {
            (sub == 0x01 || sub == 0x03) && payload.size >= 9 -> ParsedPayload.CountdownState(
                subcommand = sub,
                isStartOrStop = payload.u8(2) == 1,
                setHour = payload.u8(3),
                setMinute = payload.u8(4),
                setSeconds = payload.u8(5),
                leftHour = payload.u8(6),
                leftMinute = payload.u8(7),
                leftSeconds = payload.u8(8)
            )
            sub == 0x02 && payload.size >= 3 -> ParsedPayload.CountdownState(subcommand = sub, acknowledged = payload.u8(2) == 0)
            else -> ParsedPayload.CountdownState(subcommand = sub)
        }
    }

    private fun parseStopwatch(payload: ByteArray): ParsedPayload {
        val sub = payload.u8OrZero(1)
        return when {
            (sub == 0x01 || sub == 0x03) && payload.size >= 6 -> ParsedPayload.StopwatchState(
                subcommand = sub,
                isStartOrStop = payload.u8(2) == 1,
                hour = payload.u8(3),
                minute = payload.u8(4),
                seconds = payload.u8(5)
            )
            sub == 0x02 && payload.size >= 3 -> ParsedPayload.StopwatchState(subcommand = sub, acknowledged = payload.u8(2) == 0)
            else -> ParsedPayload.StopwatchState(subcommand = sub)
        }
    }

    private fun parseScoreboard(payload: ByteArray): ParsedPayload {
        val sub = payload.u8OrZero(1)
        return when {
            sub == 0x01 && payload.size >= 14 -> ParsedPayload.ScoreboardState(
                subcommand = sub,
                hostScore = payload.u16(2),
                visitScore = payload.u16(4),
                hostTotalScore = payload.u8(6),
                visitTotalScore = payload.u8(7),
                deviceMinute = payload.u8(8),
                deviceSeconds = payload.u8(9),
                isStartOrStop = payload.u8(10) == 1,
                setMinute = payload.u8(11),
                setSeconds = payload.u8(12),
                isCountDown = payload.u8(13) == 1
            )
            (sub == 0x02 || sub == 0x03 || sub == 0x04) && payload.size >= 3 -> ParsedPayload.ScoreboardState(subcommand = sub, acknowledged = payload.u8(2) == 0)
            else -> ParsedPayload.ScoreboardState(subcommand = sub)
        }
    }

    // DeviceManager.java's 0x14 handler dispatches on a subcommand at
    // offset 1: 1 = SET result ack (just a status byte, no data record),
    // 2 = GET response carrying all 10 fields from offset 2 - confirmed via
    // the GetNightModeEventResponse construction (search "GetNightModeEventResponse").
    // An earlier version of this parser didn't check the subcommand at all
    // and only ever extracted the first 5 fields, discarding
    // deviceStateEnabled/brightness/voiceControlEnabled/wakeUpDuration/
    // voiceSensitivity even when a real GET response carried them.
    private fun parseNightMode(payload: ByteArray): ParsedPayload {
        if (payload.u8OrZero(1) == 2 && payload.size >= 12) {
            return ParsedPayload.NightModeState(
                enabled = payload.u8(2) != 0,
                startHour = payload.u8(3),
                startMinute = payload.u8(4),
                endHour = payload.u8(5),
                endMinute = payload.u8(6),
                deviceStateEnabled = payload.u8(7) != 0,
                brightness = payload.u8(8),
                voiceControlEnabled = payload.u8(9) != 0,
                wakeUpDuration = payload.u8(10),
                voiceSensitivity = payload.u8(11)
            )
        }
        return ParsedPayload.NightModeState(null, null, null, null, null)
    }

    private fun parseTomato(payload: ByteArray): ParsedPayload {
        val sub = payload.u8OrZero(1)
        if (sub != 0x02) return ParsedPayload.TomatoClockState(emptyList())
        val count = payload.u8OrZero(2)
        val items = (0 until count).map { ParsedPayload.TomatoItem(payload.u8OrZero(3 + it)) }
        return ParsedPayload.TomatoClockState(items)
    }

    private fun parseAlarms(payload: ByteArray): ParsedPayload {
        val sub = payload.u8OrZero(1)
        if (sub != 0x02) return ParsedPayload.AlarmList(emptyList())
        val count = payload.u8OrZero(2)
        val alarms = mutableListOf<ParsedPayload.AlarmEntry>()
        var offset = 3
        repeat(count) {
            if (payload.size < offset + 7) return@repeat
            alarms += ParsedPayload.AlarmEntry(
                enabled = payload.u8(offset) != 0,
                hour = payload.u8(offset + 1),
                minute = payload.u8(offset + 2),
                repeatMask = payload.u8(offset + 3),
                durationSeconds = payload.u16(offset + 4),
                reminderDurationMinutes = payload.u8(offset + 6)
            )
            offset += 7
        }
        return ParsedPayload.AlarmList(alarms)
    }

    private fun parseReminder(payload: ByteArray): ParsedPayload {
        return when (payload.u8OrZero(1)) {
            0x01 -> {
                val count = payload.u8OrZero(2)
                val ids = (0 until count).map { payload.u8OrZero(3 + it) }
                ParsedPayload.ReminderList(ids)
            }

            0x02 -> {
                // DeviceManager.java's reminder-detail handler (search
                // "GetReminderResponseEvent  number"): year is a single byte
                // (token index 4), not 2 - an earlier version of this parser
                // read it as u16, shifting every field after it by one byte
                // (month/day/hour/minute/repeatType each reading what was
                // really the *next* field, duration spilling into the
                // length byte, and content starting one byte late).
                if (payload.size < 14) return ParsedPayload.ParseError(0x1A, "Reminder detail too short", payload)
                val id = payload.u8(2)
                val sound = payload.u8(3)
                val year = payload.u8(4)
                val month = payload.u8(5)
                val day = payload.u8(6)
                val hour = payload.u8(7)
                val minute = payload.u8(8)
                val repeatType = payload.u8(9)
                // payload.u8(10) is a reserved/discarded byte in the original too.
                val duration = payload.u16(11)
                val len = payload.u8(13)
                val content = payload.copyOfRange(14, minOf(14 + len, payload.size)).decodeToString()
                ParsedPayload.ReminderDetail(id, sound, year, month, day, hour, minute, repeatType, duration, content)
            }

            else -> ParsedPayload.Unknown(payload)
        }
    }

    private fun parseTemperatureHumidity(payload: ByteArray): ParsedPayload {
        val type = payload.u8OrZero(1)
        if (type != 0x01 || payload.size < 5) return ParsedPayload.Unknown(payload)
        val raw = payload.u16(2)
        val sign = raw and 0x8000 != 0
        val integerPart = (raw and 0x7FF0) shr 4
        val fraction = (raw and 0x000F) / 10.0
        val temperature = (integerPart + fraction) * if (sign) -1 else 1
        return ParsedPayload.TemperatureHumidity(temperature, payload.u8(4))
    }

    private fun parseVolume(payload: ByteArray): ParsedPayload {
        val sub = payload.u8OrZero(1)
        return if (sub == 0x03 && payload.size >= 3) ParsedPayload.VolumeState(payload.u8(2)) else ParsedPayload.Unknown(payload)
    }

    private fun parseDeviceInfo(payload: ByteArray): ParsedPayload {
        val packageSize = if (payload.size == 21) payload.u16(19) else null
        return ParsedPayload.DeviceInfo(
            switchOnOff = payload.u8OrZero(1) != 0,
            brightness = payload.u8OrZero(2),
            rotate = payload.u8OrZero(3),
            localMicSupported = payload.u8OrZero(4) != 0,
            localMicOnOff = payload.u8OrZero(5) != 0,
            localMicMode = payload.u8OrZero(6),
            showDeviceId = payload.u8OrZero(7) != 0,
            maxProgramNumber = payload.u8OrZero(8),
            remoteEnable = payload.u8OrZero(9) != 0,
            packageSize = packageSize
        )
    }

    private fun parseOtaInfo(payload: ByteArray): ParsedPayload {
        val supported = payload.u8OrZero(1) != 0
        val major = payload.u8OrZero(2)
        val minor = payload.u8OrZero(3)
        val len = payload.u8OrZero(4)
        val name = payload.copyOfRange(5, minOf(5 + len, payload.size)).decodeToString()
        return ParsedPayload.OtaInfo(supported, major, minor, name)
    }

    private fun parseTransferStart(payload: ByteArray): ParsedPayload =
        ParsedPayload.TransferStartResponse(payload.u8(0), payload.u8OrZero(1))

    private fun parseTransferChunk(payload: ByteArray): ParsedPayload {
        if (payload.size < 5) return ParsedPayload.ParseError(payload.u8(0), "Chunk ack too short", payload)
        return ParsedPayload.TransferChunkResponse(payload.u8(0), payload.u16(2), payload.u8(4))
    }

    private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF
    private fun ByteArray.u8OrZero(index: Int): Int = if (index < size) u8(index) else 0
    private fun ByteArray.u16(index: Int): Int = (u8(index) shl 8) or u8(index + 1)
}