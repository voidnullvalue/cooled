package com.cooled.core.ble

object LedScanRecordParser {
    fun parse(raw: ByteArray?): LedScanMetadata {
        if (raw == null || raw.isEmpty()) return LedScanMetadata()
        val manufacturer = extractManufacturerData(raw)
        val source = manufacturer ?: raw
        return LedScanMetadata(
            deviceId = readDeviceId(source),
            rows = readRows(source),
            columns = readColumns(source),
            colorType = readColorType(source),
            rawHex = raw.joinToString(" ") { "%02X".format(it) }
        )
    }

    private fun extractManufacturerData(raw: ByteArray): ByteArray? {
        var index = 0
        while (index < raw.size) {
            val length = raw[index].toInt() and 0xFF
            if (length == 0) break
            val typeIndex = index + 1
            val dataStart = index + 2
            val next = index + 1 + length
            if (typeIndex >= raw.size || next > raw.size) break
            val type = raw[typeIndex].toInt() and 0xFF
            if (type == 0xFF && dataStart < next) return raw.copyOfRange(dataStart, next)
            index = next
        }
        return null
    }

    private fun readDeviceId(data: ByteArray): Int? {
        if (data.size < 2) return null
        return when {
            data.size >= 8 -> u16(data, 0)
            else -> null
        }
    }

    private fun readRows(data: ByteArray): Int? = data.getOrNull(4)?.toInt()?.and(0xFF)?.takeIf { it in plausibleDimensions }
    private fun readColumns(data: ByteArray): Int? = data.getOrNull(5)?.toInt()?.and(0xFF)?.takeIf { it in plausibleDimensions }
    private fun readColorType(data: ByteArray): Int? = data.getOrNull(6)?.toInt()?.and(0xFF)?.takeIf { it in 1..8 }

    private fun u16(data: ByteArray, offset: Int): Int = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private val plausibleDimensions = setOf(8, 10, 11, 12, 16, 20, 22, 24, 32, 48, 64, 96, 128)
}
