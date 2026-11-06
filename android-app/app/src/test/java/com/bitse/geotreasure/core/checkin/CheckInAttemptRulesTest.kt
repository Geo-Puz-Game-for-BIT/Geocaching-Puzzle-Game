package com.bitse.geotreasure.core.checkin

import com.bitse.geotreasure.core.model.UploadState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckInAttemptRulesTest {

    @Test
    fun unknownState_isOutcomeUnknown() {
        assertTrue(CheckInAttemptRules.isOutcomeUnknown(UploadState.UNKNOWN))
        assertFalse(CheckInAttemptRules.isOutcomeUnknown(UploadState.FAILED))
        assertFalse(CheckInAttemptRules.isOutcomeUnknown(UploadState.CAPTURED))
    }

    @Test
    fun unknownState_cannotRetake() {
        assertFalse(CheckInAttemptRules.canRetake(UploadState.UNKNOWN, isProcessing = false))
    }

    @Test
    fun confirmedState_cannotRetake() {
        assertFalse(CheckInAttemptRules.canRetake(UploadState.CONFIRMED, isProcessing = false))
    }

    @Test
    fun processingState_cannotRetake() {
        assertFalse(CheckInAttemptRules.canRetake(UploadState.CAPTURED, isProcessing = true))
    }

    @Test
    fun capturedIdle_canRetake() {
        assertTrue(CheckInAttemptRules.canRetake(UploadState.CAPTURED, isProcessing = false))
    }

    @Test
    fun unknownDraft_cannotDiscard() {
        assertFalse(CheckInAttemptRules.canDiscard(UploadState.UNKNOWN))
        assertFalse(CheckInAttemptRules.canDiscard(UploadState.UPLOADING))
    }

    @Test
    fun failedDraft_canDiscard() {
        assertTrue(CheckInAttemptRules.canDiscard(UploadState.FAILED))
        assertTrue(CheckInAttemptRules.canDiscard(UploadState.CAPTURED))
    }
}
