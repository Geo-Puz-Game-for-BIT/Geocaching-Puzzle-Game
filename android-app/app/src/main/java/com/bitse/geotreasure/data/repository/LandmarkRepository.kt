package com.bitse.geotreasure.data.repository

import com.bitse.geotreasure.core.common.AppResult
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.Landmark

interface LandmarkRepository {
    suspend fun listLandmarks(
        center: GeoPoint?,
        radiusMeters: Int?,
    ): AppResult<List<Landmark>>

    suspend fun getLandmark(landmarkId: String): AppResult<Landmark>
}
