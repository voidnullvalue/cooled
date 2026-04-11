package com.cooled.data.repositories

import com.cooled.core.ble.BleTransport
import com.cooled.core.ble.ScanDevice
import com.cooled.core.model.FamilyDetector
import com.cooled.core.protocol.CommandBuilders
import com.cooled.core.protocol.ParsedPayload
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
    suspend fun sendQueryInfo() = transport.write(CommandBuilders.queryDeviceInfo())
    suspend fun sendCheckPassword(password: String) = transport.write(CommandBuilders.checkPassword(password))
    suspend fun sendSetPassword(password: String) = transport.write(CommandBuilders.setPassword(password))

    fun detectFamily(name: String?) = FamilyDetector.detect(name)
    fun remembered() = store.all()
}
