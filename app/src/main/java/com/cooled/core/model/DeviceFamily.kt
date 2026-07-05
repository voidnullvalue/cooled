package com.cooled.core.model

enum class DeviceFamily {
    COOLLEDM,
    COOLLEDU,
    /** DeviceManager.Cool_LED_UD = "iLedBike" - a real, distinct family from COOLLEDU with its own CoolledUDUtils protocol class (adds drive-state support COOLLEDU doesn't have). */
    COOLLEDUD,
    COOLLEDUX,
    COOLLEDX,
    COOLLEDS,
    ILEDCLOCK,
    UNKNOWN
}

object FamilyDetector {
    private val map = linkedMapOf(
        "iLedClock" to DeviceFamily.ILEDCLOCK,
        "iLedBike" to DeviceFamily.COOLLEDUD,
        "CoolLEDUX" to DeviceFamily.COOLLEDUX,
        "CoolLEDU" to DeviceFamily.COOLLEDU,
        "CoolLEDM" to DeviceFamily.COOLLEDM,
        "CoolLEDX" to DeviceFamily.COOLLEDX,
        "CoolLEDS" to DeviceFamily.COOLLEDS,
        "CoolLED" to DeviceFamily.COOLLEDM
    )

    fun detect(name: String?): DeviceFamily =
        map.entries.firstOrNull { name?.startsWith(it.key, ignoreCase = true) == true }?.value
            ?: DeviceFamily.UNKNOWN
}
