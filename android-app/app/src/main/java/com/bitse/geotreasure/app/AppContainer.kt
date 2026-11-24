package com.bitse.geotreasure.app

import android.content.Context
import com.bitse.geotreasure.BuildConfig
import com.bitse.geotreasure.core.account.AccountSession
import com.bitse.geotreasure.core.account.asTokenProvider
import com.bitse.geotreasure.core.camera.CameraXManager
import com.bitse.geotreasure.core.camera.FaceQualityChecker
import com.bitse.geotreasure.core.checkin.CheckInSubmitCoordinator
import com.bitse.geotreasure.core.location.AmapLocationProvider
import com.bitse.geotreasure.core.location.DemoLocationProvider
import com.bitse.geotreasure.core.location.LocationPrivacyManager
import com.bitse.geotreasure.core.location.LocationProvider
import com.bitse.geotreasure.data.account.CheckInSealStore
import com.bitse.geotreasure.data.account.DemoAccountSession
import com.bitse.geotreasure.data.remote.CheckInApi
import com.bitse.geotreasure.data.remote.CloudBaseApiClient
import com.bitse.geotreasure.data.repository.CheckInDraftStore
import com.bitse.geotreasure.data.repository.CheckInRepository
import com.bitse.geotreasure.data.repository.CloudBaseLandmarkRepository
import com.bitse.geotreasure.data.repository.FakeLandmarkRepository
import com.bitse.geotreasure.data.repository.LandmarkRepository
import com.bitse.geotreasure.data.repository.RemoteCheckInRepository

class AppContainer(private val context: Context) {
    /** 账号会话（D1 落地前为 Demo 兜底）。 */
    val accountSession: AccountSession = DemoAccountSession()

    val privacyManager = LocationPrivacyManager(context)
    val explorationProgressStore = ExplorationProgressStore(context, accountSession)
    val checkInDraftStore = CheckInDraftStore(context, accountSession)
    val checkInSealStore = CheckInSealStore(context)

    private val tokenProvider = accountSession.asTokenProvider()

    val landmarkRepository: LandmarkRepository = if (BuildConfig.HAS_CLOUD_API) {
        CloudBaseLandmarkRepository(
            CloudBaseApiClient(
                baseUrl = BuildConfig.API_BASE_URL,
                authProvider = tokenProvider,
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
    val checkInRepository: CheckInRepository = RemoteCheckInRepository(
        CheckInApi(
            baseUrl = BuildConfig.API_BASE_URL,
            authProvider = tokenProvider,
        ),
    )
    val submitCoordinator = CheckInSubmitCoordinator(checkInRepository)

    fun createCameraManager(): CameraXManager = CameraXManager(context)

    fun createFaceQualityChecker(): FaceQualityChecker = FaceQualityChecker()
}
