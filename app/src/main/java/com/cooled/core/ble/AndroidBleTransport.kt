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
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.cooled.core.assets.AssetManifestOriginalLedAssetCatalog
import com.cooled.core.assets.AssetOriginalLedAssetBytes
import com.cooled.core.assets.OriginalLedAssetByteSources
import com.cooled.core.assets.OriginalLedAssetCatalogs
import com.cooled.core.protocol.AndroidPixelGridDecoder
import com.cooled.core.protocol.AssetCoolleduxFontSource
import com.cooled.core.protocol.BleProtocolConstants
import com.cooled.core.protocol.CoolleduxFontSources
import com.cooled.core.protocol.PixelGridDecoders
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

@SuppressLint("MissingPermission")
class AndroidBleTransport(private val context: Context) : BleTransport {
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = manager?.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var gatt: BluetoothGatt? = null
    private var connectedDeviceName: String? = null
    private var isScanning = false
    private var scanWatchdog: Job? = null
    private val writeMutex = Mutex()
    @Volatile private var pendingWriteAck: CompletableDeferred<Int>? = null
    @Volatile private var pendingDescriptorWrite: CompletableDeferred<Boolean>? = null

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

    init {
        CoolleduxFontSources.active = AssetCoolleduxFontSource(context.assets)
        OriginalLedAssetCatalogs.active = AssetManifestOriginalLedAssetCatalog(context.assets)
        OriginalLedAssetByteSources.active = AssetOriginalLedAssetBytes(context.assets)
        PixelGridDecoders.active = AndroidPixelGridDecoder
    }

