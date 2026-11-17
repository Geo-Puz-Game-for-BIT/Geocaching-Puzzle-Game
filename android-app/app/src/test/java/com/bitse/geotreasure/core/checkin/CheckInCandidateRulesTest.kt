package com.bitse.geotreasure.core.checkin

import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.LocationSample
import com.bitse.geotreasure.core.model.LocationSourceKind
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 候选尝试生命周期规则测试。
 *
 * 覆盖两个整改问题：
 * 1. 拍摄失败仍覆盖旧上下文 → abort 必须保留 challenge_old / attempt_old / 定位；
 * 2. 换 ID 时机与 D 新契约不兼容 → 候选的 challenge 拍前绑定候选 attemptId，commit 才统一采用。
 */
class CheckInCandidateRulesTest {

    private val location = LocationSample(
        point = GeoPoint(39.0, 116.0),
        accuracyMeters = 10f,
        capturedAt = Instant.fromEpochMilliseconds(0L),
        source = LocationSourceKind.GPS,
    )
    private val expiresOld = Instant.fromEpochMilliseconds(1_000L)
    private val expiresNew = Instant.fromEpochMilliseconds(2_000L)

    private fun official() = AttemptSnapshot("att_old", "chl_old", expiresOld, location)

    // ---------- 问题 1：拍摄失败保留旧上下文 ----------

    @Test
    fun abort_keepsOfficialChallenge_whenCandidateHadNewChallenge() {
        // 拍摄前候选领了 challenge_new，拍摄失败 → 丢弃候选，官方仍保留 challenge_old
        val flow = CandidateFlow(official = official())
        val withCandidate = flow.copy(
            candidate = AttemptSnapshot("att_new", "chl_new", expiresNew, location),
        )
        val afterAbort = CheckInCandidateRules.abort(withCandidate)

        assertEquals("chl_old", afterAbort.official.challengeId)
        assertEquals("att_old", afterAbort.official.attemptId)
        assertEquals(location, afterAbort.official.frozenLocation)
        assertNull(afterAbort.candidate)
    }

    @Test
    fun abort_withoutCandidate_isIdentity() {
        val flow = CandidateFlow(official = official())
        val afterAbort = CheckInCandidateRules.abort(flow)
        assertEquals(official(), afterAbort.official)
        assertNull(afterAbort.candidate)
    }

    // ---------- 问题 2：challenge 拍前绑定 attempt，commit 统一采用 ----------

    @Test
    fun commit_adoptsCandidateAttemptAndChallengeBinding() {
        // 候选 challenge 绑定候选 attemptId，commit 后 attempt 与 challenge 一致
        val flow = CandidateFlow(official = official())
        val candidate = AttemptSnapshot("att_new", "chl_new", expiresNew, location)
        val afterCommit = CheckInCandidateRules.commit(flow.copy(candidate = candidate))

        assertEquals("att_new", afterCommit.official.attemptId)
        assertEquals("chl_new", afterCommit.official.challengeId)
        assertEquals(expiresNew, afterCommit.official.challengeExpiresAt)
        assertEquals(location, afterCommit.official.frozenLocation)
        assertNull(afterCommit.candidate)
    }

    @Test
    fun commit_withoutCandidate_keepsOfficial() {
        val flow = CandidateFlow(official = official())
        assertEquals(official(), CheckInCandidateRules.commit(flow).official)
    }

    // ---------- 三态候选组装 ----------

    @Test
    fun buildCandidate_whenRequestSent_usesNewAttemptAndNewChallenge() {
        // 已发送：换新 attemptId + 新 challenge（绑定一致）
        val candidate = CheckInCandidateRules.buildCandidate(
            official = official(),
            requestSent = true,
            hasValidChallenge = true,
            newAttemptId = "att_new",
            newChallengeId = "chl_new",
            newChallengeExpiresAt = expiresNew,
            location = location,
        )
        assertEquals("att_new", candidate.attemptId)
        assertEquals("chl_new", candidate.challengeId)
    }

    @Test
    fun buildCandidate_whenNoValidChallenge_reusesAttemptWithNewChallenge() {
        // 首拍/挑战过期：复用官方 attemptId，仅领新 challenge（不换 ID）
        val candidate = CheckInCandidateRules.buildCandidate(
            official = official(),
            requestSent = false,
            hasValidChallenge = false,
            newAttemptId = "att_new",
            newChallengeId = "chl_new",
            newChallengeExpiresAt = expiresNew,
            location = location,
        )
        assertEquals("att_old", candidate.attemptId)
        assertEquals("chl_new", candidate.challengeId)
    }

