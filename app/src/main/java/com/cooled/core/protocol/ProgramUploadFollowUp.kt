package com.cooled.core.protocol

import com.cooled.core.model.DeviceFamily

data class ProgramUploadFollowUpCommand(
    val frame: ByteArray,
    val logLabel: String,
    val delayMs: Long = 1000L
)

object ProgramUploadFollowUp {
    /**
     * The recovered APK does not set driveState=1 after a CoolLEDUX program upload.
     * After the final package ACK it clears transfer state and refreshes device info.
     */
    fun afterSuccessfulUpload(family: DeviceFamily): ProgramUploadFollowUpCommand? = when (family) {
        DeviceFamily.COOLLEDUX,
        DeviceFamily.COOLLEDX,
        DeviceFamily.COOLLEDS -> ProgramUploadFollowUpCommand(
            frame = CommandBuilders.queryDeviceInfo(),
            logLabel = "Post-upload device-info query"
        )
        else -> null
    }
}