    /**
     * True only for callbacks belonging to the currently active connection.
     * The single [callback] instance is reused across reconnects, so without
     * this guard a callback delivered late for an already-abandoned
     * BluetoothGatt (e.g. a write ack that arrives after we gave up and
     * force-disconnected on timeout - see `write()`) could be mistaken for
     * one belonging to whatever connection is active by the time it lands.
     */
    private fun isCurrent(g: BluetoothGatt): Boolean = g === gatt

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (!isCurrent(g)) return
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _state.value = ConnectionState.CONNECTED
                if (hasConnectPermission()) g.discoverServices()
            } else {
                pendingWriteAck?.complete(status)
                pendingWriteAck = null
                pendingDescriptorWrite?.complete(false)
                pendingDescriptorWrite = null
                if (hasConnectPermission()) g.close()
                gatt = null
                connectedDeviceName = null
                _mtu.value = 23
                _rx.value = RxFrame(byteArrayOf())
                _state.value = ConnectionState.DISCONNECTED
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (!isCurrent(g)) return
            val service: BluetoothGattService = g.getService(BleProtocolConstants.serviceUuid) ?: run {
                _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "service ${BleProtocolConstants.serviceUuid} not found")
                return
            }
            val ch: BluetoothGattCharacteristic = service.getCharacteristic(BleProtocolConstants.commandCharacteristicUuid) ?: run {
                _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "characteristic ${BleProtocolConstants.commandCharacteristicUuid} not found")
                return
            }
            g.setCharacteristicNotification(ch, true)
            val cccd: BluetoothGattDescriptor? = ch.getDescriptor(BleProtocolConstants.cccdUuid)
            if (cccd == null) {
                _state.value = ConnectionState.READY
                return
            }
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            // Android's GATT client only tolerates one outstanding operation
            // at a time - firing requestMtu() immediately after writeDescriptor()
            // without waiting for onDescriptorWrite races the two operations
            // against each other (a real bug this port had). Sequence them
            // via a coroutine that awaits the descriptor-write callback.
            scope.launch {
                val descriptorAck = CompletableDeferred<Boolean>()
                pendingDescriptorWrite = descriptorAck
                val accepted = hasConnectPermission() && g.writeDescriptor(cccd)
                val wroteOk = if (accepted) withTimeoutOrNull(2500L) { descriptorAck.await() } == true else false
                pendingDescriptorWrite = null
                if (!wroteOk) {
                    _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "notification descriptor write failed or timed out")
                }
                if (isCurrent(g) && connectedDeviceName in BleProtocolConstants.mtuNegotiationNames && hasConnectPermission()) {
                    g.requestMtu(247)
                }
                if (isCurrent(g)) _state.value = ConnectionState.READY
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (!isCurrent(g)) return
            pendingDescriptorWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (!isCurrent(g)) return
            _rx.value = RxFrame(value)
            _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, value, "notify")
        }

        @Deprecated("Kept for API 24-32 callback compatibility")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (!isCurrent(g)) return
            val value = characteristic.value ?: return
            _rx.value = RxFrame(value)
            _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, value, "notify")
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (!isCurrent(g)) return
            pendingWriteAck?.complete(status)
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (!isCurrent(g)) return
            _mtu.value = mtu
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) { addScanResult(result) }
        override fun onBatchScanResults(results: MutableList<ScanResult>) { results.forEach { addScanResult(it) } }
        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            scanWatchdog?.cancel()
            _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "scan failed=$errorCode")
        }
    }

    override fun startScan() {
        if (!hasScanPermission()) {
            _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "scan skipped: missing BLUETOOTH_SCAN/location permission")
            return
        }
        if (needsLocationEnabledForScan() && !isLocationEnabled()) {
            _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "scan warning: Android may suppress BLE results while system Location is off")
        }
        val btAdapter = adapter ?: run {
            _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "scan skipped: no Bluetooth adapter")
            return
        }
        if (!btAdapter.isEnabled) {
            _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "scan skipped: Bluetooth is off")
            return
        }
        val scanner = btAdapter.bluetoothLeScanner ?: run {
            _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "scan skipped: no BLE scanner")
            return
        }

        if (isScanning) scanner.stopScan(scanCallback)
        isScanning = false
        scanWatchdog?.cancel()
        _scan.value = emptyList()

        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(0L).build()
        scanner.startScan(null, settings, scanCallback)
        isScanning = true
        _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "scan started: unfiltered BLE scan")
        scanWatchdog = scope.launch {
            delay(5000L)
            if (isScanning && _scan.value.isEmpty()) {
                _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "scan diagnostic: no BLE advertisements received after 5s; check Android Location toggle, Bluetooth, and whether the device is advertising")
            }
        }
    }

    override fun stopScan() {
        if (hasScanPermission()) adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        scanWatchdog?.cancel()
        _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "scan stopped")
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
        stopScan()
        // Close out any previous connection's GATT client before starting a
        // new one - otherwise a stale client from an unclean prior session
        // leaks (Android's per-app GATT client pool is small, and repeated
        // leaks eventually surface as status-133 GATT_ERROR on every connect).
        gatt?.close()
        gatt = null
        connectedDeviceName = d.name
        _state.value = ConnectionState.CONNECTING
        gatt = d.connectGatt(context, false, callback)
    }

    override suspend fun disconnect() {
        pendingWriteAck?.complete(BluetoothGatt.GATT_FAILURE)
        pendingWriteAck = null
        pendingDescriptorWrite?.complete(false)
        pendingDescriptorWrite = null
        if (hasConnectPermission()) {
            gatt?.disconnect()
            gatt?.close()
        }
        gatt = null
        connectedDeviceName = null
        _mtu.value = 23
        _rx.value = RxFrame(byteArrayOf())
        _state.value = ConnectionState.DISCONNECTED
    }

    override suspend fun write(bytes: ByteArray) = writeMutex.withLock {
        if (!hasConnectPermission()) {
            _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "write skipped: missing BLUETOOTH_CONNECT permission")
            return@withLock
        }
        val g = gatt ?: return@withLock
        val service = g.getService(BleProtocolConstants.serviceUuid) ?: return@withLock
        val ch = service.getCharacteristic(BleProtocolConstants.commandCharacteristicUuid) ?: return@withLock
        val writeMode = chooseWriteMode(ch)
        if (writeMode == null) {
            _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "write skipped: characteristic is not writable properties=0x${ch.properties.toString(16)}")
            return@withLock
        }

        ch.writeType = writeMode
        val expectsAck = writeMode == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val splitSize = if (_mtu.value > 23) (_mtu.value - 3).coerceAtMost(180) else 20
        val chunks = bytes.toList().chunked(splitSize).map { it.toByteArray() }
        chunks.forEachIndexed { index, chunk ->
            val ack = if (expectsAck) CompletableDeferred<Int>() else null
            pendingWriteAck = ack
            ch.value = chunk
            val modeLabel = if (expectsAck) "write" else "write-no-response"
            val note = if (chunks.size == 1) modeLabel else "$modeLabel split ${index + 1}/${chunks.size}"
            _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.TX, chunk, note)
            val accepted = g.writeCharacteristic(ch)
            if (!accepted) {
                pendingWriteAck = null
                _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "write failed to enqueue split ${index + 1}/${chunks.size} mode=$modeLabel properties=0x${ch.properties.toString(16)}")
                return@withLock
            }

            if (expectsAck) {
                val status = withTimeoutOrNull(2500L) { ack?.await() }
                pendingWriteAck = null
                if (status == null) {
                    // A genuine timeout (as opposed to an explicit non-success
                    // status) means we can no longer be sure whether the
                    // native write is still in flight - a late callback could
                    // otherwise race a subsequent write on this same
                    // connection and complete the wrong one (see PendingAck
                    // note above `callback`). Force a clean teardown instead
                    // of silently continuing; isCurrent()'s identity check
                    // then guarantees any late callback for this gatt is
                    // ignored, since a fresh connect() creates a new instance.
                    _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "write timed out split ${index + 1}/${chunks.size}; disconnecting")
                    forceDisconnectAfterTimeout(g)
                    return@withLock
                }
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "write failed split ${index + 1}/${chunks.size}: status=$status")
                    return@withLock
                }
                if (index != chunks.lastIndex) delay(25L)
            } else {
                pendingWriteAck = null
                if (index != chunks.lastIndex) delay(60L)
            }
        }
    }

    private fun forceDisconnectAfterTimeout(g: BluetoothGatt) {
        if (!isCurrent(g)) return
        if (hasConnectPermission()) {
            g.disconnect()
            g.close()
        }
        gatt = null
        connectedDeviceName = null
        _mtu.value = 23
        _rx.value = RxFrame(byteArrayOf())
        _state.value = ConnectionState.DISCONNECTED
    }

    private fun chooseWriteMode(ch: BluetoothGattCharacteristic): Int? {
        val supportsWriteNoResponse = (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
        val supportsWrite = (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
        // Matches the original app's BleConnector.writeCharacteristic: NO_RESPONSE
        // wins whenever the characteristic advertises it, even if WRITE is also
        // present - not the other way around.
        return when {
            supportsWriteNoResponse -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            supportsWrite -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            else -> null
        }
    }

    private fun addScanResult(result: ScanResult) {
        val device = result.device ?: return
        val advertisedName = result.scanRecord?.deviceName
        val bondedName = if (hasConnectPermission()) device.name else null
        val name = advertisedName ?: bondedName
        val metadata = LedScanRecordParser.parse(result.scanRecord?.bytes)
        // update{} applies its transform in a CAS retry loop, so concurrent
        // onScanResult deliveries (the platform can call back from more than
        // one thread on some OEM stacks) can't silently lose one another's
        // addition the way plain read-then-write to _scan.value could.
        _scan.update { (it + ScanDevice(name, device.address, result.rssi, metadata)).distinctBy { d -> d.address } }
        if (_scan.value.size == 1) {
            _io.value = BleIoEvent(System.currentTimeMillis(), BleIoDirection.RX, byteArrayOf(), "scan result received")
        }
    }

    private fun hasScanPermission(): Boolean {
        val scanAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else true
        val locationAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return scanAllowed && locationAllowed
    }

    private fun hasConnectPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun needsLocationEnabledForScan(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    private fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(LocationManager::class.java) ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) lm.isLocationEnabled else {
            @Suppress("DEPRECATION")
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }
}
