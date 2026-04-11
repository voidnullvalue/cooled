package com.cooled.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooled.core.ble.ConnectionState
import com.cooled.core.ble.FakeBleTransport
import com.cooled.core.model.CapabilityMap
import com.cooled.core.model.DeviceFamily
import com.cooled.core.protocol.AlarmCommand
import com.cooled.core.protocol.ParsedPayload
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
import java.time.Instant

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
                appendEvent("RX parsed: $p")
            }
        }
    }

    fun scan() = repo.startScan()
    fun connect(address: String, name: String?) = viewModelScope.launch {
        repo.connect(address)
        val f = repo.detectFamily(name)
        family.value = f
        capabilities.value = CapabilityMap.forFamily(f)
    }

    fun queryInfo() = viewModelScope.launch { repo.sendQueryInfo() }
    fun power(on: Boolean) = viewModelScope.launch { repo.sendPower(on) }
    fun brightness(v: Int) = viewModelScope.launch { repo.sendBrightness(v) }
    fun speed(v: Int) = viewModelScope.launch { repo.sendRhythm(v) }
    fun mirror(v: Int) = viewModelScope.launch { repo.sendMirror(v) }
    fun checkPassword(p: String) = viewModelScope.launch { repo.sendCheckPassword(p) }
    fun setPassword(p: String) = viewModelScope.launch { repo.sendSetPassword(p) }

    fun syncTimeNow() = viewModelScope.launch { repo.sendTimeSync(Instant.now().epochSecond.toInt()) }
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

    fun startFakeTransfer() = viewModelScope.launch {
        val compressed = ByteArray(2500) { (it % 17).toByte() }
        val chunks = compressed.toList().chunked(1024).map { it.toByteArray() }
        transferMachine.startSession(chunks.size)
        repo.sendProgramStart(
            family.value,
            ProgramStartRequest(compressed = compressed, index = 0, count = 1, showCount = 1, programType = 14, extraTypeByte = 1)
        )
        fake.enqueueRxPayload(byteArrayOf(0x02, 0x00))
        chunks.forEachIndexed { idx, c ->
            repo.sendDataChunk(0x03, compressed.size, idx, c)
            fake.enqueueRxPayload(byteArrayOf(0x03, 0x00, ((idx shr 8) and 0xFF).toByte(), (idx and 0xFF).toByte(), 0x00))
        }
    }

    fun setSampleAlarm() = viewModelScope.launch {
        repo.sendAlarmList(listOf(AlarmCommand(true, 7, 30, 0b0111110, 600, 5)))
    }

    fun timeoutTransfer() {
        transferMachine.onTimeout()
        appendEvent("Transfer timeout tick -> ${transferMachine.state.value}")
    }

    fun cancelTransfer() {
        transferMachine.cancel()
        appendEvent("Transfer cancelled")
    }

    private fun appendEvent(text: String) {
        _events.value = (listOf(text) + _events.value).take(80)
    }
}
