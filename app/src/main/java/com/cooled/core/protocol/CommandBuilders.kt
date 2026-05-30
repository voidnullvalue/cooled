package com.cooled.core.protocol

import com.cooled.core.crc.CoolLedCrc
import com.cooled.core.model.DeviceFamily
import java.util.Calendar
import kotlin.random.Random

object CommandBuilders {
    fun queryDeviceInfo(): ByteArray = FrameCodec.encode(byteArrayOf(0x1F.toByte()))
    fun queryOtaVersion(): ByteArray = FrameCodec.encode(byteArrayOf(0xFD.toByte()))
    fun queryDriveState(): ByteArray = FrameCodec.encode(byteArrayOf(0x1C.toByte(), 0x02))
    fun setDriveState(state: Int): ByteArray = FrameCodec.encode(byteArrayOf(0x1C.toByte(), 0x01, state.coerceIn(0, 255).toByte()))

    fun setBrightness(value: Int): ByteArray = FrameCodec.encode(byteArrayOf(0x04.toByte(), value.coerceIn(0, 100).toByte()))
    fun setPower(on: Boolean): ByteArray = FrameCodec.encode(byteArrayOf(0x05.toByte(), (if (on) 0x01 else 0x00).toByte()))
    fun setRhythm(type: Int): ByteArray = FrameCodec.encode(byteArrayOf(0x06.toByte(), type.coerceIn(0, 255).toByte()))
    fun setMirror(value: Int): ByteArray = FrameCodec.encode(byteArrayOf(0x0C.toByte(), value.coerceIn(0, 255).toByte()))
    fun queryTimerSwitches(): ByteArray = FrameCodec.encode(byteArrayOf(0x0B.toByte()))

