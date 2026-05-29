package com.cooled.core.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.cooled.core.protocol.BleProtocolConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@SuppressLint("MissingPermission")
class AndroidBleTransport(private val context: Context) : BleTransport {
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = manager?.adapter
    private var gatt: BluetoothGatt? = null

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _mtu = MutableStateFlow(23)
    private val _rx = MutableStateFlow(RxFrame(byteArrayOf()))
    private val _scan = MutableStateFlow(emptyList<ScanDevice>())
    private val _io = MutableStateFlow(BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "init"))

    override val connectionState: Flow<ConnectionState> = _state.asStateFlow()
    override val mtu: Flow<Int> = _mtu.asStateFlow()
    override val rxFrames: Flow<RxFrame> = _rx.asStateFlow()
    override val scanResults: Flow<List<ScanDevice>> = _scan.asStateFlow()
    override val ioEvents: Flow<BleIoEvent> = _io.asStateFlow()

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                _state.value = ConnectionState.CONNECTED
                if (hasConnectPermission()) g.discoverServices()
            } else {
                _state.value = ConnectionState.DISCONNECTED
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service: BluetoothGattService = g.getService(BleProtocolConstants.serviceUuid) ?: return
            val ch: BluetoothGattCharacteristic = service.getCharacteristic(BleProtocolConstants.commandCharacteristicUuid) ?: return
            g.setCharacteristicNotification(ch, true)
            val cccd: BluetoothGattDescriptor? = ch.getDescriptor(BleProtocolConstants.cccdUuid)
            cccd?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            cccd?.let { g.writeDescriptor(it) }
            g.requestMtu(247)
            _state.value = ConnectionState.READY
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            _rx.value = RxFrame(value)
            _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, value, "notify")
        }

        @Deprecated("Kept for API 24-32 callback compatibility")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val value = characteristic.value ?: return
            _rx.value = RxFrame(value)
            _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, value, "notify")
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) { _mtu.value = mtu }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val d = result.device ?: return
            val name = if (hasConnectPermission()) d.name else null
            _scan.value = (_scan.value + ScanDevice(name, d.address, result.rssi)).distinctBy { it.address }
        }

        override fun onScanFailed(errorCode: Int) {
            _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "scan failed=$errorCode")
        }
    }

    override fun startScan() {
        if (!hasScanPermission()) {
            _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "scan skipped: missing BLUETOOTH_SCAN/location permission")
            return
        }
        val scanner = adapter?.bluetoothLeScanner ?: run {
            _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "scan skipped: no BLE scanner")
            return
        }
        val filter = ScanFilter.Builder().setServiceUuid(android.os.ParcelUuid(BleProtocolConstants.serviceUuid)).build()
        scanner.startScan(listOf(filter), ScanSettings.Builder().build(), scanCallback)
    }

    override fun stopScan() {
        if (hasScanPermission()) adapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    override suspend fun connect(address: String) {
        if (!hasConnectPermission()) {
            _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "connect skipped: missing BLUETOOTH_CONNECT permission")
            return
        }
        val d: BluetoothDevice = adapter?.getRemoteDevice(address) ?: run {
            _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "connect skipped: no adapter/device")
            return
        }
        _state.value = ConnectionState.CONNECTING
        gatt = d.connectGatt(context, false, callback)
    }

    override suspend fun disconnect() {
        if (hasConnectPermission()) {
            gatt?.disconnect()
            gatt?.close()
        }
        gatt = null
        _state.value = ConnectionState.DISCONNECTED
    }

    override suspend fun write(bytes: ByteArray) {
        if (!hasConnectPermission()) {
            _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "write skipped: missing BLUETOOTH_CONNECT permission")
            return
        }
        val g = gatt ?: return
        val service = g.getService(BleProtocolConstants.serviceUuid) ?: return
        val ch = service.getCharacteristic(BleProtocolConstants.commandCharacteristicUuid) ?: return
        ch.value = bytes
        _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.TX, bytes, "write")
        g.writeCharacteristic(ch)
    }

    private fun hasScanPermission(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasConnectPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}