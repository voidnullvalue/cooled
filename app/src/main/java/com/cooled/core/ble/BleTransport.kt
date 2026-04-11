package com.cooled.core.ble

import kotlinx.coroutines.flow.Flow

data class ScanDevice(val name: String?, val address: String, val rssi: Int)

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, READY }

data class RxFrame(val bytes: ByteArray)

interface BleTransport {
    val connectionState: Flow<ConnectionState>
    val mtu: Flow<Int>
    val rxFrames: Flow<RxFrame>
    fun startScan()
    fun stopScan()
    val scanResults: Flow<List<ScanDevice>>
    suspend fun connect(address: String)
    suspend fun disconnect()
    suspend fun write(bytes: ByteArray)
}
