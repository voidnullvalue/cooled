package com.cooled.core.model

data class DeviceCapabilities(
    val supportsClock: Boolean,
    val supportsScoreboard: Boolean,
    val supportsColorModes: Boolean,
    val supportsDriveState: Boolean,
    val supportsOta: Boolean
)

object CapabilityMap {
    fun forFamily(family: DeviceFamily): DeviceCapabilities = when (family) {
        DeviceFamily.COOLLEDM -> DeviceCapabilities(false, false, false, false, true)
        DeviceFamily.COOLLEDU -> DeviceCapabilities(false, false, false, false, true)
        DeviceFamily.COOLLEDUX -> DeviceCapabilities(true, true, true, true, true)
        DeviceFamily.ILEDCLOCK -> DeviceCapabilities(true, true, true, false, true)
        else -> DeviceCapabilities(false, false, false, false, false)
    }
}
