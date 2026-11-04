package com.bitse.geotreasure.data.remote

import com.bitse.geotreasure.core.checkin.CheckInQueryResult
import java.util.concurrent.TimeUnit
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
        return CheckInApi(baseUrl = server.url("/").toString(), authToken = "tok_1", client = client)
    }

    private fun receiptJson() = """{"requestId":"req_1","data":{"checkInId":"chk_1","status":"COMPLETED","fragmentGrant":{"fragmentId":"frg_1","puzzleId":"pzl_1","pieceIndex":0,"grantedAt":"2024-01-01T00:00:00Z"}}}"""

    @Test
    fun query_200_returnsSuccess() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(receiptJson()))
        val result = api().queryCheckIn("att_1")
        assertTrue(result is CheckInQueryResult.Success)
        assertEquals("chk_1", (result as CheckInQueryResult.Success).receipt.checkInId)
        assertEquals("/checkins?attemptId=att_1", server.takeRequest().path)
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
}
