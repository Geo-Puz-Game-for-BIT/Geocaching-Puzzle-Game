package com.bitse.geotreasure.core.checkin

import com.bitse.geotreasure.core.model.LocationSample
import kotlinx.datetime.Instant

/**
 * 一次打卡尝试的官方上下文快照。
 * 幂等键 [attemptId] 与已领取的 [challengeId] 必须绑定（D 契约：拍前 challenge 绑定 attempt），
 * 提交时冻结的定位 [frozenLocation] 一并快照。
 */
data class AttemptSnapshot(
    val attemptId: String,
    val challengeId: String?,
    val challengeExpiresAt: Instant?,
    val frozenLocation: LocationSample?,
)

/**
 * 候选尝试流程状态：官方上下文 [official] + 尚未采用的候选 [candidate]。
 *
 * 拍摄流程按「准备候选 → 成功 commit / 失败 abort」推进：
 * - 拍摄前 [buildCandidate] 只产出候选（**不**改动 official、**不**覆盖草稿）；
 * - 拍摄成功 commit 才把候选提升为 official；
 * - 拍摄失败/取消 abort 丢弃候选，official 保持原样。
 *
 * 由此同时解决两个问题：
 * 1. 拍摄失败仍覆盖旧上下文 —— 领新 challenge/更新定位只发生在候选内，失败即丢弃，旧 challenge/定位保留；
 * 2. 换 ID 时机与 D 新契约不兼容 —— 新候选的 challenge 用**候选 attemptId** 领取（绑定一致），
 *    成功后才正式采用该 ID，避免「challenge 绑旧 ID、上传用新 ID」。
 */
data class CandidateFlow(
    val official: AttemptSnapshot,
    val candidate: AttemptSnapshot? = null,
)

/** 候选尝试生命周期纯规则（JVM 可单测）。 */
object CheckInCandidateRules {

    /**
     * 是否需要换新 attemptId：已发出过提交的 attempt 不可复用（其幂等键已被服务端登记），
     * 重拍必须换新 attemptId 并配套新 challenge。
     */
    fun needsNewAttemptId(requestSent: Boolean): Boolean = requestSent

    /** 是否需要领新 challenge：换新 attempt 必领；当前无有效 challenge 也需领。 */
    fun needsNewChallenge(requestSent: Boolean, hasValidChallenge: Boolean): Boolean =
        requestSent || !hasValidChallenge

    /**
     * 拍摄前组装候选（三态）：
     * - [requestSent] → 新 attemptId + 新 challenge（二者一致绑定，D 契约）；
     * - `!hasValidChallenge` → 复用 official.attemptId + 新 challenge（首拍/挑战过期，attempt 仍新鲜）；
     * - 否则 → 复用 official.attemptId + official.challengeId（重拍仅刷新定位与照片）。
     * 三种情况冻结定位都刷新为 [location]。
     */
    fun buildCandidate(
        official: AttemptSnapshot,
        requestSent: Boolean,
        hasValidChallenge: Boolean,
        newAttemptId: String,
        newChallengeId: String,
        newChallengeExpiresAt: Instant,
        location: LocationSample?,
    ): AttemptSnapshot = when {
        requestSent -> AttemptSnapshot(newAttemptId, newChallengeId, newChallengeExpiresAt, location)
        !hasValidChallenge -> AttemptSnapshot(official.attemptId, newChallengeId, newChallengeExpiresAt, location)
        else -> AttemptSnapshot(official.attemptId, official.challengeId, official.challengeExpiresAt, location)
    }

    /**
     * 可靠结束旧尝试（明确未成功）后的全新官方上下文：新 attemptId + 空 challenge/定位。
     *
     * 新 ID 必须在此（可靠结束时刻）生成——若只在 capture 候选阶段依据 requestSent 判断是否换 ID，
     * 一旦 requestSent 被清掉而 ID 未换，候选会看到 requestSent=false 继续复用旧 ID，
     * 触发服务端幂等冲突（旧 ID 已被登记）。后续 capture 复用此新 ID 领新 challenge，全程同一 ID。
     */
    fun resetForNewAttempt(newAttemptId: String): AttemptSnapshot = AttemptSnapshot(
        attemptId = newAttemptId,
        challengeId = null,
        challengeExpiresAt = null,
        frozenLocation = null,
    )

    /** 拍摄成功：候选提升为官方上下文（attemptId 与 challengeId 保持一致绑定）。 */
    fun commit(flow: CandidateFlow): CandidateFlow =
        flow.candidate?.let { CandidateFlow(official = it) } ?: flow

    /** 拍摄失败/取消：丢弃候选，官方上下文保持不变（旧 attempt/challenge/定位全部保留）。 */
    fun abort(flow: CandidateFlow): CandidateFlow = flow.copy(candidate = null)
}
