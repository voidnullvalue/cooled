package com.cooled.core.logging

import android.util.Log

object BleLog {
    private const val TAG = "CoolLEDReimpl"
    var enabled: Boolean = true

    fun tx(bytes: ByteArray, label: String = "TX") {
        if (enabled) Log.d(TAG, "$label ${bytes.joinToString(" ") { "%02X".format(it) }}")
    }

    fun rx(bytes: ByteArray, label: String = "RX") {
        if (enabled) Log.d(TAG, "$label ${bytes.joinToString(" ") { "%02X".format(it) }}")
    }
}
