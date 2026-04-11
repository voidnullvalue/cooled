package com.cooled.core.protocol

sealed class ParsedPayload {
    data class Unknown(val data: ByteArray) : ParsedPayload()
    data class ParseError(val opcode: Int, val reason: String, val data: ByteArray) : ParsedPayload()

    data class BrightnessState(val value: Int) : ParsedPayload()
    data class PowerState(val on: Boolean) : ParsedPayload()
    data class MirrorState(val value: Int) : ParsedPayload()
    data class PasswordCheckResult(val success: Boolean, val code: Int) : ParsedPayload()
    data class PasswordSetResult(val success: Boolean, val code: Int) : ParsedPayload()

    data class DeviceInfo(
        val model: Int,
        val fwMajor: Int,
        val fwMinor: Int,
        val rows: Int,
        val columns: Int,
        val colorType: Int,
        val packageSize: Int?
    ) : ParsedPayload()

    data class OtaInfo(val supported: Boolean, val versionMajor: Int, val versionMinor: Int, val remoteFile: String) : ParsedPayload()

    data class TimerSwitches(val values: List<Int>) : ParsedPayload()
    data class TimeSyncAck(val status: Int) : ParsedPayload()
    data class TimerAck(val status: Int) : ParsedPayload()
    data class VolumeState(val value: Int) : ParsedPayload()

    data class CountdownState(val subcommand: Int, val minute: Int?, val second: Int?, val running: Boolean?) : ParsedPayload()
    data class StopwatchState(val subcommand: Int, val minute: Int?, val second: Int?, val running: Boolean?) : ParsedPayload()
    data class ScoreboardState(val left: Int?, val right: Int?, val mode: Int?, val running: Boolean?) : ParsedPayload()

    data class NightModeState(
        val enabled: Boolean?,
        val startHour: Int?,
        val startMinute: Int?,
        val endHour: Int?,
        val endMinute: Int?
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
            0x1E -> parseVolume(payload)
            0x1F -> parseDeviceInfo(payload)
            0xFD -> parseOtaInfo(payload)
            0x02, 0xFE -> parseTransferStart(payload)
            0x03, 0xFF -> parseTransferChunk(payload)
            else -> ParsedPayload.Unknown(payload)
        }
    }

    private fun parseTimerSwitches(payload: ByteArray): ParsedPayload {
        val count = payload.u8OrZero(1)
        val values = (0 until count).map { idx -> payload.u8OrZero(2 + idx) }
        return ParsedPayload.TimerSwitches(values)
    }

    private fun parseCountdown(payload: ByteArray): ParsedPayload {
        val sub = payload.u8OrZero(1)
        if (sub == 0x03 && payload.size >= 5) {
            return ParsedPayload.CountdownState(sub, payload.u8(2), payload.u8(3), payload.u8(4) != 0)
        }
        return ParsedPayload.CountdownState(sub, null, null, null)
    }

    private fun parseStopwatch(payload: ByteArray): ParsedPayload {
        val sub = payload.u8OrZero(1)
        if (sub == 0x03 && payload.size >= 5) {
            return ParsedPayload.StopwatchState(sub, payload.u8(2), payload.u8(3), payload.u8(4) != 0)
        }
        return ParsedPayload.StopwatchState(sub, null, null, null)
    }

    private fun parseScoreboard(payload: ByteArray): ParsedPayload {
        val sub = payload.u8OrZero(1)
        if (payload.size >= 6) {
            return ParsedPayload.ScoreboardState(
                left = payload.u8(2),
                right = payload.u8(3),
                mode = payload.u8(4),
                running = payload.u8(5) != 0
            )
        }
        return ParsedPayload.ScoreboardState(null, null, null, null)
    }

    private fun parseNightMode(payload: ByteArray): ParsedPayload {
        if (payload.size >= 7) {
            return ParsedPayload.NightModeState(
                enabled = payload.u8(2) != 0,
                startHour = payload.u8(3),
                startMinute = payload.u8(4),
                endHour = payload.u8(5),
                endMinute = payload.u8(6)
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
                if (payload.size < 15) return ParsedPayload.ParseError(0x1A, "Reminder detail too short", payload)
                val id = payload.u8(2)
                val sound = payload.u8(3)
                val year = payload.u8(4) shl 8 or payload.u8(5)
                val month = payload.u8(6)
                val day = payload.u8(7)
                val hour = payload.u8(8)
                val minute = payload.u8(9)
                val repeatType = payload.u8(10)
                val duration = payload.u16(12)
                val len = payload.u8(14)
                val content = payload.copyOfRange(15, minOf(15 + len, payload.size)).decodeToString()
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
        val model = payload.u8OrZero(1)
        val fwMajor = payload.u8OrZero(2)
        val fwMinor = payload.u8OrZero(3)
        val rows = payload.u8OrZero(4)
        val columns = payload.u8OrZero(5)
        val colorType = payload.u8OrZero(6)
        val packageSize = if (payload.size >= 21) payload.u16(19) else null
        return ParsedPayload.DeviceInfo(model, fwMajor, fwMinor, rows, columns, colorType, packageSize)
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
