package com.bitse.geotreasure.core.checkin

import com.bitse.geotreasure.core.model.UploadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckInFlowRulesTest {

    // ---------- F1：提交入口分流（先已发出/未知 → RECOVER，再明确失败 → NEW_ATTEMPT） ----------

    @Test
    fun submitRoute_unknownMissingPhoto_recovers() {
        // UNKNOWN（即使照片缺失，requestSent=true 也会进 RECOVER）
        assertEquals(
            SubmitRoute.RECOVER,
            CheckInFlowRules.submitRoute(UploadState.UNKNOWN, requestSent = true, isProcessing = false),
        )
    }

    @Test
    fun submitRoute_unknownNoRequestStillRecovers() {
        // 防御：即使 requestSent 未标记，UNKNOWN 也必须走查询恢复而非首提
        assertEquals(
            SubmitRoute.RECOVER,
            CheckInFlowRules.submitRoute(UploadState.UNKNOWN, requestSent = false, isProcessing = false),
        )
    }

    @Test
    fun submitRoute_failedAfterSent_newAttempt() {
        // 明确失败（已发出且被明确拒绝）→ 换新尝试
        assertEquals(
            SubmitRoute.NEW_ATTEMPT,
            CheckInFlowRules.submitRoute(UploadState.FAILED, requestSent = true, isProcessing = false),
        )
    }

    @Test
    fun submitRoute_failedBeforeSent_firstSubmit() {
        // 照片上传失败（未发出）→ 同一 attempt 重试首提
        assertEquals(
            SubmitRoute.FIRST_SUBMIT,
            CheckInFlowRules.submitRoute(UploadState.FAILED, requestSent = false, isProcessing = false),
        )
    }

    @Test
    fun submitRoute_capturedIdle_firstSubmit() {
        assertEquals(
            SubmitRoute.FIRST_SUBMIT,
            CheckInFlowRules.submitRoute(UploadState.CAPTURED, requestSent = false, isProcessing = false),
        )
    }

    @Test
    fun submitRoute_confirmedOrProcessing_return() {
        assertEquals(
            SubmitRoute.RETURN,
            CheckInFlowRules.submitRoute(UploadState.CONFIRMED, requestSent = true, isProcessing = false),
        )
        assertEquals(
            SubmitRoute.RETURN,
            CheckInFlowRules.submitRoute(UploadState.CAPTURED, requestSent = false, isProcessing = true),
        )
    }

    // ---------- F1：恢复草稿无条件恢复 uploadState，照片缺失仅影响预览 ----------

    @Test
    fun recoverDraft_unknownMissingPhoto_stillUnknown() {
        // F1 回归：UNKNOWN 草稿 + 照片缺失 → uploadState 仍 UNKNOWN（不得回退 EMPTY），且标记已发出
        val patch = CheckInFlowRules.buildRecoveredDraftPatch(
            uploadState = UploadState.UNKNOWN,
            localPhotoPath = "/tmp/gone.jpg",
            photoExists = false,
        )
        assertEquals(UploadState.UNKNOWN, patch.uploadState)
        assertNull(patch.photoPath)
        assertTrue(patch.markRequestSent)
        assertTrue(patch.restoredDraft)
    }

    @Test
    fun recoverDraft_unknownWithPhoto_keepsPhoto() {
        val patch = CheckInFlowRules.buildRecoveredDraftPatch(
            uploadState = UploadState.UNKNOWN,
            localPhotoPath = "/tmp/p.jpg",
            photoExists = true,
        )
        assertEquals(UploadState.UNKNOWN, patch.uploadState)
        assertEquals("/tmp/p.jpg", patch.photoPath)
        assertTrue(patch.markRequestSent)
    }

    @Test
    fun recoverDraft_failedMissingPhoto_stillFailed() {
        val patch = CheckInFlowRules.buildRecoveredDraftPatch(
            uploadState = UploadState.FAILED,
            localPhotoPath = "/tmp/gone.jpg",
            photoExists = false,
        )
        assertEquals(UploadState.FAILED, patch.uploadState)
        assertNull(patch.photoPath)
        assertFalse(patch.markRequestSent)
    }

    @Test
    fun recoverDraft_emptyWithPhoto_noError() {
        val patch = CheckInFlowRules.buildRecoveredDraftPatch(
            uploadState = UploadState.EMPTY,
            localPhotoPath = "/tmp/p.jpg",
            photoExists = true,
        )
        assertEquals(UploadState.EMPTY, patch.uploadState)
        assertEquals("/tmp/p.jpg", patch.photoPath)
        assertNull(patch.errorMessage)
        assertFalse(patch.markRequestSent)
    }
}