    @Test
    fun buildCandidate_whenValidChallenge_reusesAttemptAndChallenge() {
        // 重拍（challenge 仍有效）：完全复用，仅刷新定位
        val candidate = CheckInCandidateRules.buildCandidate(
            official = official(),
            requestSent = false,
            hasValidChallenge = true,
            newAttemptId = "att_new",
            newChallengeId = "chl_new",
            newChallengeExpiresAt = expiresNew,
            location = location,
        )
        assertEquals("att_old", candidate.attemptId)
        assertEquals("chl_old", candidate.challengeId)
        assertEquals(location, candidate.frozenLocation)
    }

    // ---------- 失败保留 / 全程同 attemptId 回归 ----------

    @Test
    fun captureFailure_keepsOfficialDraftFullyUntouched() {
        // 拍摄失败/取消：官方草稿（attemptId/challengeId/expiresAt/frozenLocation）任何字段都不得被候选改写
        val before = official()
        val flow = CandidateFlow(official = before)
        val withCandidate = flow.copy(
            candidate = AttemptSnapshot("att_new", "chl_new", expiresNew, location),
        )
        val afterAbort = CheckInCandidateRules.abort(withCandidate)

        assertEquals(before, afterAbort.official)
        assertNull(afterAbort.candidate)
    }

    @Test
    fun fullFlow_success_keepsSingleAttemptIdEndToEnd() {
        // 全程同 attemptId：领 challenge 用候选 ID，commit 后 attemptId 仍为该 ID，challenge 始终绑定它，不发生二次换 ID
        val flow = CandidateFlow(official = official())
        val candidate = CheckInCandidateRules.buildCandidate(
            official = flow.official,
            requestSent = true,
            hasValidChallenge = false,
            newAttemptId = "att_new",
            newChallengeId = "chl_new",
            newChallengeExpiresAt = expiresNew,
            location = location,
        )
        // 候选内：challenge 绑定 attemptId
        assertEquals("att_new", candidate.attemptId)
        assertEquals("chl_new", candidate.challengeId)

        // commit：attemptId 与 challenge 一致采用，未发生二次换 ID
        val afterCommit = CheckInCandidateRules.commit(flow.copy(candidate = candidate))
        assertEquals("att_new", afterCommit.official.attemptId)
        assertEquals("chl_new", afterCommit.official.challengeId)
        assertEquals(candidate, afterCommit.official)
    }

    // ---------- 明确失败→重新开始：新 ID 回归 ----------

    @Test
    fun resetForNewAttempt_generatesFreshAttemptId() {
        val reset = CheckInCandidateRules.resetForNewAttempt("att_new")
        assertEquals("att_new", reset.attemptId)
        assertNull(reset.challengeId)
        assertNull(reset.challengeExpiresAt)
        assertNull(reset.frozenLocation)
    }

    @Test
    fun afterReset_captureReusesTheNewAttemptId_notOldId() {
        // 回归：已发送明确失败 → 点重新开始（reset 生成新 ID + 清 requestSent/challenge）→ 再次拍摄，
        // 候选必须复用 reset 的新 ID（而非因 requestSent=false 复用旧 ID），challenge 绑定新 ID，全程同一 ID。
        val reset = CheckInCandidateRules.resetForNewAttempt("att_new")
        val flow = CandidateFlow(official = reset)
        val candidate = CheckInCandidateRules.buildCandidate(
            official = flow.official,
            requestSent = false,        // reset 已清 requestSent
            hasValidChallenge = false,  // reset 已清 challenge
            newAttemptId = "att_unused",
            newChallengeId = "chl_new",
            newChallengeExpiresAt = expiresNew,
            location = location,
        )
        assertEquals("att_new", candidate.attemptId)
        assertEquals("chl_new", candidate.challengeId)

        val committed = CheckInCandidateRules.commit(flow.copy(candidate = candidate))
        assertEquals("att_new", committed.official.attemptId)
        assertEquals("chl_new", committed.official.challengeId)
    }

    // ---------- 决策谓词 ----------

    @Test
    fun needsNewAttemptId_onlyWhenRequestSent() {
        assertTrue(CheckInCandidateRules.needsNewAttemptId(requestSent = true))
        assertFalse(CheckInCandidateRules.needsNewAttemptId(requestSent = false))
    }

    @Test
    fun needsNewChallenge_whenRequestSentOrChallengeInvalid() {
        assertTrue(CheckInCandidateRules.needsNewChallenge(requestSent = true, hasValidChallenge = true))
        assertTrue(CheckInCandidateRules.needsNewChallenge(requestSent = false, hasValidChallenge = false))
        assertFalse(CheckInCandidateRules.needsNewChallenge(requestSent = false, hasValidChallenge = true))
    }
}
