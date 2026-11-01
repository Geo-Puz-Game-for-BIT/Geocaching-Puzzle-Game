package com.bitse.geotreasure.data.repository

import com.bitse.geotreasure.core.model.CheckInReceipt
import com.bitse.geotreasure.core.model.LocationSample
import com.bitse.geotreasure.core.model.SubmitCheckInRequest
import com.bitse.geotreasure.core.model.UploadedPhoto
import com.bitse.geotreasure.data.remote.CheckInApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 打卡提交编排接口（便于单测注入 fake）。 */
interface CheckInRepository {
    /** 上传照片，返回服务端 photoId（对应 UploadState.UPLOADED）。 */
    suspend fun uploadPhoto(photoFile: File): UploadedPhoto

    /** 领一次性 challenge 后提交打卡，返回 CheckInReceipt（对应 UploadState.CONFIRMED）。 */
    suspend fun submitCheckIn(
        landmarkId: String,
        photoId: String,
        location: LocationSample,
        idempotencyKey: String,
    ): CheckInReceipt
}

/**
 * 打卡上传编排：上传照片 → 得 photoId →（重试时可跳过本步，复用 photoId）
 * → 领一次性 challenge → POST /checkins → 收 CheckInReceipt。
 * 幂等键 [idempotencyKey] 使用草稿 id（同一草稿重复提交走服务端幂等，不重复发奖）。
 */
class RemoteCheckInRepository(private val api: CheckInApi) : CheckInRepository {
    override suspend fun uploadPhoto(photoFile: File): UploadedPhoto =
        withContext(Dispatchers.IO) { api.uploadPhoto(photoFile) }

    override suspend fun submitCheckIn(
        landmarkId: String,
        photoId: String,
        location: LocationSample,
        idempotencyKey: String,
    ): CheckInReceipt = withContext(Dispatchers.IO) {
        val challenge = api.createChallenge(landmarkId)
        api.submitCheckIn(
            request = SubmitCheckInRequest(
                landmarkId = landmarkId,
                challengeId = challenge.id,
                photoId = photoId,
                location = location,
            ),
            idempotencyKey = idempotencyKey,
        )
    }
}
