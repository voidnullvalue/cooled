package com.cooled.data.persistence

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks BLE addresses the user has connected to, most-recent first, for a
 * "recently connected" shortcut in the connect screen. The original APK
 * persists device metadata the same way (`CoolLED.getInstance().
 * savePreference(...)`, backed by Android SharedPreferences) rather than
 * keeping it in memory only - this interface exists so the real
 * (SharedPreferences-backed) and fake/test (in-memory) implementations can
 * be swapped the same way BleTransport/CoolleduxFontSource are elsewhere in
 * this codebase.
 */
interface RememberedDeviceStore {
    fun remember(address: String)
    fun all(): Flow<List<String>>
}

/** In-memory default: does not survive process death. Used for tests and the fake/demo transport, where nothing needs to persist across runs. */
class InMemoryRememberedDeviceStore : RememberedDeviceStore {
    private val remembered = MutableStateFlow<List<String>>(emptyList())
    override fun remember(address: String) {
        remembered.value = (listOf(address) + remembered.value.filterNot { it == address }).take(MAX_REMEMBERED)
    }
    override fun all(): Flow<List<String>> = remembered.asStateFlow()

    companion object {
        const val MAX_REMEMBERED = 10
    }
}
