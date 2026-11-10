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

    /**
     * 挑战过期：先查旧结果。
     * - [recoveredReceipt] 非空表示此前已成功（可恢复）；
     * - 为空时由 [unknownResult] 区分：false=查询确认无记录（明确未成功，可换新挑战重拍），
     *   true=查询故障/无法判定（结果未知，保留照片、仅可同 key 重试）。
     */
    data class ChallengeExpired(
        val recoveredReceipt: CheckInReceipt?,
        val unknownResult: Boolean,
    ) : SubmitOutcome

    /**
     * 失败。 [unknownResult]：
     * - true = 结果未知（网络中断或查询故障），不可安全重拍/换新尝试，仅可同 key 同内容重试；
     * - false = 明确未成功（服务端明确拒绝或查询确认无记录），可安全换新尝试。
     */
    data class Failed(val error: Throwable, val unknownResult: Boolean) : SubmitOutcome
}

/** 提交结果对页面可采取的下一步动作。 */
enum class SubmitResolution {
    /** 已成功（含恢复），可发奖/清草稿。 */
    SUCCESS,

    /** 明确未成功，可安全换新尝试（重拍/删除旧照/生成新 attemptId）。 */
    CONFIRMED_ABSENT,

    /** 结果未知或查询故障，保留照片与冻结内容，仅可同 key 同内容重试。 */
    UNKNOWN,
}

/** 把提交结果归一到「下一步动作」。 */
fun SubmitOutcome.resolution(): SubmitResolution = when (this) {
    is SubmitOutcome.Success -> SubmitResolution.SUCCESS
    is SubmitOutcome.Recovered -> SubmitResolution.SUCCESS
    is SubmitOutcome.ChallengeExpired -> when {
        recoveredReceipt != null -> SubmitResolution.SUCCESS
        unknownResult -> SubmitResolution.UNKNOWN
        else -> SubmitResolution.CONFIRMED_ABSENT
    }
    is SubmitOutcome.Failed -> if (unknownResult) SubmitResolution.UNKNOWN else SubmitResolution.CONFIRMED_ABSENT
}

/**
 * 打卡提交编排（纯 Kotlin，可注入 fake repository 做 JVM 单测）。
 *
 * 落实 C-N01/C-N02 / R1~R4 规则：
 * - 幂等键用稳定 [attemptId]（每次新尝试独立生成，由调用方保证）；
 * - 请求内容（challengeId / photoId / location）由调用方冻结后传入，同一尝试重试不换内容；
 * - 结果未知（网络中断 / 服务端 5xx 可重试）先查询，确认未成功才允许重试；
 * - 挑战过期先查旧结果，确认未成功再由调用方换新挑战重拍；
 * - 查询按 **attempt** 身份识别，不用「某地标历史成功」替代本次 attempt 结果；
 * - 查询只把「服务端特定 404 CHECKIN_NOT_FOUND」判为明确无记录，普通 404/405/5xx/超时/未配置一律未知；
 * - 已发出提交的 attempt 提供独立的 [recover] 查询入口（不重放 POST、不依赖本地冻结内容）；
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
                isChallengeExpired(e) -> onChallengeExpired(e, attemptId)
                isUnknownOutcome(e) -> onUnknownOutcome(e, attemptId)
                isIdempotencyConflict(e) -> onIdempotencyConflict(e, attemptId)
                else -> SubmitOutcome.Failed(e, unknownResult = false)
            }
        }
    }

    /**
     * 已发出提交的 attempt 的恢复入口：只按 [attemptId] 查询，不重放 POST、不依赖本地冻结内容。
     * 用于 UNKNOWN 草稿恢复 / 结果未知后的「重试恢复」。
     *
     * F3：在服务端「可靠终态」保证成立前，查询无记录（NotFound）不能证明旧 POST 不会稍后成功
     * （存在在途请求先查无记录、后写入的竞态），因此一律保持未知（不返回 CONFIRMED_ABSENT），
     * 禁止据此换新 ID / 删旧照。待 D 落地终态语义 + 竞态测试后再放行 CONFIRMED_ABSENT。
     */
    suspend fun recover(attemptId: String): SubmitOutcome =
        when (val q = queryStatus(attemptId)) {
            is CheckInQueryResult.Success -> SubmitOutcome.Recovered(q.receipt)
            CheckInQueryResult.NotFound -> SubmitOutcome.Failed(
                CheckInApiException("CHECKIN_NOT_FOUND", true, "服务端暂未确认本次尝试结果，请稍后重试"),
                unknownResult = true,
            )
            CheckInQueryResult.Processing -> SubmitOutcome.Failed(
                CheckInApiException("RECOVERY_PROCESSING", true, "服务端处理中，请稍后重试"),
                unknownResult = true,
            )
            CheckInQueryResult.Unavailable -> SubmitOutcome.Failed(
                CheckInApiException("RECOVERY_UNAVAILABLE", true, "无法确认上次结果，请稍后重试"),
                unknownResult = true,
            )
        }

    private suspend fun onChallengeExpired(e: Throwable, attemptId: String): SubmitOutcome =
        when (val q = queryStatus(attemptId)) {
            is CheckInQueryResult.Success -> SubmitOutcome.ChallengeExpired(q.receipt, unknownResult = false)
            CheckInQueryResult.NotFound -> SubmitOutcome.ChallengeExpired(null, unknownResult = false)
            CheckInQueryResult.Processing -> SubmitOutcome.ChallengeExpired(null, unknownResult = true)
            CheckInQueryResult.Unavailable -> SubmitOutcome.ChallengeExpired(null, unknownResult = true)
        }

    private suspend fun onUnknownOutcome(e: Throwable, attemptId: String): SubmitOutcome =
        when (val q = queryStatus(attemptId)) {
            is CheckInQueryResult.Success -> SubmitOutcome.Recovered(q.receipt)
            // 提交自身结果未知（可能已到达服务端），查询确认无记录也不能排除竞态 → 保持未知
            CheckInQueryResult.NotFound -> SubmitOutcome.Failed(e, unknownResult = true)
            CheckInQueryResult.Processing -> SubmitOutcome.Failed(e, unknownResult = true)
            CheckInQueryResult.Unavailable -> SubmitOutcome.Failed(e, unknownResult = true)
        }

    private suspend fun onIdempotencyConflict(e: Throwable, attemptId: String): SubmitOutcome =
        when (val q = queryStatus(attemptId)) {
            is CheckInQueryResult.Success -> SubmitOutcome.Recovered(q.receipt)
            // 服务端明确拒绝（幂等冲突），查询确认无记录即「明确未成功」
            CheckInQueryResult.NotFound -> SubmitOutcome.Failed(e, unknownResult = false)
            CheckInQueryResult.Processing -> SubmitOutcome.Failed(e, unknownResult = true)
            CheckInQueryResult.Unavailable -> SubmitOutcome.Failed(e, unknownResult = true)
        }

    private suspend fun queryStatus(attemptId: String): CheckInQueryResult =
        try {
            repository.queryCheckIn(attemptId)
        } catch (e: Throwable) {
            CheckInQueryResult.Unavailable
        }

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
