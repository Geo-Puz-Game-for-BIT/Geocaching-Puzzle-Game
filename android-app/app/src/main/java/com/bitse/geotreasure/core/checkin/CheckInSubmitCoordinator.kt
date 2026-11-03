package com.bitse.geotreasure.core.checkin

import com.bitse.geotreasure.core.model.CheckInReceipt
import com.bitse.geotreasure.core.model.LocationSample
import com.bitse.geotreasure.core.model.SubmitCheckInRequest
import com.bitse.geotreasure.data.remote.CheckInApiException
import com.bitse.geotreasure.data.repository.CheckInRepository
import java.io.IOException

/** 一次提交尝试的编排结果。 */
sealed interface SubmitOutcome {
    /** 服务端本次返回成功回执。 */
    data class Success(val receipt: CheckInReceipt) : SubmitOutcome

    /** 未知/冲突后经查询确认此前已成功（恢复，不重复发奖）。 */
    data class Recovered(val receipt: CheckInReceipt) : SubmitOutcome

    /** 挑战过期：recoveredReceipt 非空表示此前已成功（可恢复），空表示需换新挑战重拍。 */
    data class ChallengeExpired(val recoveredReceipt: CheckInReceipt?) : SubmitOutcome

    /** 明确失败；unknownResult=true 表示结果未知（可能是网络中断，可同 key 同内容重试）。 */
    data class Failed(val error: Throwable, val unknownResult: Boolean) : SubmitOutcome
}

/**
 * 打卡提交编排（纯 Kotlin，可注入 fake repository 做 JVM 单测）。
 *
 * 落实 C-N01/C-N02 规则：
 * - 幂等键用稳定 [attemptId]（每次新尝试独立生成，由调用方保证）；
 * - 请求内容（challengeId / photoId / location）由调用方冻结后传入，同一尝试重试不换内容；
 * - 结果未知（网络中断 / 服务端 5xx 可重试）先查询，确认未成功才允许重试；
 * - 挑战过期先查旧结果，确认未成功再由调用方换新挑战重拍；
 * - 只有拿到有效成功回执才由调用方更新成果，本类绝不自行发奖。
 */
class CheckInSubmitCoordinator(
    private val repository: CheckInRepository,
) {

    suspend fun submit(
        landmarkId: String,
        attemptId: String,
        challengeId: String,
        photoId: String,
        location: LocationSample,
    ): SubmitOutcome {
        val request = SubmitCheckInRequest(
            landmarkId = landmarkId,
            challengeId = challengeId,
            photoId = photoId,
            location = location,
        )
        return try {
            SubmitOutcome.Success(repository.submitCheckIn(request, attemptId))
        } catch (e: Throwable) {
            when {
                isChallengeExpired(e) ->
                    SubmitOutcome.ChallengeExpired(queryQuietly(landmarkId))
                isUnknownOutcome(e) -> {
                    val recovered = queryQuietly(landmarkId)
                    if (recovered != null) SubmitOutcome.Recovered(recovered)
                    else SubmitOutcome.Failed(e, unknownResult = true)
                }
                isIdempotencyConflict(e) -> {
                    val recovered = queryQuietly(landmarkId)
                    if (recovered != null) SubmitOutcome.Recovered(recovered)
                    else SubmitOutcome.Failed(e, unknownResult = false)
                }
                else -> SubmitOutcome.Failed(e, unknownResult = false)
            }
        }
    }

    private suspend fun queryQuietly(landmarkId: String): CheckInReceipt? =
        runCatching { repository.queryCheckIn(landmarkId) }.getOrNull()

    private fun isChallengeExpired(e: Throwable): Boolean =
        (e as? CheckInApiException)?.code == "CHALLENGE_EXPIRED"

    private fun isIdempotencyConflict(e: Throwable): Boolean =
        (e as? CheckInApiException)?.code == "IDEMPOTENCY_CONFLICT"

    private fun isUnknownOutcome(e: Throwable): Boolean = when (e) {
        is IOException -> true
        is CheckInApiException -> e.retryable && e.code in setOf("AUDIT_WRITE_FAILED", "INTERNAL_ERROR")
        else -> false
    }
}