    fun syncTimeNow(calendar: Calendar = Calendar.getInstance()): ByteArray {
        val yearTwoDigit = calendar.get(Calendar.YEAR) % 100
        val monthOneBased = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val weekday = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)
        return syncTime(yearTwoDigit, monthOneBased, day, weekday, hour, minute, second)
    }

    fun syncTime(yearTwoDigit: Int, month: Int, day: Int, weekday: Int, hour: Int, minute: Int, second: Int): ByteArray = FrameCodec.encode(
        byteArrayOf(
            0x09.toByte(),
            yearTwoDigit.coerceIn(0, 99).toByte(),
            month.coerceIn(1, 12).toByte(),
            day.coerceIn(1, 31).toByte(),
            weekday.coerceIn(1, 7).toByte(),
            hour.coerceIn(0, 23).toByte(),
            minute.coerceIn(0, 59).toByte(),
            second.coerceIn(0, 59).toByte()
        )
    )

    fun setTimerSwitches(items: List<TimerSwitchCommand>): ByteArray {
        val body = mutableListOf<Byte>()
        body += 0x0A.toByte()
        body += items.size.coerceIn(0, 255).toByte()
        items.forEach { item ->
            body += (if (item.enabled) 1 else 0).toByte()
            body += item.hour.coerceIn(0, 23).toByte()
            body += item.minute.coerceIn(0, 59).toByte()
            body += item.weekdayMask.coerceIn(0, 127).toByte()
            body += (if (item.turnDeviceOn) 1 else 0).toByte()
            body += 0x00.toByte()
        }
        return FrameCodec.encode(body.toByteArray())
    }

    @Deprecated("Timer switches are on/off schedules in the APK, not countdown minutes. Use setTimerSwitches.")
    fun setTimer(minutes: Int, enabled: Boolean): ByteArray = setTimerSwitches(
        listOf(TimerSwitchCommand(enabled = enabled, hour = (minutes / 60) % 24, minute = minutes % 60, weekdayMask = 0, turnDeviceOn = enabled))
    )

    fun queryCountdownStatus(): ByteArray = FrameCodec.encode(byteArrayOf(0x0F.toByte(), 0x01))
    fun resetCountdown(hour: Int = 0, minute: Int = 0, second: Int = 0): ByteArray = FrameCodec.encode(
        byteArrayOf(0x0F.toByte(), 0x02, hour.coerceIn(0, 23).toByte(), minute.coerceIn(0, 59).toByte(), second.coerceIn(0, 59).toByte())
    )
    fun setCountdownRunning(running: Boolean): ByteArray = FrameCodec.encode(byteArrayOf(0x0F.toByte(), 0x03, (if (running) 1 else 0).toByte()))

    fun queryStopwatchStatus(): ByteArray = FrameCodec.encode(byteArrayOf(0x10.toByte(), 0x01))
    fun resetStopwatch(): ByteArray = FrameCodec.encode(byteArrayOf(0x10.toByte(), 0x02))
    fun setStopwatchRunning(running: Boolean): ByteArray = FrameCodec.encode(byteArrayOf(0x10.toByte(), 0x03, (if (running) 1 else 0).toByte()))

    fun queryScoreboardStatus(): ByteArray = FrameCodec.encode(byteArrayOf(0x11.toByte(), 0x01))
    fun resetScoreboard(left: Int = 0, right: Int = 0): ByteArray = FrameCodec.encode(byteArrayOf(0x11.toByte(), 0x02, left.coerceIn(0, 255).toByte(), right.coerceIn(0, 255).toByte()))
    fun setScoreboardRunning(running: Boolean): ByteArray = FrameCodec.encode(byteArrayOf(0x11.toByte(), 0x04, (if (running) 1 else 0).toByte()))

    fun setVolume(value: Int): ByteArray = FrameCodec.encode(byteArrayOf(0x1E.toByte(), 0x03, value.coerceIn(0, 100).toByte()))
    fun setColorMode(modeIndex: Int): ByteArray = FrameCodec.encode(byteArrayOf(0x13.toByte(), 0x03, modeIndex.coerceIn(0, 255).toByte()))

    fun queryTomato(): ByteArray = FrameCodec.encode(byteArrayOf(0x15.toByte(), 0x02))
    fun queryTemperatureHumidity(type: Int = 1): ByteArray = FrameCodec.encode(byteArrayOf(0x19.toByte(), type.coerceIn(0, 255).toByte()))

    fun queryAlarmList(): ByteArray = FrameCodec.encode(byteArrayOf(0x16.toByte(), 0x02))
    fun setAlarmList(alarms: List<AlarmCommand>): ByteArray {
        val body = mutableListOf(0x16.toByte(), 0x01, alarms.size.coerceIn(0, 255).toByte())
        alarms.forEach { a ->
            body += (if (a.enabled) 1 else 0).toByte()
            body += a.hour.coerceIn(0, 23).toByte()
            body += a.minute.coerceIn(0, 59).toByte()
            body += a.repeatMask.coerceIn(0, 127).toByte()
            body += shortToBytes(a.durationSeconds.coerceIn(0, 65535))
            body += a.reminderDurationMinutes.coerceIn(0, 255).toByte()
        }
        return FrameCodec.encode(body.toByteArray())
    }

    fun queryReminderList(): ByteArray = FrameCodec.encode(byteArrayOf(0x1A.toByte(), 0x01))
    fun queryReminderDetail(id: Int): ByteArray = FrameCodec.encode(byteArrayOf(0x1A.toByte(), 0x02, id.coerceIn(0, 255).toByte()))
    fun deleteReminder(id: Int): ByteArray = FrameCodec.encode(byteArrayOf(0x1A.toByte(), 0x03, id.coerceIn(0, 255).toByte()))

    fun setNightMode(enabled: Boolean, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int): ByteArray =
        FrameCodec.encode(
            byteArrayOf(
                0x14.toByte(), 0x01, (if (enabled) 1 else 0).toByte(),
                startHour.coerceIn(0, 23).toByte(), startMinute.coerceIn(0, 59).toByte(),
                endHour.coerceIn(0, 23).toByte(), endMinute.coerceIn(0, 59).toByte(),
                0x00, 0x00, 0x00, 0x00, 0x00
            )
        )

    fun checkPassword(password: String): ByteArray = passwordPayload(0x0D, password)
    fun setPassword(password: String): ByteArray = passwordPayload(0x0E, password)

    private fun passwordPayload(opcode: Int, password: String): ByteArray {
        val rand = Random.nextInt(0, 256)
        val masked = password.map { ch -> (("0$ch".toInt(16)) xor rand).toByte() }
        val xor = masked.fold(0) { acc, b -> acc xor (b.toInt() and 0xFF) }
        return FrameCodec.encode(byteArrayOf(opcode.toByte(), rand.toByte()) + masked.toByteArray() + byteArrayOf(xor.toByte()))
    }

    fun buildProgramStartHeader(family: DeviceFamily, request: ProgramStartRequest): ByteArray {
        val trailer = if (request.programType == null) byteArrayOf() else typedProgramTrailer(request.programType, request.extraTypeByte)
        val opcode = when {
            request.useAlternateOpcode -> 0x1A
            else -> 0x02
        }
        val startSource = request.startSource ?: request.compressed
        val crc = CoolLedCrc.crc32Like(startSource)
        val base = mutableListOf(opcode.toByte())
        base += intToBytes(crc)
        base += intToBytes(startSource.size)
        base += (request.index and 0xFF).toByte()
        request.count?.let { base += (it and 0xFF).toByte() }
        request.showCount?.let { base += (it and 0xFF).toByte() }

        // CoolledUXUtils.getStartDataForProgram basic overload is used for normal
        // CoolLEDUX uploads; advanced fields are only APK-confirmed for the
        // isILedCar path, so do not append speculative trailers here.
        if (family != DeviceFamily.COOLLEDUX && trailer.isNotEmpty()) {
            base += 0x00
            repeat(8) { base += 0x00 }
            base += trailer.toList()
        }
        return FrameCodec.encode(base.toByteArray())
    }

    fun buildOtaStartHeader(compressedFirmware: ByteArray, includePreamble64: Boolean): ByteArray {
        val crc = CoolLedCrc.crc32Like(compressedFirmware)
        val base = mutableListOf(0xFE.toByte())
        base += intToBytes(crc)
        base += intToBytes(compressedFirmware.size)
        if (includePreamble64) {
            base += 0x40
            base += compressedFirmware.take(64)
        }
        return FrameCodec.encode(base.toByteArray())
    }

    fun buildDataChunk(messageType: Int, totalCompressedLength: Int, chunkIndex: Int, chunk: ByteArray): ByteArray {
        val payloadAfterMessageType = mutableListOf<Byte>()
        payloadAfterMessageType += 0x00
        payloadAfterMessageType += intToBytes(totalCompressedLength)
        payloadAfterMessageType += shortToBytes(chunkIndex)
        payloadAfterMessageType += shortToBytes(chunk.size)
        payloadAfterMessageType += chunk.toList()
        val xor = payloadAfterMessageType.fold(0) { acc, b -> acc xor (b.toInt() and 0xFF) }
        val body = mutableListOf(messageType.toByte())
        body += payloadAfterMessageType
        body += xor.toByte()
        return FrameCodec.encode(body.toByteArray())
    }

    fun splitChunks(data: ByteArray, chunkSize: Int = 1024): List<ByteArray> = data.toList().chunked(chunkSize).map { it.toByteArray() }

    private fun typedProgramTrailer(programType: Int, extraTypeByte: Int?): ByteArray = when (programType) {
        8 -> byteArrayOf(0x01)
        9 -> byteArrayOf(0x02)
        11 -> byteArrayOf(0x03)
        7 -> byteArrayOf(0x04, 0x01, 0x00, 0x00, 0x00, 0x0A)
        6, 19 -> byteArrayOf(0x04, 0x01, 0x00, 0x00, 0x00, 0x05)
        14 -> if (extraTypeByte == null) byteArrayOf(0x05) else byteArrayOf(0x05, extraTypeByte.toByte())
        else -> byteArrayOf(0x00, 0x00) + intToBytes(extraTypeByte ?: 0).toByteArray()
    }

    private fun intToBytes(value: Int) = listOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    private fun shortToBytes(value: Int) = listOf(((value ushr 8) and 0xFF).toByte(), (value and 0xFF).toByte())
}

data class TimerSwitchCommand(
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val weekdayMask: Int,
    val turnDeviceOn: Boolean
)

data class AlarmCommand(
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val repeatMask: Int,
    val durationSeconds: Int,
    val reminderDurationMinutes: Int
)

data class ProgramStartRequest(
    val compressed: ByteArray,
    val index: Int,
    val count: Int? = null,
    val showCount: Int? = null,
    val useAlternateOpcode: Boolean = false,
    val programType: Int? = null,
    val extraTypeByte: Int? = null,
    val startSource: ByteArray? = null
)
