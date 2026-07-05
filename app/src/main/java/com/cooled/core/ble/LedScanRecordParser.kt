package com.cooled.core.ble

/**
 * Port of DeviceManager.getDeviceId/getDeviceRow/getDeviceColumn/getDeviceColorTye
 * (reverse/jadx/sources/com/jtkj/led1248/light/device/DeviceManager.java:10597-10623).
 *
 * These index directly into the *raw* BLE advertisement byte array at fixed
 * offsets - there is no manufacturer-specific-data (AD type 0xFF) extraction
 * step in the original at all, despite an earlier version of this port
 * guessing one. Confirmed against the original source, not just the earlier
 * unverified layout:
 *  - deviceId = (raw[10] << 8) | raw[9]  (each byte hex-formatted with
 *    LightUtils.getHexStringForInt, which always zero-pads to 2 hex digits,
 *    then the two strings are concatenated and parsed back as one base-16
 *    number - arithmetically identical to a 16-bit big-endian-of-those-two-
 *    bytes read, just computed via string ops in the original)
 *  - row = raw[17]                       (single byte, not concatenated)
 *  - column = (raw[18] << 8) | raw[19]   (same two-byte concatenation as deviceId)
 *  - colorType = raw[20]                 (single byte)
 */
object LedScanRecordParser {
    fun parse(raw: ByteArray?): LedScanMetadata {
        if (raw == null || raw.isEmpty()) return LedScanMetadata()
        if (raw.size <= 20) return LedScanMetadata(rawHex = raw.toHex())
        return LedScanMetadata(
            deviceId = u16(raw, high = 10, low = 9),
            rows = byteAt(raw, 17),
            columns = u16(raw, high = 18, low = 19),
            colorType = byteAt(raw, 20),
            rawHex = raw.toHex()
        )
    }

    private fun byteAt(data: ByteArray, index: Int): Int? = data.getOrNull(index)?.toInt()?.and(0xFF)

    private fun u16(data: ByteArray, high: Int, low: Int): Int? {
        val h = data.getOrNull(high)?.toInt()?.and(0xFF) ?: return null
        val l = data.getOrNull(low)?.toInt()?.and(0xFF) ?: return null
        return (h shl 8) or l
    }

    private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
}
