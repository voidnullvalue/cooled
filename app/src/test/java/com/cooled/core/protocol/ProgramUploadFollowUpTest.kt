package com.cooled.core.protocol

import com.cooled.core.model.DeviceFamily
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgramUploadFollowUpTest {
    @Test
    fun coolLedUxPostUploadMatchesApkDeviceInfoRefreshNotDriveStateActivation() {
        val followUp = ProgramUploadFollowUp.afterSuccessfulUpload(DeviceFamily.COOLLEDUX)

        assertEquals("Post-upload device-info query", followUp!!.logLabel)
        assertArrayEquals(byteArrayOf(0x1F), FrameCodec.decode(followUp.frame))
    }

    @Test
    fun nonUxFamiliesDoNotGetPostUploadActivationCommand() {
        assertNull(ProgramUploadFollowUp.afterSuccessfulUpload(DeviceFamily.COOLLEDU))
        assertNull(ProgramUploadFollowUp.afterSuccessfulUpload(DeviceFamily.UNKNOWN))
    }
}
