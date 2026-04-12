package com.cooled.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooled.core.ble.BleIoDirection
import com.cooled.core.ble.ConnectionState
import com.cooled.core.ble.FakeBleTransport
import com.cooled.core.model.CapabilityMap
import com.cooled.core.model.DeviceFamily
import com.cooled.core.protocol.AlarmCommand
import com.cooled.core.protocol.ParsedPayload
import com.cooled.core.protocol.ProgramContent
import com.cooled.core.protocol.ProgramStartRequest
import com.cooled.core.protocol.TransferState
import com.cooled.core.protocol.TransferStateMachine
import com.cooled.data.persistence.RememberedDeviceStore
import com.cooled.data.repositories.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel : ViewModel() {
    private val fake = FakeBleTransport()
    private val repo = DeviceRepository(fake, RememberedDeviceStore())
    private val transferMachine = TransferStateMachine()

    private val _events = MutableStateFlow<List<String>>(emptyList())
    val events: StateFlow<List<String>> = _events.asStateFlow()
    val transferState: StateFlow<TransferState> = transferMachine.state

    val scanResults = repo.scanResults.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val connection = repo.connectionState.stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionState.DISCONNECTED)
    val mtu = repo.mtu.stateIn(viewModelScope, SharingStarted.Eagerly, 23)
    val parsed = repo.parsedRx.stateIn(viewModelScope, SharingStarted.Eagerly, ParsedPayload.Unknown(byteArrayOf()))
    val family = MutableStateFlow(DeviceFamily.UNKNOWN)
    val capabilities = MutableStateFlow(CapabilityMap.forFamily(DeviceFamily.UNKNOWN))

    init {
        viewModelScope.launch {
            repo.parsedRx.collect { p ->
                transferMachine.onParsed(p)
                val kind = when (p) {
                    is ParsedPayload.Unknown, is ParsedPayload.ParseError -> "unknown"
                    else -> "parsed"
                }
                appendEvent("${ts()} RX $kind: $p")
            }
        }
        viewModelScope.launch {
            repo.connectionState.collect {
                if (it == ConnectionState.DISCONNECTED) {
                    transferMachine.cancel()
                    appendEvent("${ts()} Transfer cleanup on disconnect")
                }
            }
        }
        viewModelScope.launch {
            fake.ioEvents.collect { evt ->
                val dir = if (evt.direction == BleIoDirection.TX) "TX" else "RX"
                appendEvent("${evt.timestampMs} $dir raw(${evt.bytes.size}): ${evt.bytes.toHex()} ${evt.note.orEmpty()}")
            }
        }
    }

    fun scan() = repo.startScan()
    fun connect(address: String, name: String?) = viewModelScope.launch {
        repo.connect(address)
        val f = repo.detectFamily(name)
        family.value = f
        capabilities.value = CapabilityMap.forFamily(f)
        appendEvent("${ts()} Device family=$f caps=${capabilities.value}")
    }

    fun queryInfo() = viewModelScope.launch { repo.sendQueryInfo() }
    fun power(on: Boolean) = viewModelScope.launch { repo.sendPower(on) }
    fun brightness(v: Int) = viewModelScope.launch { repo.sendBrightness(v) }
    fun speed(v: Int) = viewModelScope.launch { repo.sendRhythm(v) }
    fun mirror(v: Int) = viewModelScope.launch { repo.sendMirror(v) }
    fun colorMode(v: Int) = viewModelScope.launch { repo.sendColorMode(v) }
    fun checkPassword(p: String) = viewModelScope.launch { repo.sendCheckPassword(p) }
    fun setPassword(p: String) = viewModelScope.launch { repo.sendSetPassword(p) }

    fun syncTimeNow() = viewModelScope.launch { repo.sendTimeSync((System.currentTimeMillis() / 1000L).toInt()) }
    fun timer(minutes: Int, enabled: Boolean) = viewModelScope.launch { repo.sendSetTimer(minutes, enabled) }
    fun countdown(running: Boolean) = viewModelScope.launch { repo.sendCountdown(running) }
    fun stopwatch(running: Boolean) = viewModelScope.launch { repo.sendStopwatch(running) }
    fun scoreboard(running: Boolean) = viewModelScope.launch { repo.sendScoreboard(running) }
    fun volume(v: Int) = viewModelScope.launch { repo.sendVolume(v) }
    fun queryTomato() = viewModelScope.launch { repo.sendQueryTomato() }
    fun queryTempHumidity() = viewModelScope.launch { repo.sendQueryTempHumidity() }
    fun queryAlarms() = viewModelScope.launch { repo.sendQueryAlarms() }
    fun setNightMode() = viewModelScope.launch { repo.sendNightMode(true, 22, 0, 6, 0) }
    fun queryReminderList() = viewModelScope.launch { repo.sendQueryReminderList() }
    fun resetCountdown() = viewModelScope.launch { repo.resetCountdown() }
    fun resetStopwatch() = viewModelScope.launch { repo.resetStopwatch() }

    fun sendTextProgram() = viewModelScope.launch {
        val pack = repo.sendComposedProgram(
            family = family.value,
            content = ProgramContent.Text("HELLO", speed = 3, effect = 1),
            index = 0,
            count = 1,
            showCount = 1,
            programType = if (family.value == DeviceFamily.ILEDCLOCK) 14 else null,
            extraTypeByte = 1
        )
        appendEvent("${ts()} Program queued compressed=${pack.metadata.compressedSize} chunks=${pack.metadata.chunkCount}")
    }

    fun startFakeTransfer() = viewModelScope.launch {
        val compressed = ByteArray(2500) { (it % 17).toByte() }
        val chunks = compressed.toList().chunked(1024).map { it.toByteArray() }
        transferMachine.startSession(chunks.size)
        repo.sendProgramStart(
            family.value,
            ProgramStartRequest(compressed = compressed, index = 0, count = 1, showCount = 1, programType = 14, extraTypeByte = 1)
        )
        fake.enqueueRxPayload(byteArrayOf(0x02, 0x00), "start-ack")
        chunks.forEachIndexed { idx, c ->
            repo.sendDataChunk(0x03, compressed.size, idx, c)
            fake.enqueueRxPayload(byteArrayOf(0x03, 0x00, ((idx shr 8) and 0xFF).toByte(), (idx and 0xFF).toByte(), 0x00), "chunk-ack")
        }
    }

    fun scriptTransferScenario(name: String) {
        fake.clearScripted()
        when (name) {
            "happy" -> {
                fake.enqueueRxPayload(byteArrayOf(0x02, 0x00), "start-ok")
                repeat(6) { i -> fake.enqueueRxPayload(byteArrayOf(0x03, 0x00, 0x00, i.toByte(), 0x00), "chunk-ok-$i") }
            }
            "delayed_ack" -> {
                fake.enqueueRawFrame(byteArrayOf(0x7E, 0x00, 0x7E), "unexpected-frame")
                fake.enqueueRxPayload(byteArrayOf(0x02, 0x00), "late-start-ok")
            }
            "nack_then_success" -> {
                fake.enqueueRxPayload(byteArrayOf(0x02, 0x00), "start-ok")
                fake.enqueueRxPayload(byteArrayOf(0x03, 0x00, 0x00, 0x00, 0x01), "nack-1")
                fake.enqueueRxPayload(byteArrayOf(0x03, 0x00, 0x00, 0x00, 0x01), "nack-2")
                fake.enqueueRxPayload(byteArrayOf(0x03, 0x00, 0x00, 0x00, 0x00), "finally-ok")
            }
            "retry_exhaust" -> {
                fake.enqueueRxPayload(byteArrayOf(0x02, 0x00), "start-ok")
                repeat(5) { fake.enqueueRxPayload(byteArrayOf(0x03, 0x00, 0x00, 0x00, 0x01), "nack") }
            }
            "unexpected_packet" -> fake.enqueueRawFrame(byteArrayOf(0x7E, 0x01, 0x02, 0x03), "garbage")
        }
        appendEvent("${ts()} Loaded transfer script=$name")
    }

    fun setSampleAlarm() = viewModelScope.launch {
        repo.sendAlarmList(listOf(AlarmCommand(true, 7, 30, 0b0111110, 600, 5)))
    }

    fun timeoutTransfer() {
        transferMachine.onTimeout()
        appendEvent("${ts()} Transfer timeout tick -> ${transferMachine.state.value}")
    }

    fun cancelTransfer() {
        transferMachine.cancel()
        appendEvent("${ts()} Transfer cancelled")
    }

    fun copyDebugLog(): String = events.value.joinToString("\n")

    private fun appendEvent(text: String) {
        _events.value = (listOf(text) + _events.value).take(200)
    }

    private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
    private fun ts() = System.currentTimeMillis().toString()
}
