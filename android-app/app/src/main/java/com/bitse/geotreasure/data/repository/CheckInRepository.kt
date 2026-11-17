package com.bitse.geotreasure.data.repository

import com.bitse.geotreasure.core.checkin.CheckInQueryResult
import com.bitse.geotreasure.core.model.CheckInChallenge
import com.bitse.geotreasure.core.model.CheckInReceipt
import com.bitse.geotreasure.core.model.SubmitCheckInRequest
import com.bitse.geotreasure.core.model.UploadedPhoto
import com.bitse.geotreasure.data.remote.CheckInApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 打卡后端编排接口（便于单测注入 fake）。 */
interface CheckInRepository {
    /** 上传照片，返回服务端 photoId。 */
    suspend fun uploadPhoto(photoFile: File): UploadedPhoto

    /**
     * 领一次性 challenge（每次尝试一次，不在 submit 内重复领取）。
     * [attemptId] 为本次尝试的幂等键：challenge 在**拍摄前**就与 attempt 绑定（D 契约），
     * 拍摄成功后客户端采用同一 attemptId 提交，保证 challenge↔attempt 一致。
     */
    suspend fun createChallenge(landmarkId: String, attemptId: String): CheckInChallenge

    /** 提交打卡（幂等键 [idempotencyKey] = attemptId，内容须已冻结）。 */
    suspend fun submitCheckIn(request: SubmitCheckInRequest, idempotencyKey: String): CheckInReceipt

    /** 按 attemptId 查询本次尝试是否已成功（幂等恢复用），返回有类型结果。 */
    suspend fun queryCheckIn(attemptId: String): CheckInQueryResult
}

/**
 * 打卡后端实现：上传照片 → 领 challenge → POST /checkins（幂等）→ 收回执。
 * 编排（冻结 / 重放 / 未知先查）由上层 CheckInSubmitCoordinator 负责，本类只做网络调用。
 */
class RemoteCheckInRepository(private val api: CheckInApi) : CheckInRepository {
    override suspend fun uploadPhoto(photoFile: File): UploadedPhoto =
        withContext(Dispatchers.IO) { api.uploadPhoto(photoFile) }

    override suspend fun createChallenge(landmarkId: String, attemptId: String): CheckInChallenge =
        withContext(Dispatchers.IO) { api.createChallenge(landmarkId, attemptId) }

    override suspend fun submitCheckIn(
        request: SubmitCheckInRequest,
        idempotencyKey: String,
    ): CheckInReceipt = withContext(Dispatchers.IO) { api.submitCheckIn(request, idempotencyKey) }

    override suspend fun queryCheckIn(attemptId: String): CheckInQueryResult =
        withContext(Dispatchers.IO) { api.queryCheckIn(attemptId) }
}
