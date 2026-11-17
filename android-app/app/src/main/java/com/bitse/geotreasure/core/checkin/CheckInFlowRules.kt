package com.bitse.geotreasure.core.checkin

import com.bitse.geotreasure.core.model.UploadState

/** 打卡提交页（CameraViewModel）的提交入口分流。 */
enum class SubmitRoute {
    /** 处理中 / 已确认：无需动作。 */
    RETURN,

    /** 明确失败（已发出且被明确拒绝）：换新 attempt 重拍。 */
    NEW_ATTEMPT,

    /** 已发出提交（结果未知等）：优先按 attemptId 查询恢复，不依赖本地照片。 */
    RECOVER,

    /** 首次提交：需要照片与冻结定位，走完整前处理。 */
    FIRST_SUBMIT,
}

/** 恢复草稿后要写回 UI 的补丁。 */
data class RecoveredDraftPatch(
    val photoPath: String?,
    val uploadState: UploadState,
    val restoredDraft: Boolean,
    val errorMessage: String?,
)

/**
 * 打卡页面状态机纯规则（JVM 可单测）。
 *
 * 落实第二轮复核 F1/F2 的客户端边界修正：
 * - F1：恢复草稿时**无条件**恢复 uploadState（照片是否存在只影响预览可用性，不影响「已发出/未知」状态）；
 *       提交入口先按「已发出/未知」进入查询恢复，再对首次提交检查照片与定位。
 * - F2：进入重拍仅切预览，保留旧照与当前 attempt（不生成新 ID、不删旧照），
 *       新照成功采用时才生成新 attempt。
 */
object CheckInFlowRules {

    /** 提交入口分流：先「已发出/未知」→ RECOVER，再「明确失败」→ NEW_ATTEMPT，最后 FIRST_SUBMIT。 */
    fun submitRoute(
        uploadState: UploadState,
        requestSent: Boolean,
        isProcessing: Boolean,
    ): SubmitRoute = when {
        isProcessing || uploadState == UploadState.CONFIRMED -> SubmitRoute.RETURN
        requestSent && uploadState == UploadState.FAILED -> SubmitRoute.NEW_ATTEMPT
        requestSent || uploadState == UploadState.UNKNOWN -> SubmitRoute.RECOVER
        else -> SubmitRoute.FIRST_SUBMIT
    }

    /**
     * 恢复草稿 → UI 补丁（F1）：uploadState 无条件恢复；
     * 照片文件缺失时 photoPath 置 null（预览不可用），但「已发出/未知」状态与恢复入口不受影响。
     * 「是否已发送」由 CheckInDraft.requestSent 持久化恢复，不在此推断。
     */
    fun buildRecoveredDraftPatch(
        uploadState: UploadState,
        localPhotoPath: String?,
        photoExists: Boolean,
    ): RecoveredDraftPatch = RecoveredDraftPatch(
        photoPath = if (photoExists) localPhotoPath else null,
        uploadState = uploadState,
        restoredDraft = true,
        errorMessage = when (uploadState) {
            UploadState.UNKNOWN -> "上次提交结果未知，请重试以恢复（不要重拍）"
            UploadState.FAILED -> "上次提交失败，请重试"
            else -> null
        },
    )
}
