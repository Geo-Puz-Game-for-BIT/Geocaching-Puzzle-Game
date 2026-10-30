package com.bitse.geotreasure.core.location

import com.bitse.geotreasure.core.common.AppResult
import com.bitse.geotreasure.core.model.LocationSample
import kotlinx.coroutines.flow.Flow

interface LocationProvider {
    fun observeLocations(): Flow<LocationSample>

    suspend fun getCurrentLocation(): AppResult<LocationSample>

    fun stopLocationUpdates()
}
