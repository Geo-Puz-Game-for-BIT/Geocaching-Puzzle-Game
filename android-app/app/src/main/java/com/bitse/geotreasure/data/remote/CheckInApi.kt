package com.bitse.geotreasure.data.remote

import com.bitse.geotreasure.core.model.CheckInChallenge
import com.bitse.geotreasure.core.model.CheckInReceipt
import com.bitse.geotreasure.core.model.CheckInStatus
import com.bitse.geotreasure.core.model.FragmentGrant
import com.bitse.geotreasure.core.model.SubmitCheckInRequest
import com.bitse.geotreasure.core.model.UploadedPhoto
import com.google.gson.Gson
import kotlinx.datetime.Instant
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/** 服务端错误（含错误码与是否可重试）。 */
class CheckInApiException(
    val code: String?,
    val retryable: Boolean,
    override val message: String,
) : Exception(message)

/**
 * 打卡后端 HTTP 客户端（OkHttp 同步封装，需在 IO 线程调用）。
 *
 * 对齐 D 组 cloudbase 契约（`feat/cloudbase-api-poc`）：
 * - 认证：`Authorization: Bearer <token>`；
 * - 成功响应：`{ requestId, data }`；失败响应：`{ requestId, error:{code,message,retryable} }`；
 * - 照片：multipart/form-data，仅 image/jpeg，≤5MiB；
 * - 打卡：需 `Idempotency-Key` 头（幂等去重）。
 */
class CheckInApi(
    private val baseUrl: String,
    private val authToken: String,
    private val client: OkHttpClient = defaultClient(),
    private val gson: Gson = Gson(),
) {
    private val rootUrl = baseUrl.trimEnd('/')

    fun uploadPhoto(photoFile: File): UploadedPhoto {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("photo", photoFile.name, photoFile.asRequestBody("image/jpeg".toMediaType()))
            .build()
        val response = execute(
            Request.Builder()
                .url("$rootUrl/api/v1/checkin-photos")
                .header("Authorization", "Bearer $authToken")
                .post(body)
                .build(),
        )
        val envelope = parseOrThrow<PhotoEnvelope>(response)
        return envelope.data.toDomain()
    }

    fun createChallenge(landmarkId: String): CheckInChallenge {
        val json = gson.toJson(ChallengeRequestDto(landmarkId))
        val response = execute(
            Request.Builder()
                .url("$rootUrl/api/v1/checkin-challenges")
                .header("Authorization", "Bearer $authToken")
                .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build(),
        )
        val envelope = parseOrThrow<ChallengeEnvelope>(response)
        return envelope.data.toDomain()
    }

    fun submitCheckIn(request: SubmitCheckInRequest, idempotencyKey: String): CheckInReceipt {
        val dto = request.toDto()
        val json = gson.toJson(dto)
        val response = execute(
            Request.Builder()
                .url("$rootUrl/api/v1/checkins")
                .header("Authorization", "Bearer $authToken")
                .header("Idempotency-Key", idempotencyKey)
                .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build(),
        )
        val envelope = parseOrThrow<ReceiptEnvelope>(response)
        return envelope.data.toDomain()
    }

    private fun execute(request: Request): String {
        if (rootUrl.isBlank()) {
            throw CheckInApiException("NOT_CONFIGURED", false, "打卡服务端地址未配置，请在 local.properties 填入 CHECKIN_API_BASE_URL")
        }
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw toException(body, response.code)
            }
            return body
        }
    }

    private inline fun <reified T> parseOrThrow(body: String): T =
        runCatching { gson.fromJson(body, T::class.java) }
            .getOrElse { throw CheckInApiException("INVALID_RESPONSE", true, "无法解析服务端响应") }

    private fun toException(body: String, httpStatus: Int): CheckInApiException {
        val error = runCatching {
            gson.fromJson(body, ErrorEnvelope::class.java).error
        }.getOrNull()
        return CheckInApiException(
            code = error?.code,
            retryable = error?.retryable ?: (httpStatus >= 500),
            message = error?.message ?: "请求失败（HTTP $httpStatus）",
        )
    }

    companion object {
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

// ---------- DTO（JSON 字段与 D 组契约一一对应） ----------

private data class ChallengeRequestDto(val landmarkId: String)

private data class ChallengeEnvelope(val requestId: String, val data: ChallengeDto)
private data class ChallengeDto(val id: String, val landmarkId: String, val expiresAt: String) {
    fun toDomain() = CheckInChallenge(id = id, landmarkId = landmarkId, expiresAt = Instant.parse(expiresAt))
}

private data class PhotoEnvelope(val requestId: String, val data: PhotoDto)
private data class PhotoDto(val photoId: String, val objectKey: String, val uploadedAt: String) {
    fun toDomain() = UploadedPhoto(photoId = photoId, objectKey = objectKey, uploadedAt = Instant.parse(uploadedAt))
}

private data class ReceiptEnvelope(val requestId: String, val data: ReceiptDto)
private data class ReceiptDto(val checkInId: String, val status: String, val fragmentGrant: FragmentGrantDto) {
    fun toDomain() = CheckInReceipt(
        checkInId = checkInId,
        status = CheckInStatus.valueOf(status),
        fragmentGrant = FragmentGrant(
            fragmentId = fragmentGrant.fragmentId,
            puzzleId = fragmentGrant.puzzleId,
            pieceIndex = fragmentGrant.pieceIndex,
            grantedAt = Instant.parse(fragmentGrant.grantedAt),
        ),
    )
}
private data class FragmentGrantDto(
    val fragmentId: String,
    val puzzleId: String,
    val pieceIndex: Int,
    val grantedAt: String,
)

private data class CheckInRequestDto(
    val landmarkId: String,
    val challengeId: String,
    val photoId: String,
    val location: LocationDto,
)
private data class LocationDto(
    val point: PointDto,
    val accuracyMeters: Float,
    val capturedAt: String,
)
private data class PointDto(val latitude: Double, val longitude: Double)

private fun SubmitCheckInRequest.toDto() = CheckInRequestDto(
    landmarkId = landmarkId,
    challengeId = challengeId,
    photoId = photoId,
    location = LocationDto(
        point = PointDto(latitude = location.point.latitude, longitude = location.point.longitude),
        accuracyMeters = location.accuracyMeters,
        capturedAt = location.capturedAt.toString(),
    ),
)

private data class ErrorEnvelope(val requestId: String?, val error: ErrorDto?)
private data class ErrorDto(val code: String?, val message: String?, val retryable: Boolean = false)
