package com.bitse.geotreasure.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bitse.geotreasure.app.ExplorationProgressStore
import com.bitse.geotreasure.core.common.AppResult
import com.bitse.geotreasure.core.location.LocationPrivacyManager
import com.bitse.geotreasure.core.location.LocationProvider
import com.bitse.geotreasure.core.location.distanceMeters
import com.bitse.geotreasure.core.model.CheckInArea
import com.bitse.geotreasure.core.model.CircularCheckInArea
import com.bitse.geotreasure.core.model.LocalOffsetMeters
import com.bitse.geotreasure.core.model.PolygonCheckInArea
import com.bitse.geotreasure.data.repository.LandmarkRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

class MapViewModel(
    private val landmarkRepository: LandmarkRepository,
    private val locationProvider: LocationProvider,
    private val privacyManager: LocationPrivacyManager,
    private val explorationProgressStore: ExplorationProgressStore,
    hasAmapKey: Boolean,
    debugEnabled: Boolean,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        MapUiState(
            privacyAccepted = privacyManager.isAccepted.value,
            usingDemoMap = !hasAmapKey,
            debugEnabled = debugEnabled,
        ),
    )
    val uiState: StateFlow<MapUiState> = mutableUiState.asStateFlow()

    private val effectChannel = Channel<MapEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private var locationJob: Job? = null
    private var locationWatchdogJob: Job? = null

    init {
        loadLandmarks()
        viewModelScope.launch {
            explorationProgressStore.completedLandmarkIds.collect { completedIds ->
                mutableUiState.update { it.copy(completedLandmarkIds = completedIds).withUpdatedDistance() }
            }
        }
    }

    fun onAction(action: MapAction) {
        when (action) {
            MapAction.AcceptPrivacy -> {
                privacyManager.accept()
                mutableUiState.update { it.copy(privacyAccepted = true) }
                startLocationIfReady()
            }

            MapAction.RequestLocationPermission -> {
                mutableUiState.update { it.copy(permissionRequestCount = it.permissionRequestCount + 1) }
                effectChannel.trySend(MapEffect.RequestLocationPermission)
            }

            MapAction.OpenAppSettings -> effectChannel.trySend(MapEffect.OpenAppSettings)
            MapAction.Retry -> {
                loadLandmarks()
                startLocationIfReady()
            }

            is MapAction.PermissionSnapshot -> {
                mutableUiState.update {
                    it.copy(
                        permissionState = if (action.granted) {
                            LocationPermissionState.GRANTED
                        } else {
                            when (it.permissionState) {
                                LocationPermissionState.GRANTED -> LocationPermissionState.DENIED
                                else -> it.permissionState
                            }
                        },
                    )
                }
                if (action.granted) {
                    startLocationIfReady()
                } else {
                    stopLocation()
                }
            }

            is MapAction.PermissionResult -> {
                val requestCount = mutableUiState.value.permissionRequestCount
                val permissionState = when {
                    action.granted -> LocationPermissionState.GRANTED
                    requestCount >= 2 && !action.canAskAgain -> LocationPermissionState.PERMANENTLY_DENIED
                    else -> LocationPermissionState.DENIED
                }
                mutableUiState.update { it.copy(permissionState = permissionState) }
                if (action.granted) {
                    startLocationIfReady()
                } else {
                    stopLocation()
                }
            }

            is MapAction.SelectLandmark -> selectLandmark(action.landmarkId)
            is MapAction.OpenLandmark -> effectChannel.trySend(MapEffect.NavigateToLandmark(action.landmarkId))
            MapAction.ToggleDebugPanel -> mutableUiState.update { it.copy(debugPanelExpanded = !it.debugPanelExpanded) }
            MapAction.ToggleDebugRevealAll -> mutableUiState.update { it.copy(debugRevealAll = !it.debugRevealAll) }
            MapAction.ToggleDebugScoutRange -> mutableUiState.update { it.copy(debugShowScoutRange = !it.debugShowScoutRange) }
            is MapAction.AdjustDebugRadius -> adjustDebugRadius(action.deltaMeters)
            is MapAction.SetDebugRangeShape -> setDebugRangeShape(action.shape)
            MapAction.DebugCompleteSelectedLandmark -> mutableUiState.value.selectedLandmarkId?.let {
                explorationProgressStore.markLandmarkCompleted(it)
            }
        }
    }

    private fun loadLandmarks() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, message = null) }
            when (val result = landmarkRepository.listLandmarks(center = null, radiusMeters = null)) {
                is AppResult.Success -> mutableUiState.update { state ->
                    val selectedId = state.selectedLandmarkId ?: result.data.firstOrNull()?.id
                    state.copy(
                        isLoading = false,
                        landmarks = result.data,
                        selectedLandmarkId = selectedId,
                    ).withUpdatedDistance()
                }

                is AppResult.Failure -> mutableUiState.update {
                    it.copy(isLoading = false, message = result.error.message)
                }
            }
        }
    }

    private fun selectLandmark(landmarkId: String) {
        mutableUiState.update {
            it.copy(selectedLandmarkId = landmarkId).withUpdatedDistance()
        }
    }

    private fun adjustDebugRadius(deltaMeters: Double) {
        val state = mutableUiState.value
        val landmark = state.landmarks.firstOrNull { it.id == state.selectedLandmarkId } ?: return
        val currentArea = state.debugAreaOverrides[landmark.id] ?: landmark.checkInArea
        val nextRadius = (currentArea.debugRadiusMeters() + deltaMeters).coerceIn(20.0, 300.0)
        mutableUiState.update {
            it.copy(debugAreaOverrides = it.debugAreaOverrides + (landmark.id to currentArea.withDebugRadius(nextRadius)))
        }
    }

    private fun setDebugRangeShape(shape: DebugRangeShape) {
        val state = mutableUiState.value
        val landmark = state.landmarks.firstOrNull { it.id == state.selectedLandmarkId } ?: return
        val currentArea = state.debugAreaOverrides[landmark.id] ?: landmark.checkInArea
        val radius = currentArea.debugRadiusMeters()
        val nextArea = when (shape) {
            DebugRangeShape.CIRCLE -> CircularCheckInArea(radius)
            DebugRangeShape.RECTANGLE -> rectangleArea(radius)
        }
        mutableUiState.update {
            it.copy(debugAreaOverrides = it.debugAreaOverrides + (landmark.id to nextArea))
        }
    }

    private fun startLocationIfReady() {
        val state = mutableUiState.value
        if (!state.privacyAccepted || state.permissionState != LocationPermissionState.GRANTED) return
        if (locationJob?.isActive == true) return

        locationJob = viewModelScope.launch {
            scheduleLocationWatchdog()
            locationProvider.observeLocations()
                .catch { throwable ->
                    locationWatchdogJob?.cancel()
                    mutableUiState.update {
                        it.copy(
                            locationSignalActive = false,
                            message = throwable.message ?: "定位暂时不可用，请稍后重试。",
                        )
                    }
                }
                .collect { location ->
                    scheduleLocationWatchdog()
                    mutableUiState.update {
                        it.copy(
                            userLocation = location,
                            locationSignalActive = true,
                            message = null,
                            exploredCenters = updatedExploredCenters(it.exploredCenters, location),
                        ).withUpdatedDistance()
                    }
                }
        }
    }

    private fun stopLocation() {
        locationJob?.cancel()
        locationJob = null
        locationWatchdogJob?.cancel()
        locationWatchdogJob = null
        locationProvider.stopLocationUpdates()
        mutableUiState.update {
            it.copy(userLocation = null, locationSignalActive = false, distanceToSelectedMeters = null)
        }
    }

    private fun scheduleLocationWatchdog() {
        locationWatchdogJob?.cancel()
        locationWatchdogJob = viewModelScope.launch {
            delay(LOCATION_SIGNAL_TIMEOUT_MILLIS)
            mutableUiState.update { state ->
                state.copy(
                    locationSignalActive = false,
                    message = if (state.userLocation == null) {
                        "暂未获取到定位，请到开阔区域后重试。"
                    } else {
                        "定位信号暂时中断，地图保留上一次位置；请移动到开阔区域。"
                    },
                )
            }
        }
    }

    private fun MapUiState.withUpdatedDistance(): MapUiState {
        val location = userLocation ?: return copy(distanceToSelectedMeters = null)
        val landmark = selectedLandmark ?: return copy(distanceToSelectedMeters = null)
        return copy(distanceToSelectedMeters = distanceMeters(location.point, landmark.point))
    }

    companion object {
        private const val LOCATION_SIGNAL_TIMEOUT_MILLIS = 12_000L

        fun factory(
            landmarkRepository: LandmarkRepository,
            locationProvider: LocationProvider,
            privacyManager: LocationPrivacyManager,
            explorationProgressStore: ExplorationProgressStore,
            hasAmapKey: Boolean,
            debugEnabled: Boolean,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MapViewModel(
                landmarkRepository = landmarkRepository,
                locationProvider = locationProvider,
                privacyManager = privacyManager,
                explorationProgressStore = explorationProgressStore,
                hasAmapKey = hasAmapKey,
                debugEnabled = debugEnabled,
            ) as T
        }
    }

    override fun onCleared() {
        locationProvider.stopLocationUpdates()
        super.onCleared()
    }
}

internal fun CheckInArea.debugRadiusMeters(): Double = when (this) {
    is CircularCheckInArea -> radiusMeters
    is PolygonCheckInArea -> vertices.maxOf { offset ->
        maxOf(abs(offset.eastMeters), abs(offset.northMeters))
    }
}

internal fun CheckInArea.withDebugRadius(radiusMeters: Double): CheckInArea = when (this) {
    is CircularCheckInArea -> CircularCheckInArea(radiusMeters)
    is PolygonCheckInArea -> rectangleArea(radiusMeters)
}

private fun rectangleArea(radiusMeters: Double): PolygonCheckInArea {
    val halfHeight = radiusMeters * 0.65
    return PolygonCheckInArea(
        listOf(
            LocalOffsetMeters(-radiusMeters, -halfHeight),
            LocalOffsetMeters(radiusMeters, -halfHeight),
            LocalOffsetMeters(radiusMeters, halfHeight),
            LocalOffsetMeters(-radiusMeters, halfHeight),
        ),
    )
}
