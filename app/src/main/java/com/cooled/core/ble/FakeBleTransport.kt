package com.cooled.core.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeBleTransport : BleTransport {
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _mtu = MutableStateFlow(23)
    private val _rx = MutableStateFlow(RxFrame(byteArrayOf()))
    private val _scan = MutableStateFlow(emptyList<ScanDevice>())

    override val connectionState: Flow<ConnectionState> = _state.asStateFlow()
    override val mtu: Flow<Int> = _mtu.asStateFlow()
    override val rxFrames: Flow<RxFrame> = _rx.asStateFlow()
    override val scanResults: Flow<List<ScanDevice>> = _scan.asStateFlow()

    override fun startScan() {
        _scan.value = listOf(
            ScanDevice("CoolLEDUX_DEV", "00:11:22:33:44:55", -42),
            ScanDevice("iLedClock_01", "66:77:88:99:AA:BB", -51)
        )
    }

    override fun stopScan() = Unit

    override suspend fun connect(address: String) {
        _state.value = ConnectionState.CONNECTING
        _state.value = ConnectionState.CONNECTED
        _mtu.value = 247
        _state.value = ConnectionState.READY
    }

    override suspend fun disconnect() { _state.value = ConnectionState.DISCONNECTED }
    override suspend fun write(bytes: ByteArray) { _rx.value = RxFrame(bytes) }
}
