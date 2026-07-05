package com.cooled.data.persistence

import android.content.Context
import com.cooled.core.ble.LedScanMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real, persistent RememberedDeviceStore backed by Android SharedPreferences
 * (plain AndroidX framework API, not a Google/cloud dependency) - the same
 * storage mechanism the original APK uses for device metadata via
 * `CoolLED.getInstance().savePreference(...)`. Each device is serialized as
 * address/name/rows/columns/colorType fields joined by U+0001, with records
 * joined by U+0002 - control characters a Bluetooth device name won't
 * plausibly contain, unlike the comma this store's earlier address-only
 * format used.
 */
class SharedPreferencesRememberedDeviceStore(context: Context) : RememberedDeviceStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _all = MutableStateFlow(readDevices())

    override fun remember(device: RememberedDevice) {
        val updated = (listOf(device) + _all.value.filterNot { it.address == device.address }).take(InMemoryRememberedDeviceStore.MAX_REMEMBERED)
        prefs.edit().putString(KEY_DEVICES, updated.joinToString(RECORD_SEPARATOR) { encode(it) }).apply()
        _all.value = updated
    }

    override fun all(): Flow<List<RememberedDevice>> = _all.asStateFlow()

    private fun encode(device: RememberedDevice): String = listOf(
        device.address,
        device.name.orEmpty(),
        device.metadata.rows?.toString().orEmpty(),
        device.metadata.columns?.toString().orEmpty(),
        device.metadata.colorType?.toString().orEmpty()
    ).joinToString(FIELD_SEPARATOR)

    private fun decode(raw: String): RememberedDevice? {
        val parts = raw.split(FIELD_SEPARATOR)
        val address = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        return RememberedDevice(
            address = address,
            name = parts.getOrNull(1)?.takeIf { it.isNotBlank() },
            metadata = LedScanMetadata(
                rows = parts.getOrNull(2)?.toIntOrNull(),
                columns = parts.getOrNull(3)?.toIntOrNull(),
                colorType = parts.getOrNull(4)?.toIntOrNull()
            )
        )
    }

    private fun readDevices(): List<RememberedDevice> {
        val stored = prefs.getString(KEY_DEVICES, null)
        if (stored != null) {
            return stored.split(RECORD_SEPARATOR).mapNotNull { if (it.isBlank()) null else decode(it) }
        }
        // Migrate the earlier address-only format so upgrading users don't
        // lose their "recently connected" list outright - name/metadata will
        // just be empty until they reconnect once via a fresh scan.
        return prefs.getString(LEGACY_KEY_ADDRESSES, null)
            ?.split(LEGACY_SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?.map { RememberedDevice(address = it) }
            ?: emptyList()
    }

    companion object {
        private const val PREFS_NAME = "cooled_remembered_devices"
        private const val KEY_DEVICES = "devices_v2"
        private const val LEGACY_KEY_ADDRESSES = "addresses"
        private const val LEGACY_SEPARATOR = ","
        // Control characters, not punctuation, so a user-set device name
        // can't plausibly collide with either delimiter. Built at runtime
        // (not a source-level \u escape) to sidestep encoding ambiguity.
        private val FIELD_SEPARATOR = 1.toChar().toString()
        private val RECORD_SEPARATOR = 2.toChar().toString()
    }
}
