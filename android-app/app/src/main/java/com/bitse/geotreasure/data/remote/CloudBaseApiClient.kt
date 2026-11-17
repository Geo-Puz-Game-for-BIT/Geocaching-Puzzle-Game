package com.bitse.geotreasure.data.remote

import com.bitse.geotreasure.core.model.CampusZone
import com.bitse.geotreasure.core.model.CircularCheckInArea
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.Landmark
import com.bitse.geotreasure.core.model.LocalOffsetMeters
import com.bitse.geotreasure.core.model.PolygonCheckInArea
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class CloudBaseApiException(
    val code: String,
    override val message: String,
    val retryable: Boolean,
) : Exception(message)

class CloudBaseApiClient(
    baseUrl: String,
    private val bearerToken: String,
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
) {
    private val baseUrl = normalizedApiBaseUrl(baseUrl)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listLandmarks(
        center: GeoPoint?,
        radiusMeters: Int?,
    ): List<Landmark> = withContext(Dispatchers.IO) {
        val query = buildList {
            if (center != null && radiusMeters != null) {
                add("latitude=${encode(center.latitude.toString())}")
                add("longitude=${encode(center.longitude.toString())}")
                add("radiusMeters=$radiusMeters")
            }
        }.joinToString("&")
        val path = if (query.isEmpty()) "/landmarks" else "/landmarks?$query"
        val (_, body) = request(path)
        val envelope = json.decodeFromString<LandmarkEnvelope>(body)
        envelope.data.map { it.toDomain() }
    }

    private fun request(path: String): Pair<String, String> {
        val connection = connectionFactory(URL("$baseUrl$path"))
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $bearerToken")
            val status = connection.responseCode
            val requestId = connection.getHeaderField("X-Request-Id").orEmpty()
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val error = runCatching {
                    json.decodeFromString<ErrorEnvelope>(body).error
                }.getOrNull()
                throw CloudBaseApiException(
                    code = error?.code ?: "HTTP_$status",
                    message = error?.message ?: "CloudBase request failed with $status",
                    retryable = error?.retryable ?: (status >= 500),
                )
            }
            requestId to body
        } catch (error: CloudBaseApiException) {
            throw error
        } catch (error: IOException) {
            throw IOException("CloudBase network request failed", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun LandmarkDto.toDomain(): Landmark {
        if (coordinateSystem != "GCJ02") {
            throw CloudBaseApiException(
                "INVALID_RESPONSE",
                "Unsupported coordinate system: $coordinateSystem",
                false,
            )
        }
        val zone = runCatching { CampusZone.valueOf(campusZone) }.getOrElse {
            throw CloudBaseApiException("INVALID_RESPONSE", "Unknown campus zone: $campusZone", false)
        }
        val area = when (checkInArea.type) {
            "circle" -> CircularCheckInArea(
                radiusMeters = requireNotNull(checkInArea.radiusMeters) {
                    "Circle check-in area is missing radiusMeters"
                },
            )
            "polygon" -> PolygonCheckInArea(
                vertices = requireNotNull(checkInArea.vertices) {
                    "Polygon check-in area is missing vertices"
                }.map { LocalOffsetMeters(it.eastMeters, it.northMeters) },
            )
            else -> throw CloudBaseApiException(
                "INVALID_RESPONSE",
                "Unknown check-in area type: ${checkInArea.type}",
                false,
            )
        }
        return Landmark(
            id = id,
            name = name,
            point = GeoPoint(point.latitude, point.longitude),
            campusZone = zone,
            fragmentId = fragmentId,
            pieceIndex = pieceIndex,
            coordinateVerified = coordinateVerified,
            checkInArea = area,
            revealPoints = revealPoints.map { GeoPoint(it.latitude, it.longitude) },
            difficulty = difficulty,
            terrain = terrain,
            hint = hint,
        )
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        const val TIMEOUT_MILLIS = 12_000
    }
}

@Serializable
private data class LandmarkEnvelope(
    val requestId: String,
    val data: List<LandmarkDto>,
)

@Serializable
private data class LandmarkDto(
    val id: String,
    val name: String,
    val point: GeoPointDto,
    val coordinateSystem: String,
    val campusZone: String,
    val fragmentId: String,
    val pieceIndex: Int,
    val coordinateVerified: Boolean,
    val checkInArea: CheckInAreaDto,
    val revealPoints: List<GeoPointDto> = emptyList(),
    val difficulty: Int,
    val terrain: Int,
    val hint: String? = null,
)

@Serializable
private data class GeoPointDto(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
private data class CheckInAreaDto(
    val type: String,
    val radiusMeters: Double? = null,
    val vertices: List<LocalOffsetDto>? = null,
)

@Serializable
private data class LocalOffsetDto(
    val eastMeters: Double,
    val northMeters: Double,
)

@Serializable
private data class ErrorEnvelope(
    val requestId: String,
    val error: ErrorBody,
)

@Serializable
private data class ErrorBody(
    val code: String,
    val message: String,
    val retryable: Boolean,
)
