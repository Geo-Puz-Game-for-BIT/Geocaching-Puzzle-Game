package com.bitse.geotreasure.core.account

import com.bitse.geotreasure.core.model.CheckInDraft
import com.bitse.geotreasure.core.model.LocationSample
import com.bitse.geotreasure.core.model.UploadState
import kotlinx.datetime.Instant

/**
 * 退出前的打卡封存记录：保留「服务端可能已成功、客户端尚未恢复」的关键证据，
 * 供旧账号重新有效登录后按稳定 attemptId 查询恢复，避免静默丢失。
 *
 * 封存记录只属于原账号；旧账号重新登录后用新会话查询其自己的原 attempt，
 * 取得可靠终态后按原规则更新和清理。
 */
data class CheckInSeal(
    val accountId: String,
    val attemptId: String,
    val landmarkId: String,
    val requestSent: Boolean,
    val uploadStateName: String,
    val photoId: String?,
    val photoPath: String?,
    val challengeId: String?,
    val frozenLocation: LocationSample?,
    /** 已取得的成功回执（若已恢复），否则 null。 */
    val knownCheckInId: String?,
    val sealedAt: Instant,
)

/** 退出前 `PrepareAccountLeave` 的结论。 */
sealed interface PrepareAccountLeaveResult {
    /** 无待确认业务，可直接进入远程退出。 */
    data object NoPending : PrepareAccountLeaveResult

    /** 已确认终态（成功已落盘 / 明确未成功），可安全退出。 */
    data object ConfirmedTerminal : PrepareAccountLeaveResult

    /** 存在待确认打卡，已持久化封存，可进入远程退出；旧号重登后恢复。 */
    data class Sealed(val seal: CheckInSeal) : PrepareAccountLeaveResult

    /** 封存失败（无法持久化关键证据），必须阻止删除关键证据并说明原因，不得继续联网写。 */
    data class SealFailed(val reason: String, val pendingSeal: CheckInSeal) : PrepareAccountLeaveResult
}

/**
 * 退出前决策纯规则（JVM 可测）。
 * 语义：
 * - 结果未知（requestSent 或 UNKNOWN / UPLOADING）→ 可能已成功，必须封存；
 * - 明确未成功且未发送（FAILED 且 !requestSent、CAPTURED、UPLOADED 未发送）→ 可确认终态；
 * - 已 CONFIRMED / EMPTY → 无需封存。
 */
object AccountLeaveRules {

    /** 该草稿是否存在「服务端可能已成功但客户端未确认」的风险，退出前必须封存。 */
    fun needsSeal(draft: CheckInDraft?): Boolean {
        if (draft == null) return false
        if (draft.uploadState == UploadState.CONFIRMED) return false
        if (draft.uploadState == UploadState.EMPTY) return false
        if (draft.requestSent) return true
        return draft.uploadState == UploadState.UNKNOWN || draft.uploadState == UploadState.UPLOADING
    }

    /** 构造封存记录（调用方先持久化成功后，才允许进入远程退出）。 */
    fun buildSeal(draft: CheckInDraft, accountId: String, now: Instant): CheckInSeal = CheckInSeal(
        accountId = accountId,
        attemptId = draft.id,
        landmarkId = draft.landmarkId,
        requestSent = draft.requestSent,
        uploadStateName = draft.uploadState.name,
        photoId = draft.uploadedPhotoId,
        photoPath = draft.localPhoto?.absolutePath,
        challengeId = draft.challengeId,
        frozenLocation = draft.frozenLocation,
        knownCheckInId = null,
        sealedAt = now,
    )

    /**
     * 组合出退出前结论（纯判定，不含持久化）：
     * - 无草稿 / EMPTY → NoPending；
     * - CONFIRMED 或「明确未成功且未发送」→ ConfirmedTerminal；
     * - 其余 → 需封存（返回 [PreSeal]，由持久化层落盘后转 Sealed / SealFailed）。
     */
    fun classify(draft: CheckInDraft?, accountId: String, now: Instant): AccountLeaveClassification = when {
        draft == null || draft.uploadState == UploadState.EMPTY ->
            AccountLeaveClassification.NoPending
        draft.uploadState == UploadState.CONFIRMED ->
            AccountLeaveClassification.ConfirmedTerminal
        !needsSeal(draft) ->
            AccountLeaveClassification.ConfirmedTerminal
        else ->
            AccountLeaveClassification.PreSeal(buildSeal(draft, accountId, now))
    }
}

/** 退出前决策中间态（持久化层据此落盘封存）。 */
sealed interface AccountLeaveClassification {
    data object NoPending : AccountLeaveClassification
    data object ConfirmedTerminal : AccountLeaveClassification
    data class PreSeal(val seal: CheckInSeal) : AccountLeaveClassification
}
