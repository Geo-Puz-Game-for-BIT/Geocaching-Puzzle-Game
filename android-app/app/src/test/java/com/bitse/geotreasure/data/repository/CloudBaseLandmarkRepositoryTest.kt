package com.bitse.geotreasure.data.repository

import com.bitse.geotreasure.core.common.AppResult
import com.bitse.geotreasure.core.model.CampusZone
import com.bitse.geotreasure.core.model.CircularCheckInArea
import com.bitse.geotreasure.data.remote.CloudBaseApiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class CloudBaseLandmarkRepositoryTest {
    @Test
    fun `maps the CloudBase landmark response into the Android domain model`() = runBlocking {
        withServer(200, successBody) { baseUrl ->
            val repository = remoteRepository(baseUrl)

            val result = repository.listLandmarks(center = null, radiusMeters = null)

            assertTrue(result is AppResult.Success)
            val landmark = (result as AppResult.Success).data.single()
            assertEquals("lmk_north_lake", landmark.id)
            assertEquals("北湖", landmark.name)
            assertEquals(CampusZone.NORTH, landmark.campusZone)
            assertEquals("fragment_north_lake", landmark.fragmentId)
            assertEquals(0, landmark.pieceIndex)
            assertEquals(CircularCheckInArea(55.0), landmark.checkInArea)
        }
    }

    @Test
    fun `maps unauthorized response to AUTH_REQUIRED`() = runBlocking {
        withServer(401, errorBody("AUTH_REQUIRED")) { baseUrl ->
            val result = remoteRepository(baseUrl).listLandmarks(null, null)

            assertTrue(result is AppResult.Failure)
            assertEquals("AUTH_REQUIRED", (result as AppResult.Failure).error.code)
        }
    }

    @Test
    fun `returns retryable network error when CloudBase is unreachable`() = runBlocking {
        val result = remoteRepository("http://127.0.0.1:1/api/v1").listLandmarks(null, null)

        assertTrue(result is AppResult.Failure)
        val error = (result as AppResult.Failure).error
        assertEquals("NETWORK_ERROR", error.code)
        assertTrue(error.retryable)
    }

    private fun remoteRepository(baseUrl: String): CloudBaseLandmarkRepository =
        CloudBaseLandmarkRepository(
            CloudBaseApiClient(
                baseUrl = baseUrl,
                bearerToken = "test-token",
            ),
        )

    private suspend fun withServer(
        status: Int,
        body: String,
        block: suspend (String) -> Unit,
    ) {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setHeader("X-Request-Id", "123e4567-e89b-42d3-a456-426614174000")
                .setBody(body),
        )
        server.start()
        try {
            block(server.url("/api/v1").toString().trimEnd('/'))
        } finally {
            server.shutdown()
        }
    }

    private companion object {
        val successBody = """
            {
              "requestId": "123e4567-e89b-42d3-a456-426614174000",
              "data": [
                {
                  "id": "lmk_north_lake",
                  "name": "北湖",
                  "point": {"latitude": 39.7366, "longitude": 116.1666},
                  "coordinateSystem": "GCJ02",
                  "campusZone": "NORTH",
                  "fragmentId": "fragment_north_lake",
                  "pieceIndex": 0,
                  "coordinateVerified": false,
                  "checkInArea": {"type": "circle", "radiusMeters": 55},
                  "difficulty": 1,
                  "terrain": 1,
                  "hint": "沿湖岸寻找取景点。",
                  "task": {
                    "id": "task_north_lake",
                    "type": "photo",
                    "instructions": "在北湖完成现场拍照并上传。",
                    "puzzleId": "puzzle_liangxiang_campus",
                    "fragmentId": "fragment_north_lake"
                  }
                }
              ]
            }
        """.trimIndent()

        fun errorBody(code: String): String = """
            {
              "requestId": "123e4567-e89b-42d3-a456-426614174000",
              "error": {
                "code": "$code",
                "message": "Unauthorized",
                "retryable": false
              }
            }
        """.trimIndent()
    }
}
