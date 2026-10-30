package com.bitse.geotreasure.data.repository

import com.bitse.geotreasure.core.common.AppError
import com.bitse.geotreasure.core.common.AppResult
import com.bitse.geotreasure.core.location.distanceMeters
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.Landmark
import com.bitse.geotreasure.core.model.LiangxiangTreasureCatalog

class FakeLandmarkRepository : LandmarkRepository {
    private val landmarks = LiangxiangTreasureCatalog.landmarks

    override suspend fun listLandmarks(
        center: GeoPoint?,
        radiusMeters: Int?,
    ): AppResult<List<Landmark>> {
        val filtered = if (center != null && radiusMeters != null) {
            landmarks.filter { distanceMeters(center, it.point) <= radiusMeters }
        } else {
            landmarks
        }
        return AppResult.Success(filtered)
    }

    override suspend fun getLandmark(landmarkId: String): AppResult<Landmark> {
        val landmark = landmarks.firstOrNull { it.id == landmarkId }
        return if (landmark != null) {
            AppResult.Success(landmark)
        } else {
            AppResult.Failure(
                AppError(
                    code = "LANDMARK_NOT_FOUND",
                    message = "没有找到这个地标。",
                    retryable = false,
                ),
            )
        }
    }
}
