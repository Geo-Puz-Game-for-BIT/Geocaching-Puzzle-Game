package com.bitse.geotreasure.app

import android.content.Context
import com.bitse.geotreasure.BuildConfig
import com.bitse.geotreasure.core.camera.CameraXManager
import com.bitse.geotreasure.core.location.AmapLocationProvider
import com.bitse.geotreasure.core.location.DemoLocationProvider
import com.bitse.geotreasure.core.location.LocationPrivacyManager
import com.bitse.geotreasure.core.location.LocationProvider
import com.bitse.geotreasure.data.repository.CheckInDraftStore
import com.bitse.geotreasure.data.repository.CloudBaseLandmarkRepository
import com.bitse.geotreasure.data.repository.FakeLandmarkRepository
import com.bitse.geotreasure.data.repository.LandmarkRepository
import com.bitse.geotreasure.data.remote.CloudBaseApiClient

class AppContainer(private val context: Context) {
    val privacyManager = LocationPrivacyManager(context)
    val explorationProgressStore = ExplorationProgressStore(context)
    val checkInDraftStore = CheckInDraftStore(context)
    val landmarkRepository: LandmarkRepository = if (BuildConfig.HAS_CLOUD_API) {
        CloudBaseLandmarkRepository(
            CloudBaseApiClient(
                baseUrl = BuildConfig.API_BASE_URL,
                bearerToken = BuildConfig.DEMO_AUTH_TOKEN,
            ),
        )
    } else {
        FakeLandmarkRepository()
    }
    val locationProvider: LocationProvider = if (BuildConfig.HAS_AMAP_KEY) {
        AmapLocationProvider(context, privacyManager)
    } else {
        DemoLocationProvider()
    }

    fun createCameraManager(): CameraXManager = CameraXManager(context)
}
