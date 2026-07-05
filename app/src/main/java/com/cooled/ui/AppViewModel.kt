package com.cooled.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cooled.core.assets.OriginalLedAssetKinds
import com.cooled.core.assets.OriginalLedAssetProgramPlanner
import com.cooled.core.ble.AndroidBleTransport
import com.cooled.core.ble.BleIoDirection
import com.cooled.core.ble.BleTransport
import com.cooled.core.ble.ConnectionState
import com.cooled.core.ble.FakeBleTransport
import com.cooled.core.ble.LedScanMetadata
import com.cooled.core.model.CapabilityMap
import com.cooled.core.model.DeviceFamily
import com.cooled.core.protocol.AlarmCommand
import com.cooled.core.protocol.ParsedPayload
import com.cooled.core.protocol.ProgramContent
import com.cooled.core.protocol.ProgramPackage
import com.cooled.core.protocol.ProgramStartRequest
import com.cooled.core.protocol.ProgramUploadFollowUp
import com.cooled.core.protocol.TimerSwitchCommand
import com.cooled.core.protocol.TransferState
import com.cooled.core.protocol.TransferStateMachine
import com.cooled.data.persistence.RememberedDeviceStore
import com.cooled.data.repositories.DeviceRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(
    private val transport: BleTransport = FakeBleTransport(),
    rememberedDeviceStore: RememberedDeviceStore = RememberedDeviceStore()
) : ViewModel() {
    private val fake = transport as? FakeBleTransport
    private val repo = DeviceRepository(transport, rememberedDeviceStore)
    private val transferMachine = TransferStateMachine()

    private var pendingProgram: ProgramPackage? = null
    private var pendingChunkIndex: Int = 0
    private var pendingStartRetries: Int = 0
    private var connectedMetadata: LedScanMetadata = LedScanMetadata()
        set(value) {
            field = value
            _connectedMetadata.value = value
        }
    private val _connectedMetadata = MutableStateFlow(LedScanMetadata())
    val connectedDeviceMetadata: StateFlow<LedScanMetadata> = _connectedMetadata.asStateFlow()

    private val _events = MutableStateFlow<List<String>>(emptyList())
    val events: StateFlow<List<String>> = _events.asStateFlow()
    val transferState: StateFlow<TransferState> = transferMachine.state
    val transportMode = MutableStateFlow(if (fake == null) "Android BLE" else "Fake demo")

    private val _lastParsedSummary = MutableStateFlow("No packet yet")
    val lastParsedSummary: StateFlow<String> = _lastParsedSummary.asStateFlow()

    val scanResults = repo.scanResults.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val connection = repo.connectionState.stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionState.DISCONNECTED)
    val mtu = repo.mtu.stateIn(viewModelScope, SharingStarted.Eagerly, 23)
    val parsed = repo.parsedRx.stateIn(viewModelScope, SharingStarted.Eagerly, ParsedPayload.Unknown(byteArrayOf()))
    val family = MutableStateFlow(DeviceFamily.UNKNOWN)
    val capabilities = MutableStateFlow(CapabilityMap.forFamily(DeviceFamily.UNKNOWN))

    init {
        appendEvent("${ts()} Transport mode=${transportMode.value}")
        viewModelScope.launch {
            repo.parsedRx.collect { p ->
                transferMachine.onParsed(p)
                handleProgramTransferAck(p)
                val summary = p.summary()
                _lastParsedSummary.value = summary
                val kind = when (p) {
                    is ParsedPayload.Unknown, is ParsedPayload.ParseError -> "unknown"
                    else -> "parsed"
                }
                appendEvent("${ts()} RX $kind: $summary")
            }
        }
        viewModelScope.launch {
            repo.connectionState.collect {
                if (it == ConnectionState.DISCONNECTED) {
                    pendingProgram = null
                    pendingChunkIndex = 0
                    pendingStartRetries = 0
                    connectedMetadata = LedScanMetadata()
                    transferMachine.cancel()
                    appendEvent("${ts()} Transfer cleanup on disconnect")
                }
            }
        }
        viewModelScope.launch {
            transport.ioEvents.collect { evt ->
                val dir = if (evt.direction == BleIoDirection.TX) "TX" else "RX"
                appendEvent("${evt.timestampMs} $dir raw(${evt.bytes.size}): ${evt.bytes.toHex()} ${evt.note.orEmpty()}")
            }
        }
    }

    fun scan() = repo.startScan()
    fun stopScan() = repo.stopScan()
    fun connect(address: String, name: String?) = viewModelScope.launch {
        val found = scanResults.value.firstOrNull { it.address == address }
        connectedMetadata = found?.metadata ?: LedScanMetadata()
        repo.connect(address)
        val f = repo.detectFamily(name)
        family.value = f
        capabilities.value = CapabilityMap.forFamily(f)
        appendEvent("${ts()} Device family=$f matrix=${connectedMetadata.columns ?: "?"}x${connectedMetadata.rows ?: "?"} colorType=${connectedMetadata.colorType ?: "?"} caps=${capabilities.value}")
    }
    fun disconnect() = viewModelScope.launch { repo.disconnect() }

    fun queryInfo() = viewModelScope.launch { repo.sendQueryInfo() }
    fun queryOtaVersion() = viewModelScope.launch { repo.sendQueryOtaVersion() }
    fun queryDriveState() = viewModelScope.launch { repo.sendQueryDriveState() }
    fun setDriveState(state: Int) = viewModelScope.launch { repo.sendDriveState(state.coerceIn(0, 255)) }
    fun power(on: Boolean) = viewModelScope.launch { repo.sendPower(on) }
    fun brightness(v: Int) = viewModelScope.launch { repo.sendBrightness(v.coerceIn(1, 100)) }
    fun speed(v: Int) = viewModelScope.launch { repo.sendRhythm(v) }
    fun mirror(v: Int) = viewModelScope.launch { repo.sendMirror(v) }
    fun colorMode(v: Int) = viewModelScope.launch { repo.sendColorMode(v.coerceIn(0, 255)) }
    fun checkPassword(p: String) = viewModelScope.launch { repo.sendCheckPassword(p) }
    fun setPassword(p: String) = viewModelScope.launch { repo.sendSetPassword(p) }

    fun syncTimeNow() = viewModelScope.launch { repo.sendTimeSyncNow() }
    fun timer(minutes: Int, enabled: Boolean) = viewModelScope.launch { repo.sendSetTimer(minutes.coerceIn(0, 24 * 60), enabled) }
    fun setTimerSwitch(enabled: Boolean, hour: Int, minute: Int, weekdayMask: Int, turnDeviceOn: Boolean) = viewModelScope.launch {
        repo.sendTimerSwitches(listOf(TimerSwitchCommand(enabled, hour.coerceIn(0, 23), minute.coerceIn(0, 59), weekdayMask.coerceIn(0, 127), turnDeviceOn)))
    }
    fun queryTimerSwitches() = viewModelScope.launch { repo.sendQueryTimerSwitches() }

    fun queryCountdownStatus() = viewModelScope.launch { repo.sendQueryCountdownStatus() }
    fun countdown(running: Boolean) = viewModelScope.launch { repo.sendCountdown(running) }
    fun resetCountdown() = viewModelScope.launch { repo.resetCountdown() }
    fun resetCountdownTo(hour: Int, minute: Int, second: Int) = viewModelScope.launch { repo.resetCountdown(hour.coerceIn(0, 23), minute.coerceIn(0, 59), second.coerceIn(0, 59)) }

    fun queryStopwatchStatus() = viewModelScope.launch { repo.sendQueryStopwatchStatus() }
    fun stopwatch(running: Boolean) = viewModelScope.launch { repo.sendStopwatch(running) }
    fun resetStopwatch() = viewModelScope.launch { repo.resetStopwatch() }

    fun queryScoreboardStatus() = viewModelScope.launch { repo.sendQueryScoreboardStatus() }
    fun scoreboard(running: Boolean) = viewModelScope.launch { repo.sendScoreboard(running) }
    fun resetScoreboard(hostScore: Int, guestScore: Int, hostSets: Int = 0, guestSets: Int = 0) = viewModelScope.launch {
        repo.resetScoreboard(hostScore.coerceIn(0, 65535), guestScore.coerceIn(0, 65535), hostSets.coerceIn(0, 255), guestSets.coerceIn(0, 255))
    }

    fun volume(v: Int) = viewModelScope.launch { repo.sendVolume(v.coerceIn(0, 100)) }
    fun queryTomato() = viewModelScope.launch { repo.sendQueryTomato() }
    fun queryTempHumidity() = viewModelScope.launch { repo.sendQueryTempHumidity() }

    fun queryAlarms() = viewModelScope.launch { repo.sendQueryAlarms() }
    fun setAlarm(enabled: Boolean, hour: Int, minute: Int, repeatMask: Int, durationSeconds: Int, reminderDurationMinutes: Int) = viewModelScope.launch {
        repo.sendAlarmList(listOf(AlarmCommand(enabled, hour.coerceIn(0, 23), minute.coerceIn(0, 59), repeatMask.coerceIn(0, 127), durationSeconds.coerceIn(0, 65535), reminderDurationMinutes.coerceIn(0, 255))))
    }
    fun setSampleAlarm() = setAlarm(true, 7, 30, 0b0111110, 600, 5)

    fun setNightMode(enabled: Boolean, sh: Int, sm: Int, eh: Int, em: Int) = viewModelScope.launch { repo.sendNightMode(enabled, sh.coerceIn(0, 23), sm.coerceIn(0, 59), eh.coerceIn(0, 23), em.coerceIn(0, 59)) }
    fun queryReminderList() = viewModelScope.launch { repo.sendQueryReminderList() }
    fun queryReminderDetail(id: Int) = viewModelScope.launch { repo.sendQueryReminderDetail(id.coerceIn(0, 255)) }
    fun deleteReminder(id: Int) = viewModelScope.launch { repo.sendDeleteReminder(id.coerceIn(0, 255)) }

    fun sendTextProgram(text: String, speed: Int, effect: Int, programType: Int?, extraTypeByte: Int?) = viewModelScope.launch {
        val cleanText = text.ifBlank { "HELLO" }.take(128)
        val pack = buildProgramPackage(
            content = ProgramContent.Text(
                text = cleanText,
                speed = speed.coerceIn(0, 255),
                effect = effect.coerceIn(0, 255),
                displayColumns = connectedMetadata.columns,
                displayRows = connectedMetadata.rows
            ),
            programType = programType,
            extraTypeByte = extraTypeByte
        )
        queueProgramUpload(pack, "text='$cleanText' matrix=${connectedMetadata.columns ?: "?"}x${connectedMetadata.rows ?: "?"} speed=${speed.coerceIn(0, 255)} effect=${effect.coerceIn(0, 255)} programType=${programType ?: "none"} extra=${extraTypeByte ?: "none"}")
    }

    fun sendOriginalAssetProgram(assetPath: String, kind: String, speed: Int, effect: Int, programType: Int?, extraTypeByte: Int?) = viewModelScope.launch {
        val selection = OriginalLedAssetProgramPlanner.selectByPath(
            assetPath = assetPath,
            kind = kind,
            displayColumns = connectedMetadata.columns,
            displayRows = connectedMetadata.rows,
            speed = speed,
            effect = effect,
            programType = programType,
            extraTypeByte = extraTypeByte
        )
        if (selection == null) {
            appendEvent("${ts()} Original asset upload skipped: blank asset path")
            return@launch
        }
        if (!selection.uploadCheck.uploadable) {
            appendEvent("${ts()} Original asset upload blocked: ${selection.uploadCheck.reason} (${selection.asset.path})")
            return@launch
        }
        queueOriginalAssetSelection(selection.toProgramContent(), selection.debugSummary(), selection.programType, selection.extraTypeByte)
    }

    fun sendPreferredOriginalAssetProgram(kind: String, speed: Int, effect: Int, programType: Int?, extraTypeByte: Int?) = viewModelScope.launch {
        val cleanKind = kind.ifBlank { OriginalLedAssetKinds.PAYLOAD_ASSET }.take(32)
        val selection = OriginalLedAssetProgramPlanner.selectPreferred(
            kind = cleanKind,
            displayColumns = connectedMetadata.columns,
            displayRows = connectedMetadata.rows,
            speed = speed,
            effect = effect,
            programType = programType,
            extraTypeByte = extraTypeByte
        )
        if (selection == null) {
            appendEvent("${ts()} Preferred original asset upload skipped: no asset found for kind=$cleanKind")
            return@launch
        }
        if (!selection.uploadCheck.uploadable) {
            appendEvent("${ts()} Preferred original asset upload blocked: ${selection.uploadCheck.reason} (${selection.asset.path})")
            return@launch
        }
        queueOriginalAssetSelection(selection.toProgramContent(), selection.debugSummary(), selection.programType, selection.extraTypeByte)
    }

    fun sendTextProgram() = sendTextProgram("HELLO", 255, 2, if (family.value == DeviceFamily.ILEDCLOCK) 14 else null, if (family.value == DeviceFamily.ILEDCLOCK) 1 else null)

    private fun queueOriginalAssetSelection(content: ProgramContent.OriginalAsset, description: String, programType: Int?, extraTypeByte: Int?) {
        val pack = buildProgramPackage(content = content, programType = programType, extraTypeByte = extraTypeByte)
        queueProgramUpload(pack, description)
    }

    private fun buildProgramPackage(content: ProgramContent, programType: Int?, extraTypeByte: Int?): ProgramPackage {
        // Official APK starts a one-program CoolLEDUX upload with index=0,count=1.
        // Rotating index while still declaring count=1 stores data in a non-displayed slot.
        val programIndex = 0
        return repo.composeProgram(
            family = family.value,
            content = content,
            index = programIndex,
            count = 1,
            showCount = 1,
            programType = programType,
            extraTypeByte = extraTypeByte
        )
    }

    private fun queueProgramUpload(pack: ProgramPackage, description: String) {
        pendingProgram = pack
        pendingChunkIndex = 0
        pendingStartRetries = 3
        transferMachine.startSession(pack.metadata.chunkCount)
        appendEvent("${ts()} Program start queued $description compressed=${pack.metadata.compressedSize} chunks=${pack.metadata.chunkCount}")
        appendEvent("${ts()} Program start sent index=0 count=1 showCount=1")
        viewModelScope.launch { repo.sendRawFrame(pack.startHeaderFrame) }
    }

    private fun handleProgramTransferAck(payload: ParsedPayload) {
        val pack = pendingProgram ?: return
        when (payload) {
            is ParsedPayload.TransferStartResponse -> {
                if (payload.status == 0) {
                    appendEvent("${ts()} Program start ACK status=0")
                    pendingStartRetries = 0
                    pendingChunkIndex = 0
                    sendPendingChunk(pack)
                } else if (payload.status == 1 && pendingStartRetries > 0) {
                    pendingStartRetries--
                    appendEvent("${ts()} Program start ACK status=${payload.status}; retrying start (${pendingStartRetries} left)")
                    viewModelScope.launch {
                        delay(300L)
                        if (pendingProgram === pack) repo.sendRawFrame(pack.startHeaderFrame)
                    }
                } else {
                    pendingProgram = null
                    pendingChunkIndex = 0
                    pendingStartRetries = 0
                    appendEvent("${ts()} Program upload aborted: start rejected status=${payload.status}")
                }
            }
            is ParsedPayload.TransferChunkResponse -> {
                if (payload.status == 0) {
                    appendEvent("${ts()} Program chunk ${payload.chunkIndex} ACK status=0")
                    pendingChunkIndex = payload.chunkIndex + 1
                    if (pendingChunkIndex >= pack.chunkFrames.size) {
                        appendEvent("${ts()} Program upload completed chunks=${pack.chunkFrames.size}")
                        pendingProgram = null
                        pendingChunkIndex = 0
                        pendingStartRetries = 0
                        runPostUploadFollowUp(pack)
                    } else {
                        sendPendingChunk(pack)
                    }
                } else if (payload.status == 1) {
                    appendEvent("${ts()} Program chunk ${payload.chunkIndex} NACK; resending")
                    pendingChunkIndex = payload.chunkIndex.coerceIn(0, pack.chunkFrames.lastIndex)
                    sendPendingChunk(pack)
                } else {
                    pendingProgram = null
                    pendingChunkIndex = 0
                    pendingStartRetries = 0
                    appendEvent("${ts()} Program upload aborted: chunk ${payload.chunkIndex} rejected status=${payload.status}")
                }
            }
            else -> Unit
        }
    }

    private fun runPostUploadFollowUp(pack: ProgramPackage) = viewModelScope.launch {
        val followUp = ProgramUploadFollowUp.afterSuccessfulUpload(pack.metadata.family) ?: return@launch
        delay(followUp.delayMs)
        appendEvent("${ts()} ${followUp.logLabel} sent")
        repo.sendRawFrame(followUp.frame)
    }

    private fun sendPendingChunk(pack: ProgramPackage) = viewModelScope.launch {
        val chunk = pack.chunkFrames.getOrNull(pendingChunkIndex) ?: return@launch
        appendEvent("${ts()} Program chunk $pendingChunkIndex sent (${pendingChunkIndex + 1}/${pack.chunkFrames.size})")
        repo.sendRawFrame(chunk)
    }

    fun startFakeTransfer() = viewModelScope.launch {
        val fakeTransport = fake ?: run {
            appendEvent("${ts()} Fake scripted transfer is unavailable in Android BLE mode")
            return@launch
        }
        val compressed = ByteArray(2500) { (it % 17).toByte() }
        val chunks = compressed.toList().chunked(1024).map { it.toByteArray() }
        transferMachine.startSession(chunks.size)
        repo.sendProgramStart(family.value, ProgramStartRequest(compressed = compressed, index = 0, count = 1, showCount = 1, programType = 14, extraTypeByte = 1))
        fakeTransport.enqueueRxPayload(byteArrayOf(0x02, 0x00), "start-ack")
        chunks.forEachIndexed { idx, c ->
            repo.sendDataChunk(0x03, compressed.size, idx, c)
            fakeTransport.enqueueRxPayload(byteArrayOf(0x03, 0x00, ((idx shr 8) and 0xFF).toByte(), (idx and 0xFF).toByte(), 0x00), "chunk-ack")
        }
    }

    fun scriptTransferScenario(name: String) {
        val fakeTransport = fake ?: run {
            appendEvent("${ts()} Script '$name' ignored; Android BLE mode has no fake response queue")
            return
        }
        fakeTransport.clearScripted()
        when (name) {
            "happy" -> {
                fakeTransport.enqueueRxPayload(byteArrayOf(0x02, 0x00), "start-ok")
                repeat(6) { i -> fakeTransport.enqueueRxPayload(byteArrayOf(0x03, 0x00, 0x00, i.toByte(), 0x00), "chunk-ok-$i") }
            }
            "delayed_ack" -> {
                fakeTransport.enqueueRawFrame(byteArrayOf(0x7E, 0x00, 0x7E), "unexpected-frame")
                fakeTransport.enqueueRxPayload(byteArrayOf(0x02, 0x00), "late-start-ok")
            }
            "nack_then_success" -> {
                fakeTransport.enqueueRxPayload(byteArrayOf(0x02, 0x00), "start-ok")
                fakeTransport.enqueueRxPayload(byteArrayOf(0x03, 0x00, 0x00, 0x00, 0x01), "nack-1")
                fakeTransport.enqueueRxPayload(byteArrayOf(0x03, 0x00, 0x00, 0x00, 0x01), "nack-2")
                fakeTransport.enqueueRxPayload(byteArrayOf(0x03, 0x00, 0x00, 0x00, 0x00), "finally-ok")
            }
            "retry_exhaust" -> {
                fakeTransport.enqueueRxPayload(byteArrayOf(0x02, 0x00), "start-ok")
                repeat(5) { fakeTransport.enqueueRxPayload(byteArrayOf(0x03, 0x00, 0x00, 0x00, 0x01), "nack") }
            }
            "unexpected_packet" -> fakeTransport.enqueueRawFrame(byteArrayOf(0x7E, 0x01, 0x02, 0x03), "garbage")
        }
        appendEvent("${ts()} Loaded transfer script=$name")
    }

    fun timeoutTransfer() {
        transferMachine.onTimeout()
        appendEvent("${ts()} Transfer timeout tick -> ${transferMachine.state.value}")
    }

    fun cancelTransfer() {
        pendingProgram = null
        pendingChunkIndex = 0
        pendingStartRetries = 0
        transferMachine.cancel()
        appendEvent("${ts()} Transfer cancelled")
    }

    fun copyDebugLog(): String = events.value.joinToString("\n")

    private fun appendEvent(text: String) {
        _events.value = (listOf(text) + _events.value).take(200)
    }

    private fun ParsedPayload.summary(): String = when (this) {
        is ParsedPayload.Unknown -> "Unknown payload (${data.size} bytes): ${data.toHex()}"
        is ParsedPayload.ParseError -> "Parse error opcode=0x${opcode.hex2()} reason=$reason raw=${data.toHex()}"
        is ParsedPayload.BrightnessState -> "Brightness=$value"
        is ParsedPayload.PowerState -> "Power=${if (on) "on" else "off"}"
        is ParsedPayload.MirrorState -> "Mirror/rotate value=$value"
        is ParsedPayload.PasswordCheckResult -> "Password check ${if (success) "OK" else "failed"} code=$code"
        is ParsedPayload.PasswordSetResult -> "Password set ${if (success) "OK" else "failed"} code=$code"
        is ParsedPayload.DriveState -> "Drive state opcode=0x${opcode.hex2()} state=$state"
        is ParsedPayload.DeviceInfo -> "Device model=$model fw=$fwMajor.$fwMinor matrix=${columns}x$rows colorType=$colorType packageSize=${packageSize ?: "unknown"}"
        is ParsedPayload.OtaInfo -> "OTA supported=$supported version=$versionMajor.$versionMinor remoteFile=$remoteFile"
        is ParsedPayload.TimerSwitches -> "Timer switches=${values.joinToString()}"
        is ParsedPayload.TimeSyncAck -> "Time sync status=$status"
        is ParsedPayload.TimerAck -> "Timer status=$status"
        is ParsedPayload.VolumeState -> "Volume=$value"
        is ParsedPayload.CountdownState -> "Countdown sub=$subcommand time=${minute ?: "?"}:${second ?: "?"} running=${running ?: "?"}"
        is ParsedPayload.StopwatchState -> "Stopwatch sub=$subcommand time=${minute ?: "?"}:${second ?: "?"} running=${running ?: "?"}"
        is ParsedPayload.ScoreboardState -> "Scoreboard left=${left ?: "?"} right=${right ?: "?"} mode=${mode ?: "?"} running=${running ?: "?"}"
        is ParsedPayload.NightModeState -> "Night mode enabled=${enabled ?: "?"} ${startHour ?: "?"}:${startMinute ?: "?"}-${endHour ?: "?"}:${endMinute ?: "?"}"
        is ParsedPayload.TomatoClockState -> "Tomato items=${items.map { it.value }.joinToString()}"
        is ParsedPayload.AlarmList -> "Alarms count=${alarms.size}"
        is ParsedPayload.ReminderList -> "Reminder IDs=${ids.joinToString()}"
        is ParsedPayload.ReminderDetail -> "Reminder id=$id date=$year-$month-$day $hour:$minute repeat=$repeatType duration=$durationSeconds content=$content"
        is ParsedPayload.TemperatureHumidity -> "Temperature=$temperatureCelsius C humidity=$humidity%"
        is ParsedPayload.TransferStartResponse -> "Transfer start response opcode=0x${opcode.hex2()} status=$status (${transferStatusLabel(status)})"
        is ParsedPayload.TransferChunkResponse -> "Transfer chunk response opcode=0x${opcode.hex2()} chunk=$chunkIndex status=$status (${transferStatusLabel(status)})"
    }

    private fun transferStatusLabel(status: Int): String = when (status) {
        0 -> "OK"
        1 -> "retry/nack"
        2 -> "busy/invalid state"
        3 -> "rejected/unsupported"
        else -> "unknown"
    }

    private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
    private fun Int.hex2(): String = "%02X".format(this and 0xFF)
    private fun ts() = System.currentTimeMillis().toString()

    companion object {
        fun androidBleFactory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val appContext = extras[APPLICATION_KEY] ?: context.applicationContext
                return AppViewModel(AndroidBleTransport(appContext), RememberedDeviceStore()) as T
            }
        }
    }
}
