package com.cooled.core.ble

import com.cooled.core.model.DeviceFamily
import com.cooled.core.model.FamilyDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LedScanRecordParserAndFamilyDetectorTest {
    @Test
    fun parsesManufacturerDataFromBleAdvertisement() {
        val manufacturer = byteArrayOf(
            0x12,
            0x34,
            0x00,
            0x00,
            32,
            128.toByte(),
            3,
            0x00
        )
        val raw = byteArrayOf(
            0x02,
            0x01,
            0x06,
            0x09,
            0xFF.toByte()
        ) + manufacturer

        val metadata = LedScanRecordParser.parse(raw)

        assertEquals(0x1234, metadata.deviceId)
        assertEquals(32, metadata.rows)
        assertEquals(128, metadata.columns)
        assertEquals(3, metadata.colorType)
        assertEquals(raw.joinToString(" ") { "%02X".format(it) }, metadata.rawHex)
    }

    @Test
    fun fallsBackToRawRecordWhenManufacturerDataIsAbsent() {
        val raw = byteArrayOf(
            0x12,
            0x34,
            0x00,
            0x00,
            16,
            64,
            2,
            0x00
        )

        val metadata = LedScanRecordParser.parse(raw)

        assertEquals(0x1234, metadata.deviceId)
        assertEquals(16, metadata.rows)
        assertEquals(64, metadata.columns)
        assertEquals(2, metadata.colorType)
    }

    @Test
    fun malformedRecordDoesNotInventMetadata() {
        val metadata = LedScanRecordParser.parse(byteArrayOf(0x09, 0xFF.toByte(), 0x12))

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
