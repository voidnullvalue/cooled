package com.cooled.core.ble

import com.cooled.core.model.DeviceFamily
import com.cooled.core.model.FamilyDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LedScanRecordParserAndFamilyDetectorTest {
    /**
     * Builds a fake raw advertisement byte array long enough to exercise
     * DeviceManager.getDeviceId/getDeviceRow/getDeviceColumn/getDeviceColorTye's
     * real, verified offsets: deviceId = (raw[10]&lt;&lt;8)|raw[9], row = raw[17],
     * column = (raw[18]&lt;&lt;8)|raw[19], colorType = raw[20]. Bytes 0-8 are
     * arbitrary AD-structure filler the original ignores for this purpose.
     */
    private fun rawRecord(deviceIdHi: Int, deviceIdLo: Int, row: Int, columnHi: Int, columnLo: Int, colorType: Int): ByteArray {
        val raw = ByteArray(21)
        raw[9] = deviceIdLo.toByte()
        raw[10] = deviceIdHi.toByte()
        raw[17] = row.toByte()
        raw[18] = columnHi.toByte()
        raw[19] = columnLo.toByte()
        raw[20] = colorType.toByte()
        return raw
    }

    @Test
    fun parsesDeviceIdRowColumnColorTypeAtTheirRealFixedOffsets() {
        val raw = rawRecord(deviceIdHi = 0x12, deviceIdLo = 0x34, row = 32, columnHi = 0x00, columnLo = 128, colorType = 3)

        val metadata = LedScanRecordParser.parse(raw)

        assertEquals(0x1234, metadata.deviceId)
        assertEquals(32, metadata.rows)
        assertEquals(128, metadata.columns)
        assertEquals(3, metadata.colorType)
        assertEquals(raw.joinToString(" ") { "%02X".format(it) }, metadata.rawHex)
    }

    @Test
    fun columnIsATwoByteBigEndianFieldUnlikeTheSingleByteRow() {
        // column = 0x0140 = 320, spanning raw[18]/raw[19]; row stays a single byte at raw[17].
        val raw = rawRecord(deviceIdHi = 0, deviceIdLo = 0, row = 16, columnHi = 0x01, columnLo = 0x40, colorType = 1)

        val metadata = LedScanRecordParser.parse(raw)

        assertEquals(16, metadata.rows)
        assertEquals(320, metadata.columns)
    }

    @Test
    fun recordTooShortToReachColorTypeOffsetReturnsAllNulls() {
        // Real parsing needs indices up to 20; anything shorter can't safely read colorType.
        val metadata = LedScanRecordParser.parse(ByteArray(20))

        assertNull(metadata.deviceId)
        assertNull(metadata.rows)
        assertNull(metadata.columns)
        assertNull(metadata.colorType)
    }

    @Test
    fun nullOrEmptyRecordReturnsEmptyMetadata() {
        val nullMetadata = LedScanRecordParser.parse(null)
        val emptyMetadata = LedScanRecordParser.parse(byteArrayOf())

        assertNull(nullMetadata.deviceId)
        assertNull(nullMetadata.rows)
        assertNull(nullMetadata.columns)
        assertNull(nullMetadata.colorType)
        assertNull(nullMetadata.rawHex)

        assertNull(emptyMetadata.deviceId)
        assertNull(emptyMetadata.rows)
        assertNull(emptyMetadata.columns)
        assertNull(emptyMetadata.colorType)
        assertNull(emptyMetadata.rawHex)
    }

    @Test
    fun familyDetectorMatchesKnownPrefixesCaseInsensitively() {
        assertEquals(DeviceFamily.ILEDCLOCK, FamilyDetector.detect("iLedClock-123"))
        assertEquals(DeviceFamily.COOLLEDUD, FamilyDetector.detect("iLedBike-42"))
        assertEquals(DeviceFamily.COOLLEDUX, FamilyDetector.detect("coolledux_panel"))
        assertEquals(DeviceFamily.COOLLEDU, FamilyDetector.detect("CoolLEDU_01"))
        assertEquals(DeviceFamily.COOLLEDM, FamilyDetector.detect("CoolLEDM thing"))
        assertEquals(DeviceFamily.COOLLEDX, FamilyDetector.detect("CoolLEDX-foo"))
        assertEquals(DeviceFamily.COOLLEDS, FamilyDetector.detect("CoolLEDS-foo"))
    }

    @Test
    fun familyDetectorFallsBackToUnknown() {
        assertEquals(DeviceFamily.UNKNOWN, FamilyDetector.detect(null))
        assertEquals(DeviceFamily.UNKNOWN, FamilyDetector.detect("SomeOtherDevice"))
    }
}
