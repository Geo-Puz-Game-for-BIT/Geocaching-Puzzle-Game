package com.bitse.geotreasure.data.remote

import com.bitse.geotreasure.core.checkin.CheckInQueryResult
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.LocationSample
import com.bitse.geotreasure.core.model.LocationSourceKind
import com.bitse.geotreasure.core.model.SubmitCheckInRequest
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.datetime.Instant
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CheckInApi.queryCheckIn 的 HTTP 解析测试（MockWebServer，覆盖 R1 要求的全部状态码）。
 *
 * 关键契约：只有「404 + error.code=CHECKIN_NOT_FOUND」才是 NotFound（明确无记录）；
 * 普通 404 / 405 / 5xx / 超时 / 未配置一律 Unavailable（查询故障，结果未知）。
 */
class CheckInApiTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun api(shortTimeout: Boolean = false): CheckInApi {
        val client = if (shortTimeout) {
            OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build()
        } else {
            OkHttpClient()
        }
        return CheckInApi(baseUrl = server.url("/api/v1").toString(), authToken = "tok_1", client = client)
    }

    private fun receiptJson() = """{"requestId":"req_1","data":{"checkInId":"chk_1","status":"COMPLETED","fragmentGrant":{"fragmentId":"frg_1","puzzleId":"pzl_1","pieceIndex":0,"grantedAt":"2024-01-01T00:00:00Z"}}}"""

    @Test
    fun query_200_returnsSuccess() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(receiptJson()))
        val result = api().queryCheckIn("att_1")
        assertTrue(result is CheckInQueryResult.Success)
        assertEquals("chk_1", (result as CheckInQueryResult.Success).receipt.checkInId)
        assertEquals("/api/v1/checkins?attemptId=att_1", server.takeRequest().path)
    }

    @Test
    fun query_202_returnsProcessing() {
        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"requestId":"req_1","data":{"status":"PROCESSING"}}"""))
        val result = api().queryCheckIn("att_1")
        assertEquals(CheckInQueryResult.Processing, result)
    }

    @Test
    fun query_404_checkInNotFound_returnsNotFound() {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"requestId":"req_1","error":{"code":"CHECKIN_NOT_FOUND","message":"not found"}}"""),
        )
        val result = api().queryCheckIn("att_1")
        assertEquals(CheckInQueryResult.NotFound, result)
    }

    @Test
    fun query_404_plain_returnsUnavailable() {
        // 普通路由/代理 404（无 CHECKIN_NOT_FOUND 错误码）不得判为明确无记录
        server.enqueue(MockResponse().setResponseCode(404).setBody("<html>Not Found</html>"))
        val result = api().queryCheckIn("att_1")
        assertEquals(CheckInQueryResult.Unavailable, result)
    }

    @Test
    fun query_404_emptyBody_returnsUnavailable() {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = api().queryCheckIn("att_1")
        assertEquals(CheckInQueryResult.Unavailable, result)
    }

    @Test
    fun query_404_wrongErrorCode_returnsUnavailable() {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{"requestId":"req_1","error":{"code":"OTHER_ERROR","message":"x"}}"""),
        )
        val result = api().queryCheckIn("att_1")
        assertEquals(CheckInQueryResult.Unavailable, result)
    }

    @Test
    fun query_405_returnsUnavailable() {
        server.enqueue(MockResponse().setResponseCode(405).setBody("Method Not Allowed"))
        val result = api().queryCheckIn("att_1")
        assertEquals(CheckInQueryResult.Unavailable, result)
    }

    @Test
    fun query_500_returnsUnavailable() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"requestId":"req_1","error":{"code":"INTERNAL_ERROR","message":"boom"}}"""))
        val result = api().queryCheckIn("att_1")
        assertEquals(CheckInQueryResult.Unavailable, result)
    }

    @Test
    fun query_timeout_returnsUnavailable() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val result = api(shortTimeout = true).queryCheckIn("att_1")
        assertEquals(CheckInQueryResult.Unavailable, result)
    }

    @Test
    fun query_notConfigured_returnsUnavailable() {
        // 未配置 baseUrl 不得返回 NotFound（未配置不能作为「明确未成功」的安全新尝试依据）
        val api = CheckInApi(baseUrl = "", authToken = "tok_1")
        val result = api.queryCheckIn("att_1")
        assertEquals(CheckInQueryResult.Unavailable, result)
    }

    @Test
    fun uploadPhoto_bindsChallengeAndAttemptHeaders() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"requestId":"req_1","data":{"photoId":"pho_1","objectKey":"checkins/usr_demo01/pho_1.jpg","uploadedAt":"2026-11-04T08:00:00Z"}}""",
            ),
        )
        val photo = File.createTempFile("checkin", ".jpg")
        photo.writeBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte()))
        try {
            api().uploadPhoto(photo, challengeId = "chl_1", attemptId = "att_1")
            val request = server.takeRequest()
            assertEquals("/api/v1/checkin-photos", request.path)
            assertEquals("chl_1", request.getHeader("X-Challenge-Id"))
            assertEquals("att_1", request.getHeader("X-Attempt-Id"))
        } finally {
            photo.delete()
        }
    }

    @Test
    fun createChallenge_bindsAttemptHeader() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"requestId":"req_1","data":{"id":"chl_1","landmarkId":"lmk_1","expiresAt":"2026-11-04T08:05:00Z"}}""",
            ),
        )

        api().createChallenge("lmk_1", "att_1")

        val request = server.takeRequest()
        assertEquals("/api/v1/checkin-challenges", request.path)
        assertEquals("att_1", request.getHeader("X-Attempt-Id"))
    }

    @Test
    fun submitCheckIn_sendsSourceAndMonotonicEvidence() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(receiptJson()))
        val request = SubmitCheckInRequest(
            landmarkId = "lmk_1",
            challengeId = "chl_1",
            photoId = "pho_1",
            location = LocationSample(
                point = GeoPoint(39.0, 116.0),
                accuracyMeters = 8f,
                capturedAt = Instant.parse("2026-11-04T08:00:00Z"),
                source = LocationSourceKind.GPS,
                elapsedRealtimeNanos = 1_000_000_000,
                submittedElapsedRealtimeNanos = 4_000_000_000,
            ),
        )

        api().submitCheckIn(request, "att_1")

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"source\":\"GPS\""))
        assertTrue(body.contains("\"elapsedRealtimeNanos\":1000000000"))
        assertTrue(body.contains("\"submittedElapsedRealtimeNanos\":4000000000"))
    }

    @Test
    fun deletePhoto_202_returnsPendingCleanupStatus() {
        server.enqueue(
            MockResponse().setResponseCode(202).setBody(
                """{"requestId":"req_1","data":{"photoId":"pho_1","status":"DELETED","storageCleanup":"PENDING","deleteAfter":"2026-11-05T08:00:00Z"}}""",
            ),
        )

        val result = api().deletePhoto("pho_1")

        assertEquals("pho_1", result.photoId)
        assertTrue(result.storageCleanupPending)
        assertEquals("/api/v1/checkin-photos/pho_1", server.takeRequest().path)
    }
}
