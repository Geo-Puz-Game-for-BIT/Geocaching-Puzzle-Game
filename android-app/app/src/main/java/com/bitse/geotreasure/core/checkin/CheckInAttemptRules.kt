package com.bitse.geotreasure.core.checkin

import com.bitse.geotreasure.core.model.UploadState

/**
 * 打卡页面状态守卫纯规则（JVM 可单测）。
 *
 * 落实 C-N01/C-N05「结果未知先恢复、不可覆盖」：一旦本次 attempt 提交结果未知（含查询故障），
 * 在查询得到可靠终态前禁止重拍、替换照片、清草稿、切任务覆盖；只允许同 key 同内容重试。
 */
object CheckInAttemptRules {

    /** 结果未知（网络中断/服务端 5xx/查询故障）。 */
    fun isOutcomeUnknown(uploadState: UploadState): Boolean = uploadState == UploadState.UNKNOWN

    /** 是否可重拍 / 换新尝试：非处理中、非未知、非已确认。 */
    fun canRetake(uploadState: UploadState, isProcessing: Boolean): Boolean =
        !isProcessing && !isOutcomeUnknown(uploadState) && uploadState != UploadState.CONFIRMED

    /** 是否可丢弃一个待处理的未完成草稿：未知/提交进行中不可丢弃（可能已成功）。 */
    fun canDiscard(uploadState: UploadState): Boolean =
        uploadState != UploadState.UNKNOWN && uploadState != UploadState.UPLOADING
}
