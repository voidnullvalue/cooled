package com.cooled.data.repositories

import com.cooled.core.ble.BleTransport
import com.cooled.core.ble.ScanDevice
import com.cooled.core.model.DeviceFamily
import com.cooled.core.model.FamilyDetector
import com.cooled.core.protocol.AlarmCommand
import com.cooled.core.protocol.CommandBuilders
import com.cooled.core.protocol.ParsedPayload
import com.cooled.core.protocol.ProgramContent
import com.cooled.core.protocol.ProgramComposer
import com.cooled.core.protocol.ProgramPackage
import com.cooled.core.protocol.ProgramStartRequest
import com.cooled.core.protocol.ProtocolParsers
import com.cooled.data.persistence.RememberedDeviceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DeviceRepository(
    private val transport: BleTransport,
    private val store: RememberedDeviceStore
) {
    val scanResults: Flow<List<ScanDevice>> = transport.scanResults
    val connectionState = transport.connectionState
    val mtu = transport.mtu
    val parsedRx: Flow<ParsedPayload> = transport.rxFrames.map { ProtocolParsers.parseFrame(it.bytes) }

    fun startScan() = transport.startScan()
    fun stopScan() = transport.stopScan()

    suspend fun connect(address: String) {
        transport.connect(address)
        store.remember(address)
    }

    suspend fun disconnect() = transport.disconnect()

    suspend fun sendPower(on: Boolean) = transport.write(CommandBuilders.setPower(on))
    suspend fun sendBrightness(value: Int) = transport.write(CommandBuilders.setBrightness(value))
    suspend fun sendRhythm(type: Int) = transport.write(CommandBuilders.setRhythm(type))
    suspend fun sendMirror(v: Int) = transport.write(CommandBuilders.setMirror(v))
    suspend fun sendColorMode(modeIndex: Int) = transport.write(CommandBuilders.setColorMode(modeIndex))
    suspend fun sendQueryInfo() = transport.write(CommandBuilders.queryDeviceInfo())
    suspend fun sendCheckPassword(password: String) = transport.write(CommandBuilders.checkPassword(password))
    suspend fun sendSetPassword(password: String) = transport.write(CommandBuilders.setPassword(password))

    suspend fun sendTimeSync(epochSeconds: Int) = transport.write(CommandBuilders.syncTime(epochSeconds))
    suspend fun sendSetTimer(minutes: Int, enabled: Boolean) = transport.write(CommandBuilders.setTimer(minutes, enabled))
    suspend fun sendCountdown(running: Boolean) = transport.write(CommandBuilders.setCountdownRunning(running))
    suspend fun sendStopwatch(running: Boolean) = transport.write(CommandBuilders.setStopwatchRunning(running))
    suspend fun resetCountdown() = transport.write(CommandBuilders.resetCountdown())
    suspend fun resetStopwatch() = transport.write(CommandBuilders.resetStopwatch())
    suspend fun sendScoreboard(running: Boolean) = transport.write(CommandBuilders.setScoreboardRunning(running))
    suspend fun sendVolume(value: Int) = transport.write(CommandBuilders.setVolume(value))
    suspend fun sendQueryTomato() = transport.write(CommandBuilders.queryTomato())
    suspend fun sendQueryTempHumidity() = transport.write(CommandBuilders.queryTemperatureHumidity())
    suspend fun sendAlarmList(alarms: List<AlarmCommand>) = transport.write(CommandBuilders.setAlarmList(alarms))
    suspend fun sendQueryAlarms() = transport.write(CommandBuilders.queryAlarmList())
    suspend fun sendQueryReminderList() = transport.write(CommandBuilders.queryReminderList())
    suspend fun sendQueryReminderDetail(id: Int) = transport.write(CommandBuilders.queryReminderDetail(id))
    suspend fun sendDeleteReminder(id: Int) = transport.write(CommandBuilders.deleteReminder(id))
    suspend fun sendNightMode(enabled: Boolean, sh: Int, sm: Int, eh: Int, em: Int) =
        transport.write(CommandBuilders.setNightMode(enabled, sh, sm, eh, em))

    suspend fun sendProgramStart(family: DeviceFamily, request: ProgramStartRequest) =
        transport.write(CommandBuilders.buildProgramStartHeader(family, request))

    suspend fun sendDataChunk(messageType: Int, totalCompressedLength: Int, chunkIndex: Int, chunk: ByteArray) =
        transport.write(CommandBuilders.buildDataChunk(messageType, totalCompressedLength, chunkIndex, chunk))

    suspend fun sendComposedProgram(
        family: DeviceFamily,
        content: ProgramContent,
        index: Int = 0,
        count: Int = 1,
        showCount: Int = 1,
        programType: Int? = null,
        extraTypeByte: Int? = null
    ): ProgramPackage {
        val pack = ProgramComposer.compose(family, content, index, count, showCount, programType, extraTypeByte)
        transport.write(pack.startHeaderFrame)
        pack.chunkFrames.forEach { transport.write(it) }
        return pack
    }

    fun detectFamily(name: String?) = FamilyDetector.detect(name)
    fun remembered() = store.all()
}
