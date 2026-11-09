package com.bitse.geotreasure.core.checkin

import com.bitse.geotreasure.core.model.CheckInChallenge
import com.bitse.geotreasure.core.model.CheckInReceipt
import com.bitse.geotreasure.core.model.CheckInStatus
import com.bitse.geotreasure.core.model.FragmentGrant
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.LocationSample
import com.bitse.geotreasure.core.model.LocationSourceKind
import com.bitse.geotreasure.core.model.PhotoDeletionResult
import com.bitse.geotreasure.core.model.SubmitCheckInRequest
import com.bitse.geotreasure.core.model.UploadedPhoto
import com.bitse.geotreasure.data.remote.CheckInApiException
import com.bitse.geotreasure.data.repository.CheckInRepository
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckInSubmitCoordinatorTest {

    private fun receipt(checkInId: String = "chk_1") = CheckInReceipt(
        checkInId = checkInId,
        status = CheckInStatus.COMPLETED,
        fragmentGrant = FragmentGrant("frg_1", "pzl_1", 0, Instant.fromEpochMilliseconds(0L)),
    )

    private fun location() = LocationSample(
        point = GeoPoint(39.0, 116.0),
        accuracyMeters = 10f,
        capturedAt = Instant.fromEpochMilliseconds(0L),
        source = LocationSourceKind.GPS,
    )

    private class FakeRepository : CheckInRepository {
        var submitCalls = 0
        var queryResult: CheckInQueryResult? = null
        var queryError: Throwable? = null
        var submitError: Throwable? = null
        var lastKey: String? = null
        var lastRequest: SubmitCheckInRequest? = null
        var lastQueryAttemptId: String? = null

        override suspend fun uploadPhoto(
            photoFile: File,
            challengeId: String,
            attemptId: String,
        ): UploadedPhoto =
            UploadedPhoto("pho_1", "obj", Instant.fromEpochMilliseconds(0L))

        override suspend fun createChallenge(landmarkId: String, attemptId: String): CheckInChallenge =
            CheckInChallenge("chl_1", landmarkId, Instant.fromEpochMilliseconds(0L))

        override suspend fun submitCheckIn(request: SubmitCheckInRequest, idempotencyKey: String): CheckInReceipt {
            submitCalls++
            lastKey = idempotencyKey
            lastRequest = request
            submitError?.let { throw it }
            return CheckInReceipt(
                "chk_1",
                CheckInStatus.COMPLETED,
                FragmentGrant("frg_1", "pzl_1", 0, Instant.fromEpochMilliseconds(0L)),
            )
        }

        override suspend fun queryCheckIn(attemptId: String): CheckInQueryResult {
            lastQueryAttemptId = attemptId
            queryError?.let { throw it }
            return queryResult ?: CheckInQueryResult.NotFound
        }

        override suspend fun deletePhoto(photoId: String): PhotoDeletionResult =
            PhotoDeletionResult(
                photoId = photoId,
                storageCleanupPending = false,
                deleteAfter = Instant.fromEpochMilliseconds(0L),
            )
    }

    @Test
    fun success_usesAttemptIdAsKey() = runBlocking {
        val repo = FakeRepository()
        val coordinator = CheckInSubmitCoordinator(repo)
        val outcome = coordinator.submit("lmk_1", "att_1", "chl_1", "pho_1", location())
        assertTrue(outcome is SubmitOutcome.Success)
        assertEquals("att_1", repo.lastKey)
    }

    @Test
    fun unknownOutcome_queryUsesAttemptId() = runBlocking {
        val repo = FakeRepository()
        repo.submitError = IOException("network lost")
        repo.queryResult = CheckInQueryResult.NotFound
        val coordinator = CheckInSubmitCoordinator(repo)
        coordinator.submit("lmk_1", "att_1", "chl_1", "pho_1", location())
        // R1：查询必须按 attempt 身份，而非 landmark
        assertEquals("att_1", repo.lastQueryAttemptId)
    }

    @Test
    fun unknownOutcome_recoversWithoutResubmit() = runBlocking {
        val repo = FakeRepository()
        repo.submitError = IOException("network lost")
        repo.queryResult = CheckInQueryResult.Success(receipt("chk_1"))
        val coordinator = CheckInSubmitCoordinator(repo)
        val outcome = coordinator.submit("lmk_1", "att_1", "chl_1", "pho_1", location())
        assertTrue(outcome is SubmitOutcome.Recovered)
        assertEquals(1, repo.submitCalls)
    }

    @Test
    fun unknownOutcome_noPriorResult_isFailedUnknown() = runBlocking {
        val repo = FakeRepository()
        repo.submitError = IOException("network lost")
        repo.queryResult = CheckInQueryResult.NotFound
        val coordinator = CheckInSubmitCoordinator(repo)
        val outcome = coordinator.submit("lmk_1", "att_1", "chl_1", "pho_1", location())
        assertTrue(outcome is SubmitOutcome.Failed)
        assertTrue((outcome as SubmitOutcome.Failed).unknownResult)
    }

    @Test
    fun unknownOutcome_processing_isFailedUnknown() = runBlocking {
        val repo = FakeRepository()
        repo.submitError = IOException("network lost")
        repo.queryResult = CheckInQueryResult.Processing
        val coordinator = CheckInSubmitCoordinator(repo)
        val outcome = coordinator.submit("lmk_1", "att_1", "chl_1", "pho_1", location())
        assertTrue(outcome is SubmitOutcome.Failed)
        assertTrue((outcome as SubmitOutcome.Failed).unknownResult)
    }

    @Test
    fun unknownOutcome_queryFailure_isFailedUnknown() = runBlocking {
        val repo = FakeRepository()
        repo.submitError = IOException("network lost")
        repo.queryError = IOException("query timeout")
        val coordinator = CheckInSubmitCoordinator(repo)
        val outcome = coordinator.submit("lmk_1", "att_1", "chl_1", "pho_1", location())
        assertTrue(outcome is SubmitOutcome.Failed)
        assertTrue((outcome as SubmitOutcome.Failed).unknownResult)
    }

    @Test
    fun idempotencyConflict_recovers() = runBlocking {
        val repo = FakeRepository()
        repo.submitError = CheckInApiException("IDEMPOTENCY_CONFLICT", false, "conflict")
        repo.queryResult = CheckInQueryResult.Success(receipt("chk_1"))
        val coordinator = CheckInSubmitCoordinator(repo)
        val outcome = coordinator.submit("lmk_1", "att_1", "chl_1", "pho_1", location())
        assertTrue(outcome is SubmitOutcome.Recovered)
    }

    @Test
    fun idempotencyConflict_noPriorResult_confirmedAbsent() = runBlocking {
        val repo = FakeRepository()
        repo.submitError = CheckInApiException("IDEMPOTENCY_CONFLICT", false, "conflict")
        repo.queryResult = CheckInQueryResult.NotFound
        val coordinator = CheckInSubmitCoordinator(repo)
        val outcome = coordinator.submit("lmk_1", "att_1", "chl_1", "pho_1", location())
        assertTrue(outcome is SubmitOutcome.Failed)
        assertTrue(!(outcome as SubmitOutcome.Failed).unknownResult)
    }

    @Test
    fun idempotencyConflict_queryFailure_isUnknown() = runBlocking {
        val repo = FakeRepository()
        repo.submitError = CheckInApiException("IDEMPOTENCY_CONFLICT", false, "conflict")
        repo.queryError = IOException("query 405")
        val coordinator = CheckInSubmitCoordinator(repo)
        val outcome = coordinator.submit("lmk_1", "att_1", "chl_1", "pho_1", location())
        assertTrue(outcome is SubmitOutcome.Failed)
        assertTrue((outcome as SubmitOutcome.Failed).unknownResult)
    }

    @Test
    fun challengeExpired_noPriorResult_confirmedAbsent() = runBlocking {
        val repo = FakeRepository()
        repo.submitError = CheckInApiException("CHALLENGE_EXPIRED", true, "expired")
        repo.queryResult = CheckInQueryResult.NotFound
        val coordinator = CheckInSubmitCoordinator(repo)
        val outcome = coordinator.submit("lmk_1", "att_1", "chl_1", "pho_1", location())
        assertTrue(outcome is SubmitOutcome.ChallengeExpired)
        val expired = outcome as SubmitOutcome.ChallengeExpired
        assertEquals(null, expired.recoveredReceipt)
        assertEquals(false, expired.unknownResult)
    }

    @Test
    fun challengeExpired_queryFailure_isUnknown() = runBlocking {
        val repo = FakeRepository()
        repo.submitError = CheckInApiException("CHALLENGE_EXPIRED", true, "expired")
        repo.queryError = IOException("query 5xx")
        val coordinator = CheckInSubmitCoordinator(repo)
        val outcome = coordinator.submit("lmk_1", "att_1", "chl_1", "pho_1", location())
        assertTrue(outcome is SubmitOutcome.ChallengeExpired)
        val expired = outcome as SubmitOutcome.ChallengeExpired
        assertEquals(null, expired.recoveredReceipt)
        assertEquals(true, expired.unknownResult)
    }

    @Test
    fun challengeExpired_processing_isUnknown() = runBlocking {
        val repo = FakeRepository()
        repo.submitError = CheckInApiException("CHALLENGE_EXPIRED", true, "expired")
        repo.queryResult = CheckInQueryResult.Processing
        val coordinator = CheckInSubmitCoordinator(repo)
        val outcome = coordinator.submit("lmk_1", "att_1", "chl_1", "pho_1", location())
        assertTrue(outcome is SubmitOutcome.ChallengeExpired)
        val expired = outcome as SubmitOutcome.ChallengeExpired
        assertEquals(null, expired.recoveredReceipt)
        assertEquals(true, expired.unknownResult)
    }

    @Test
    fun challengeExpired_withPriorResult_recovers() = runBlocking {
        val repo = FakeRepository()
        repo.submitError = CheckInApiException("CHALLENGE_EXPIRED", true, "expired")
        repo.queryResult = CheckInQueryResult.Success(receipt("chk_1"))
        val coordinator = CheckInSubmitCoordinator(repo)
        val outcome = coordinator.submit("lmk_1", "att_1", "chl_1", "pho_1", location())
        assertTrue(outcome is SubmitOutcome.ChallengeExpired)
        assertTrue((outcome as SubmitOutcome.ChallengeExpired).recoveredReceipt != null)
    }

    @Test
    fun recover_success_recovers() = runBlocking {
        val repo = FakeRepository()
        repo.queryResult = CheckInQueryResult.Success(receipt("chk_1"))
        val coordinator = CheckInSubmitCoordinator(repo)
        val outcome = coordinator.recover("att_1")
        assertTrue(outcome is SubmitOutcome.Recovered)
        assertEquals("att_1", repo.lastQueryAttemptId)
        assertEquals(0, repo.submitCalls) // 恢复不重放 POST
    }

    @Test
    fun recover_notFound_isUnknown() = runBlocking {
        // F3：终态保证成立前，恢复查询无记录不能证明旧 POST 不会稍后成功 → 保持未知
        val repo = FakeRepository()
        repo.queryResult = CheckInQueryResult.NotFound
        val coordinator = CheckInSubmitCoordinator(repo)
        val outcome = coordinator.recover("att_1")
        assertTrue(outcome is SubmitOutcome.Failed)
        assertTrue((outcome as SubmitOutcome.Failed).unknownResult)
    }

    @Test
    fun unknownThenRecoverNotFound_staysUnknown() = runBlocking {
        // F3 回归：POST 未知后首次查询 NotFound → 未知；随后「重试恢复」再查 NotFound → 仍未知（不得 CONFIRMED_ABSENT）
        val repo = FakeRepository()
        repo.submitError = IOException("network lost")
        repo.queryResult = CheckInQueryResult.NotFound
        val coordinator = CheckInSubmitCoordinator(repo)
        val first = coordinator.submit("lmk_1", "att_1", "chl_1", "pho_1", location())
        assertTrue(first is SubmitOutcome.Failed)
        assertTrue((first as SubmitOutcome.Failed).unknownResult)

        val second = coordinator.recover("att_1")
        assertTrue(second is SubmitOutcome.Failed)
        assertTrue((second as SubmitOutcome.Failed).unknownResult)
    }

    @Test
    fun recover_processing_isUnknown() = runBlocking {
        val repo = FakeRepository()
        repo.queryResult = CheckInQueryResult.Processing
        val coordinator = CheckInSubmitCoordinator(repo)
        val outcome = coordinator.recover("att_1")
        assertTrue(outcome is SubmitOutcome.Failed)
        assertTrue((outcome as SubmitOutcome.Failed).unknownResult)
    }

    @Test
    fun recover_unavailable_isUnknown() = runBlocking {
        val repo = FakeRepository()
        repo.queryResult = CheckInQueryResult.Unavailable
        val coordinator = CheckInSubmitCoordinator(repo)
        val outcome = coordinator.recover("att_1")
        assertTrue(outcome is SubmitOutcome.Failed)
        assertTrue((outcome as SubmitOutcome.Failed).unknownResult)
    }

    @Test
    fun recover_queryFailure_isUnknown() = runBlocking {
        val repo = FakeRepository()
        repo.queryError = IOException("query 5xx")
        val coordinator = CheckInSubmitCoordinator(repo)
        val outcome = coordinator.recover("att_1")
        assertTrue(outcome is SubmitOutcome.Failed)
        assertTrue((outcome as SubmitOutcome.Failed).unknownResult)
    }

    @Test
    fun resolution_mapsOutcomes() {
        val r = receipt()
        assertEquals(
            SubmitResolution.SUCCESS,
            SubmitOutcome.Success(r).resolution(),
        )
        assertEquals(
            SubmitResolution.SUCCESS,
            SubmitOutcome.Recovered(r).resolution(),
        )
        assertEquals(
            SubmitResolution.SUCCESS,
            SubmitOutcome.ChallengeExpired(r, unknownResult = false).resolution(),
        )
        assertEquals(
            SubmitResolution.CONFIRMED_ABSENT,
            SubmitOutcome.ChallengeExpired(null, unknownResult = false).resolution(),
        )
        assertEquals(
            SubmitResolution.UNKNOWN,
            SubmitOutcome.ChallengeExpired(null, unknownResult = true).resolution(),
        )
        assertEquals(
            SubmitResolution.UNKNOWN,
            SubmitOutcome.Failed(IOException("x"), unknownResult = true).resolution(),
        )
        assertEquals(
            SubmitResolution.CONFIRMED_ABSENT,
            SubmitOutcome.Failed(IOException("x"), unknownResult = false).resolution(),
        )
    }
}
