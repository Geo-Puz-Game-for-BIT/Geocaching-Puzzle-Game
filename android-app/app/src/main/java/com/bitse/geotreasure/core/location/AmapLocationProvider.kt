package com.bitse.geotreasure.core.location

import android.content.Context
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.bitse.geotreasure.core.common.AppError
import com.bitse.geotreasure.core.common.AppResult
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.LocationSample
import com.bitse.geotreasure.core.model.LocationSourceKind
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Instant

class AmapLocationProvider(
    context: Context,
    private val privacyManager: LocationPrivacyManager,
) : LocationProvider {
    private val appContext = context.applicationContext
    private var client: AMapLocationClient? = null

    override fun observeLocations(): Flow<LocationSample> = callbackFlow {
        if (!privacyManager.isAccepted.value) {
            close(IllegalStateException("AMap privacy consent is required before location starts"))
            return@callbackFlow
        }

        val locationClient = getOrCreateClient()
        locationClient.setLocationListener { location ->
            if (location != null && location.errorCode == 0) {
                trySend(
                    LocationSample(
                        point = GeoPoint(location.latitude, location.longitude),
                        accuracyMeters = location.accuracy,
                        capturedAt = Instant.fromEpochMilliseconds(location.time),
                        source = location.toSourceKind(),
                    ),
                )
            }
        }
        locationClient.startLocation()
        awaitClose { locationClient.stopLocation() }
    }

    override suspend fun getCurrentLocation(): AppResult<LocationSample> {
        val sample = withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) {
            observeLocations().first()
        }
        return if (sample != null) {
            AppResult.Success(sample)
        } else {
            AppResult.Failure(
                AppError(
                    code = "LOCATION_TIMEOUT",
                    message = "定位超时，请移动到开阔区域后重试。",
                    retryable = true,
                ),
            )
        }
    }

    override fun stopLocationUpdates() {
        client?.stopLocation()
    }

    private fun getOrCreateClient(): AMapLocationClient = client ?: AMapLocationClient(appContext).also {
        it.setLocationOption(
            AMapLocationClientOption().apply {
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                interval = LOCATION_INTERVAL_MILLIS
                isOnceLocation = false
                isNeedAddress = false
                setGpsFirst(true)
                setGpsFirstTimeout(GPS_FIRST_TIMEOUT_MILLIS)
                setLocationCacheEnable(false)
                setSensorEnable(true)
                setWifiScan(true)
                setMockEnable(false)
            },
        )
        client = it
    }

    companion object {
        private const val LOCATION_INTERVAL_MILLIS = 3_000L
        private const val LOCATION_TIMEOUT_MILLIS = 12_000L
        private const val GPS_FIRST_TIMEOUT_MILLIS = 10_000L
    }
}

private fun AMapLocation.toSourceKind(): LocationSourceKind = when (locationType) {
    AMapLocation.LOCATION_TYPE_GPS -> LocationSourceKind.GPS
    AMapLocation.LOCATION_TYPE_WIFI -> LocationSourceKind.WIFI
    AMapLocation.LOCATION_TYPE_CELL -> LocationSourceKind.CELL
    AMapLocation.LOCATION_TYPE_FIX_CACHE,
    AMapLocation.LOCATION_TYPE_LAST_LOCATION_CACHE,
    AMapLocation.LOCATION_TYPE_OFFLINE,
    -> LocationSourceKind.CACHE
    AMapLocation.LOCATION_TYPE_NETWORK,
    AMapLocation.LOCATION_TYPE_AMAP,
    AMapLocation.LOCATION_TYPE_SAME_REQ,
    AMapLocation.LOCATION_TYPE_COARSE_LOCATION,
    -> LocationSourceKind.NETWORK
    else -> LocationSourceKind.UNKNOWN
}
