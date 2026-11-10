package com.bitse.geotreasure.data.repository

import com.bitse.geotreasure.core.common.AppError
import com.bitse.geotreasure.core.common.AppResult
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.Landmark
import com.bitse.geotreasure.data.remote.CloudBaseApiClient
import com.bitse.geotreasure.data.remote.CloudBaseApiException
import java.io.IOException

class CloudBaseLandmarkRepository(
    private val client: CloudBaseApiClient,
) : LandmarkRepository {
    override suspend fun listLandmarks(
        center: GeoPoint?,
        radiusMeters: Int?,
    ): AppResult<List<Landmark>> = execute {
        client.listLandmarks(center, radiusMeters)
    }

    override suspend fun getLandmark(landmarkId: String): AppResult<Landmark> = execute {
        client.listLandmarks(center = null, radiusMeters = null)
            .firstOrNull { it.id == landmarkId }
            ?: throw CloudBaseApiException(
                "LANDMARK_NOT_FOUND",
                "没有找到这个地标。",
                false,
            )
    }

    private suspend fun <T> execute(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (error: CloudBaseApiException) {
        AppResult.Failure(
            AppError(
                code = error.code,
                message = error.message,
                retryable = error.retryable,
                cause = error,
            ),
        )
    } catch (error: IOException) {
        AppResult.Failure(
            AppError(
                code = "NETWORK_ERROR",
                message = "无法连接 CloudBase，请检查网络后重试。",
                retryable = true,
                cause = error,
            ),
        )
    } catch (error: Exception) {
        AppResult.Failure(
            AppError(
                code = "INVALID_RESPONSE",
                message = "CloudBase 返回了无效数据。",
                retryable = false,
                cause = error,
            ),
        )
    }
}
