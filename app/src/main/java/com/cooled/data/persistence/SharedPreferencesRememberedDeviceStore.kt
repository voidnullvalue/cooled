package com.cooled.data.persistence

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real, persistent RememberedDeviceStore backed by Android SharedPreferences
 * (plain AndroidX framework API, not a Google/cloud dependency) - the same
 * storage mechanism the original APK uses for device metadata via
 * `CoolLED.getInstance().savePreference(...)`. Addresses are stored as a
 * single delimiter-joined string (SharedPreferences has no native ordered-
 * list type); a comma is safe since BLE MAC addresses only ever contain hex
 * digits and colons.
 */
class SharedPreferencesRememberedDeviceStore(context: Context) : RememberedDeviceStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _all = MutableStateFlow(readAddresses())

    override fun remember(address: String) {
        val updated = (listOf(address) + _all.value.filterNot { it == address }).take(InMemoryRememberedDeviceStore.MAX_REMEMBERED)
        prefs.edit().putString(KEY_ADDRESSES, updated.joinToString(SEPARATOR)).apply()
        _all.value = updated
    }

    override fun all(): Flow<List<String>> = _all.asStateFlow()

    private fun readAddresses(): List<String> =
        prefs.getString(KEY_ADDRESSES, null)?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()

    companion object {
        private const val PREFS_NAME = "cooled_remembered_devices"
        private const val KEY_ADDRESSES = "addresses"
        private const val SEPARATOR = ","
    }
}
