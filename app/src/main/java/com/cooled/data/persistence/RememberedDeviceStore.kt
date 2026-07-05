package com.cooled.data.persistence

import com.cooled.core.ble.LedScanMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A remembered device: address plus the last-known name and scan-derived
 * matrix metadata. Both are required for a quick-reconnect (bypassing a
 * fresh BLE scan) to behave identically to a scan-based connect - without a
 * name, FamilyDetector.detect(null) falls back to DeviceFamily.UNKNOWN,
 * which routes every subsequent send through the unverified byte-placeholder
 * encoder instead of the real per-family pipeline; without matrix metadata,
 * text/asset content gets built for the wrong canvas size. This used to be
 * lost on every quick-reconnect (see AppViewModel's old connect(), which
 * only looked name/metadata up from the *current* scan results list - always
 * empty for exactly the devices this shortcut exists for).
 */
data class RememberedDevice(
    val address: String,
    val name: String? = null,
    val metadata: LedScanMetadata = LedScanMetadata()
)

/**
 * Tracks devices the user has connected to, most-recent first, for a
 * "recently connected" shortcut in the connect screen. The original APK
 * persists device metadata the same way (`CoolLED.getInstance().
 * savePreference(...)`, backed by Android SharedPreferences) rather than
 * keeping it in memory only - this interface exists so the real
 * (SharedPreferences-backed) and fake/test (in-memory) implementations can
 * be swapped the same way BleTransport/CoolleduxFontSource are elsewhere in
 * this codebase.
 */
interface RememberedDeviceStore {
    fun remember(device: RememberedDevice)
    fun all(): Flow<List<RememberedDevice>>
}

/** In-memory default: does not survive process death. Used for tests and the fake/demo transport, where nothing needs to persist across runs. */
class InMemoryRememberedDeviceStore : RememberedDeviceStore {
    private val remembered = MutableStateFlow<List<RememberedDevice>>(emptyList())
    override fun remember(device: RememberedDevice) {
        remembered.value = (listOf(device) + remembered.value.filterNot { it.address == device.address }).take(MAX_REMEMBERED)
    }
    override fun all(): Flow<List<RememberedDevice>> = remembered.asStateFlow()

    companion object {
        const val MAX_REMEMBERED = 10
    }
}
