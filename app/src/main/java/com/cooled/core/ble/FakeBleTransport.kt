package com.cooled.core.ble

import com.cooled.core.protocol.FrameCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeBleTransport : BleTransport {
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _mtu = MutableStateFlow(23)
    private val _rx = MutableStateFlow(RxFrame(byteArrayOf()))
    private val _scan = MutableStateFlow(emptyList<ScanDevice>())
    private val _io = MutableStateFlow(BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "init"))

    private val scriptedResponses = ArrayDeque<ScriptedRx>()

    override val connectionState: Flow<ConnectionState> = _state.asStateFlow()
    override val mtu: Flow<Int> = _mtu.asStateFlow()
    override val rxFrames: Flow<RxFrame> = _rx.asStateFlow()
    override val scanResults: Flow<List<ScanDevice>> = _scan.asStateFlow()
    override val ioEvents: Flow<BleIoEvent> = _io.asStateFlow()

    override fun startScan() {
        _scan.value = listOf(
            ScanDevice("CoolLEDUX_DEV", "00:11:22:33:44:55", -42),
            ScanDevice("iLedClock_01", "66:77:88:99:AA:BB", -51),
            ScanDevice("CoolLEDX_02", "11:22:33:44:55:66", -49),
            ScanDevice("CoolLEDS_03", "22:33:44:55:66:77", -50)
        )
    }

    override fun stopScan() = Unit

    override suspend fun connect(address: String) {
        _state.value = ConnectionState.CONNECTING
        _state.value = ConnectionState.CONNECTED
        _mtu.value = 247
        _state.value = ConnectionState.READY
    }

    override suspend fun disconnect() {
        _state.value = ConnectionState.DISCONNECTED
    }

    override suspend fun write(bytes: ByteArray) {
        _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.TX, bytes, "write")
        val scripted = scriptedResponses.removeFirstOrNull()
        val toEmit = scripted?.frame ?: bytes
        _rx.value = RxFrame(toEmit)
        _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, toEmit, scripted?.note ?: "loopback")
    }

    fun enqueueRxPayload(payload: ByteArray, note: String? = null) {
        scriptedResponses.addLast(ScriptedRx(FrameCodec.encode(payload), note))
    }

    fun enqueueRawFrame(frame: ByteArray, note: String? = null) {
        scriptedResponses.addLast(ScriptedRx(frame, note))
    }

    fun clearScripted() = scriptedResponses.clear()

    private data class ScriptedRx(val frame: ByteArray, val note: String?)
}
