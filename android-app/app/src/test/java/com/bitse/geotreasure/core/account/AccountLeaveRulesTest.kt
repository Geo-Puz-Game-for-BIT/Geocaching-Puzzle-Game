package com.bitse.geotreasure.core.account

import com.bitse.geotreasure.core.model.CheckInDraft
import com.bitse.geotreasure.core.model.UploadState
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 退出前封存决策纯规则测试：
 * - 结果未知（requestSent / UNKNOWN / UPLOADING）→ 必须封存，避免「服务端可能已成功」静默丢失；
 * - 明确未成功且未发送（CAPTURED / UPLOADED / FAILED 且 !requestSent）→ 可确认终态，安全退出；
 * - 已 CONFIRMED / EMPTY → 无需封存。
 */
class AccountLeaveRulesTest {

    private val now: Instant = Instant.parse("2024-01-01T00:00:00Z")

    private fun draft(
        uploadState: UploadState,
        requestSent: Boolean = false,
        id: String = "att_1",
        landmarkId: String = "lmk_1",
    ): CheckInDraft = CheckInDraft(
        id = id,
        landmarkId = landmarkId,
        localPhoto = null,
        uploadedPhotoId = null,
        challengeId = null,
        challengeExpiresAt = null,
        frozenLocation = null,
        uploadState = uploadState,
        createdAt = now,
        updatedAt = now,
        requestSent = requestSent,
    )

    @Test
    fun `无草稿无需封存`() {
        assertFalse(AccountLeaveRules.needsSeal(null))
    }

    @Test
    fun `EMPTY 与 CONFIRMED 无需封存`() {
        assertFalse(AccountLeaveRules.needsSeal(draft(UploadState.EMPTY)))
        assertFalse(AccountLeaveRules.needsSeal(draft(UploadState.CONFIRMED)))
    }

    @Test
    fun `明确未成功且未发送无需封存`() {
        assertFalse(AccountLeaveRules.needsSeal(draft(UploadState.CAPTURED)))
        assertFalse(AccountLeaveRules.needsSeal(draft(UploadState.UPLOADED)))
        assertFalse(AccountLeaveRules.needsSeal(draft(UploadState.FAILED, requestSent = false)))
    }

    @Test
    fun `结果未知或已发送必须封存`() {
        assertTrue(AccountLeaveRules.needsSeal(draft(UploadState.UNKNOWN)))
        assertTrue(AccountLeaveRules.needsSeal(draft(UploadState.UPLOADING)))
        assertTrue(AccountLeaveRules.needsSeal(draft(UploadState.FAILED, requestSent = true)))
        assertTrue(AccountLeaveRules.needsSeal(draft(UploadState.UPLOADED, requestSent = true)))
    }

    @Test
    fun `classify 区分 NoPending 与 ConfirmedTerminal`() {
        assertEquals(AccountLeaveClassification.NoPending, AccountLeaveRules.classify(null, "a", now))
        assertEquals(AccountLeaveClassification.NoPending, AccountLeaveRules.classify(draft(UploadState.EMPTY), "a", now))
        assertEquals(
            AccountLeaveClassification.ConfirmedTerminal,
            AccountLeaveRules.classify(draft(UploadState.CONFIRMED), "a", now),
        )
        assertEquals(
            AccountLeaveClassification.ConfirmedTerminal,
            AccountLeaveRules.classify(draft(UploadState.CAPTURED), "a", now),
        )
    }

    @Test
    fun `classify 对未知结果返回 PreSeal`() {
        val classified = AccountLeaveRules.classify(draft(UploadState.UNKNOWN), "a", now)
        assertTrue(classified is AccountLeaveClassification.PreSeal)
        val seal = (classified as AccountLeaveClassification.PreSeal).seal
        assertEquals("a", seal.accountId)
        assertEquals("att_1", seal.attemptId)
        assertEquals("lmk_1", seal.landmarkId)
        assertEquals("UNKNOWN", seal.uploadStateName)
    }

    @Test
    fun `buildSeal 映射草稿关键字段`() {
        val d = draft(UploadState.UNKNOWN, requestSent = true, id = "att_x", landmarkId = "lmk_x")
        val seal = AccountLeaveRules.buildSeal(d, "acc_9", now)
        assertEquals("acc_9", seal.accountId)
        assertEquals("att_x", seal.attemptId)
        assertEquals("lmk_x", seal.landmarkId)
        assertTrue(seal.requestSent)
        assertEquals("UNKNOWN", seal.uploadStateName)
        assertEquals(now, seal.sealedAt)
    }
}
