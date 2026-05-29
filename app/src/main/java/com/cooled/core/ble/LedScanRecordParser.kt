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

    private fun readRows(data: ByteArray): Int? = firstPlausibleDimension(data, listOf(4, 5, 6, 7, 8, 9, 10))
    private fun readColumns(data: ByteArray): Int? = firstPlausibleDimension(data, listOf(5, 6, 7, 8, 9, 10, 11))
    private fun readColorType(data: ByteArray): Int? = firstPlausibleColor(data, listOf(6, 7, 8, 9, 10, 11, 12))

    private fun firstPlausibleDimension(data: ByteArray, offsets: List<Int>): Int? {
        return offsets.asSequence()
            .mapNotNull { idx -> data.getOrNull(idx)?.toInt()?.and(0xFF) }
            .firstOrNull { it in plausibleDimensions }
    }

    private fun firstPlausibleColor(data: ByteArray, offsets: List<Int>): Int? {
        return offsets.asSequence()
            .mapNotNull { idx -> data.getOrNull(idx)?.toInt()?.and(0xFF) }
            .firstOrNull { it in 1..8 }
    }

    private fun u16(data: ByteArray, offset: Int): Int = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private val plausibleDimensions = setOf(8, 10, 11, 12, 16, 20, 22, 24, 32, 48, 64, 96, 128)
}
