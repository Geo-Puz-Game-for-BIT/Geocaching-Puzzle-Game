package com.bitse.geotreasure.app

import android.content.Context
import com.bitse.geotreasure.BuildConfig
import com.bitse.geotreasure.core.location.AmapLocationProvider
import com.bitse.geotreasure.core.location.DemoLocationProvider
import com.bitse.geotreasure.core.location.LocationPrivacyManager
import com.bitse.geotreasure.core.location.LocationProvider
import com.bitse.geotreasure.data.repository.FakeLandmarkRepository
import com.bitse.geotreasure.data.repository.LandmarkRepository

class AppContainer(context: Context) {
    val privacyManager = LocationPrivacyManager(context)
    val explorationProgressStore = ExplorationProgressStore(context)
    val landmarkRepository: LandmarkRepository = FakeLandmarkRepository()
    val locationProvider: LocationProvider = if (BuildConfig.HAS_AMAP_KEY) {
        AmapLocationProvider(context, privacyManager)
    } else {
        DemoLocationProvider()
    }
}
