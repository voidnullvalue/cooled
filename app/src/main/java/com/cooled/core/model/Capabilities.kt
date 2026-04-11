package com.cooled.core.model

data class DeviceCapabilities(
    val supportsClock: Boolean,
    val supportsScoreboard: Boolean,
    val supportsColorModes: Boolean,
    val supportsDriveState: Boolean,
    val supportsOta: Boolean,
    val supportsAlarms: Boolean,
    val supportsReminders: Boolean,
    val supportsNightMode: Boolean,
    val supportsCountdown: Boolean,
    val supportsStopwatch: Boolean,
    val supportsTomato: Boolean,
    val supportsTempHumidity: Boolean,
    val supportsVolume: Boolean,
    val supportsAdvancedStartHeaders: Boolean
)

object CapabilityMap {
    fun forFamily(family: DeviceFamily): DeviceCapabilities = when (family) {
        DeviceFamily.COOLLEDM -> DeviceCapabilities(
            supportsClock = false,
            supportsScoreboard = false,
            supportsColorModes = false,
            supportsDriveState = false,
            supportsOta = true,
            supportsAlarms = false,
            supportsReminders = false,
            supportsNightMode = false,
            supportsCountdown = false,
            supportsStopwatch = false,
            supportsTomato = false,
            supportsTempHumidity = false,
            supportsVolume = false,
            supportsAdvancedStartHeaders = false
        )

        DeviceFamily.COOLLEDU -> DeviceCapabilities(
            supportsClock = false,
            supportsScoreboard = false,
            supportsColorModes = false,
            supportsDriveState = true,
            supportsOta = true,
            supportsAlarms = false,
            supportsReminders = false,
            supportsNightMode = false,
            supportsCountdown = false,
            supportsStopwatch = false,
            supportsTomato = false,
            supportsTempHumidity = false,
            supportsVolume = false,
            supportsAdvancedStartHeaders = true
        )

        DeviceFamily.COOLLEDUX -> DeviceCapabilities(
            supportsClock = true,
            supportsScoreboard = true,
            supportsColorModes = true,
            supportsDriveState = true,
            supportsOta = true,
            supportsAlarms = false,
            supportsReminders = false,
            supportsNightMode = false,
            supportsCountdown = true,
            supportsStopwatch = true,
            supportsTomato = false,
            supportsTempHumidity = false,
            supportsVolume = true,
            supportsAdvancedStartHeaders = true
        )

        DeviceFamily.ILEDCLOCK -> DeviceCapabilities(
            supportsClock = true,
            supportsScoreboard = true,
            supportsColorModes = true,
            supportsDriveState = false,
            supportsOta = true,
            supportsAlarms = true,
            supportsReminders = true,
            supportsNightMode = true,
            supportsCountdown = true,
            supportsStopwatch = true,
            supportsTomato = true,
            supportsTempHumidity = true,
            supportsVolume = true,
            supportsAdvancedStartHeaders = true
        )

        DeviceFamily.COOLLEDX,
        DeviceFamily.COOLLEDS -> DeviceCapabilities(
            supportsClock = false,
            supportsScoreboard = false,
            supportsColorModes = true,
            supportsDriveState = false,
            supportsOta = true,
            supportsAlarms = false,
            supportsReminders = false,
            supportsNightMode = false,
            supportsCountdown = false,
            supportsStopwatch = false,
            supportsTomato = false,
            supportsTempHumidity = false,
            supportsVolume = false,
            supportsAdvancedStartHeaders = true
        )

        DeviceFamily.UNKNOWN -> DeviceCapabilities(
            supportsClock = false,
            supportsScoreboard = false,
            supportsColorModes = false,
            supportsDriveState = false,
            supportsOta = false,
            supportsAlarms = false,
            supportsReminders = false,
            supportsNightMode = false,
            supportsCountdown = false,
            supportsStopwatch = false,
            supportsTomato = false,
            supportsTempHumidity = false,
            supportsVolume = false,
            supportsAdvancedStartHeaders = false
        )
    }
}
