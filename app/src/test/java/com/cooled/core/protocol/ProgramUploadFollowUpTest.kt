package com.cooled.core.protocol

import com.cooled.core.model.DeviceFamily
import org.junit.Assert.assertNull
import org.junit.Test

class ProgramUploadFollowUpTest {
    @Test
    fun coolLedUxPostUploadMatchesApkFinalAckSuccessEventOnly() {
        assertNull(ProgramUploadFollowUp.afterSuccessfulUpload(DeviceFamily.COOLLEDUX))
    }

    @Test
    fun nonUxFamiliesDoNotGetPostUploadActivationCommand() {
        assertNull(ProgramUploadFollowUp.afterSuccessfulUpload(DeviceFamily.COOLLEDU))
        assertNull(ProgramUploadFollowUp.afterSuccessfulUpload(DeviceFamily.UNKNOWN))
    }
}
