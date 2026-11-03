package com.bitse.geotreasure.core.checkin

import com.bitse.geotreasure.core.model.CheckInChallenge
import com.bitse.geotreasure.core.model.CheckInReceipt
import com.bitse.geotreasure.core.model.CheckInStatus
import com.bitse.geotreasure.core.model.FragmentGrant
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.LocationSample
import com.bitse.geotreasure.core.model.LocationSourceKind
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
        var queryResult: CheckInReceipt? = null
        var submitError: Throwable? = null
        var lastKey: String? = null
        var lastRequest: SubmitCheckInRequest? = null

        override suspend fun uploadPhoto(photoFile: File): UploadedPhoto =
            UploadedPhoto("pho_1", "obj", Instant.fromEpochMilliseconds(0L))

        override suspend fun createChallenge(landmarkId: String): CheckInChallenge =
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

        override suspend fun queryCheckIn(landmarkId: String): CheckInReceipt? = queryResult
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
    fun unknownOutcome_recoversWithoutResubmit() = runBlocking {
        val repo = FakeRepository()
        repo.submitError = IOException("network lost")
        repo.queryResult = receipt("chk_1")
        val coordinator = CheckInSubmitCoordinator(repo)
        val outcome = coordinator.submit("lmk_1", "att_1", "chl_1", "pho_1", location())
        assertTrue(outcome is SubmitOutcome.Recovered)
        assertEquals(1, repo.submitCalls) // 不重复提交，不增奖
    }

    @Test
    fun unknownOutcome_noPriorResult_isFailedUnknown() = runBlocking {
        val repo = FakeRepository()
        repo.submitError = IOException("network lost")
        repo.queryResult = null
        val coordinator = CheckInSubmitCoordinator(repo)
        val outcome = coordinator.submit("lmk_1", "att_1", "chl_1", "pho_1", location())
        assertTrue(outcome is SubmitOutcome.Failed)
        assertTrue((outcome as SubmitOutcome.Failed).unknownResult)
    }

    @Test
    fun idempotencyConflict_recovers() = runBlocking {
        val repo = FakeRepository()
        repo.submitError = CheckInApiException("IDEMPOTENCY_CONFLICT", false, "conflict")
        repo.queryResult = receipt("chk_1")
        val coordinator = CheckInSubmitCoordinator(repo)
        val outcome = coordinator.submit("lmk_1", "att_1", "chl_1", "pho_1", location())
        assertTrue(outcome is SubmitOutcome.Recovered)
    }

    @Test
    fun idempotencyConflict_noPriorResult_fails() = runBlocking {
        val repo = FakeRepository()
        repo.submitError = CheckInApiException("IDEMPOTENCY_CONFLICT", false, "conflict")
        repo.queryResult = null
        val coordinator = CheckInSubmitCoordinator(repo)
        val outcome = coordinator.submit("lmk_1", "att_1", "chl_1", "pho_1", location())
        assertTrue(outcome is SubmitOutcome.Failed)
        assertTrue(!(outcome as SubmitOutcome.Failed).unknownResult)
    }

    @Test
    fun challengeExpired_noPriorResult_needsNewAttempt() = runBlocking {
        val repo = FakeRepository()
        repo.submitError = CheckInApiException("CHALLENGE_EXPIRED", true, "expired")
        repo.queryResult = null
        val coordinator = CheckInSubmitCoordinator(repo)
        val outcome = coordinator.submit("lmk_1", "att_1", "chl_1", "pho_1", location())
        assertTrue(outcome is SubmitOutcome.ChallengeExpired)
        assertTrue((outcome as SubmitOutcome.ChallengeExpired).recoveredReceipt == null)
    }

    @Test
    fun challengeExpired_withPriorResult_recovers() = runBlocking {
        val repo = FakeRepository()
        repo.submitError = CheckInApiException("CHALLENGE_EXPIRED", true, "expired")
        repo.queryResult = receipt("chk_1")
        val coordinator = CheckInSubmitCoordinator(repo)
        val outcome = coordinator.submit("lmk_1", "att_1", "chl_1", "pho_1", location())
        assertTrue(outcome is SubmitOutcome.ChallengeExpired)
        assertTrue((outcome as SubmitOutcome.ChallengeExpired).recoveredReceipt != null)
    }
}
