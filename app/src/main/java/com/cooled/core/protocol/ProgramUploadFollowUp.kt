package com.cooled.core.protocol

import com.cooled.core.model.DeviceFamily

data class ProgramUploadFollowUpCommand(
    val frame: ByteArray,
    val logLabel: String,
    val delayMs: Long = 1000L
)

object ProgramUploadFollowUp {
    /**
     * APK DeviceManager.checkCoolLEDUXMessages posts ResponseEvent(3) after the
     * final CoolLEDUX chunk ACK and sends no follow-up BLE frame.
     */
    fun afterSuccessfulUpload(family: DeviceFamily): ProgramUploadFollowUpCommand? = when (family) {
        DeviceFamily.COOLLEDUX -> null
        else -> null
    }
}
