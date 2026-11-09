package com.bitse.geotreasure.data.remote

import com.bitse.geotreasure.core.checkin.CheckInQueryResult
import com.bitse.geotreasure.core.model.CheckInChallenge
import com.bitse.geotreasure.core.model.CheckInReceipt
import com.bitse.geotreasure.core.model.CheckInStatus
import com.bitse.geotreasure.core.model.FragmentGrant
import com.bitse.geotreasure.core.model.PhotoDeletionResult
import com.bitse.geotreasure.core.model.SubmitCheckInRequest
import com.bitse.geotreasure.core.model.UploadedPhoto
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder
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
    private val rootUrl = normalizedApiBaseUrl(baseUrl)

    fun uploadPhoto(
        photoFile: File,
        challengeId: String,
        attemptId: String,
    ): UploadedPhoto {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("photo", photoFile.name, photoFile.asRequestBody("image/jpeg".toMediaType()))
            .build()
        val response = execute(
            Request.Builder()
                .url("$rootUrl/checkin-photos")
                .header("Authorization", "Bearer $authToken")
                .header("X-Challenge-Id", challengeId)
                .header("X-Attempt-Id", attemptId)
                .post(body)
                .build(),
        )
        val envelope = parseOrThrow<PhotoEnvelope>(response)
        return envelope.data.toDomain()
    }

    fun createChallenge(landmarkId: String, attemptId: String): CheckInChallenge {
        val json = gson.toJson(ChallengeRequestDto(landmarkId, attemptId))
        val response = execute(
            Request.Builder()
                .url("$rootUrl/checkin-challenges")
                .header("Authorization", "Bearer $authToken")
                .header("X-Attempt-Id", attemptId)
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
                .url("$rootUrl/checkins")
                .header("Authorization", "Bearer $authToken")
                .header("Idempotency-Key", idempotencyKey)
                .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build(),
        )
        val envelope = parseOrThrow<ReceiptEnvelope>(response)
        return envelope.data.toDomain()
    }

    /**
     * 按 attempt 查询本次尝试是否已成功（幂等恢复用），返回有类型结果。
     *
     * 契约（`docs/api/checkin-recovery.md`，D 服务端已实现 `GET /checkins?attemptId=`）：
     * - 已成功 → `200 { requestId, data: { checkInId, status, fragmentGrant } }` → [CheckInQueryResult.Success]；
     * - 处理中 → `202 { requestId, data: { status: "PROCESSING" } }` → [CheckInQueryResult.Processing]；
     * - 明确无记录 → `404 { requestId, error: { code: "CHECKIN_NOT_FOUND" } }` → [CheckInQueryResult.NotFound]；
     * - 其它（普通 404 / 405 / 5xx / 超时 / 未配置）→ [CheckInQueryResult.Unavailable]（查询故障，结果未知）。
     *
     * 关键：普通路由 404、代理 404 与「未配置」**不得**当作「明确无记录」，否则会误删旧照、放开重拍。
     */
    fun queryCheckIn(attemptId: String): CheckInQueryResult {
        if (rootUrl.isBlank()) return CheckInQueryResult.Unavailable
        val request = Request.Builder()
            .url(
                "$rootUrl/checkins?attemptId=${
                    URLEncoder.encode(attemptId, Charsets.UTF_8.name())
                }",
            )
            .header("Authorization", "Bearer $authToken")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                when {
                    resp.code == 200 -> {
                        val envelope = parseOrThrow<QueryEnvelope>(body)
                        val data = envelope.data
                            ?: return CheckInQueryResult.Unavailable
                        CheckInQueryResult.Success(data.toDomain())
                    }
                    resp.code == 202 -> CheckInQueryResult.Processing
                    resp.code == 404 && isCheckInNotFound(body) -> CheckInQueryResult.NotFound
                    else -> CheckInQueryResult.Unavailable
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CheckInQueryResult.Unavailable
        }
    }

    fun deletePhoto(photoId: String): PhotoDeletionResult {
        val encodedId = URLEncoder.encode(photoId, Charsets.UTF_8.name())
        val response = execute(
            Request.Builder()
                .url("$rootUrl/checkin-photos/$encodedId")
                .header("Authorization", "Bearer $authToken")
                .delete()
                .build(),
        )
        return parseOrThrow<PhotoDeletionEnvelope>(response).data.toDomain()
    }

    /** 仅当错误体明确携带 `error.code == "CHECKIN_NOT_FOUND"` 才视为「明确无记录」。 */
    private fun isCheckInNotFound(body: String): Boolean =
        runCatching { gson.fromJson(body, CheckInErrorEnvelope::class.java).error?.code }
            .getOrNull() == "CHECKIN_NOT_FOUND"

    private fun execute(request: Request): String {
        if (rootUrl.isBlank()) {
            throw CheckInApiException("NOT_CONFIGURED", false, "打卡服务端地址未配置，请在 local.properties 填入 API_BASE_URL")
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
            gson.fromJson(body, CheckInErrorEnvelope::class.java).error
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

private data class ChallengeRequestDto(val landmarkId: String, val attemptId: String)

private data class ChallengeEnvelope(val requestId: String, val data: ChallengeDto)
private data class ChallengeDto(val id: String, val landmarkId: String, val expiresAt: String) {
    fun toDomain() = CheckInChallenge(id = id, landmarkId = landmarkId, expiresAt = Instant.parse(expiresAt))
}

private data class PhotoEnvelope(val requestId: String, val data: PhotoDto)
private data class PhotoDto(val photoId: String, val objectKey: String, val uploadedAt: String) {
    fun toDomain() = UploadedPhoto(photoId = photoId, objectKey = objectKey, uploadedAt = Instant.parse(uploadedAt))
}

private data class PhotoDeletionEnvelope(val requestId: String, val data: PhotoDeletionDto)
private data class PhotoDeletionDto(
    val photoId: String,
    val status: String,
    val storageCleanup: String,
    val deleteAfter: String,
) {
    fun toDomain() = PhotoDeletionResult(
        photoId = photoId,
        storageCleanupPending = storageCleanup == "PENDING",
        deleteAfter = Instant.parse(deleteAfter),
    )
}

private data class QueryEnvelope(val requestId: String?, val data: ReceiptDto?)

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
    val source: String,
    val elapsedRealtimeNanos: Long?,
    val submittedElapsedRealtimeNanos: Long?,
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
        source = location.source.name,
        elapsedRealtimeNanos = location.elapsedRealtimeNanos,
        submittedElapsedRealtimeNanos = location.submittedElapsedRealtimeNanos,
    ),
)

private data class CheckInErrorEnvelope(val requestId: String?, val error: CheckInErrorDto?)
private data class CheckInErrorDto(val code: String?, val message: String?, val retryable: Boolean = false)
