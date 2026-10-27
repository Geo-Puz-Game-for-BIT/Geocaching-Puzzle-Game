package com.bitse.geotreasure.feature.map

import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.CheckInArea
import com.bitse.geotreasure.core.model.Landmark
import com.bitse.geotreasure.core.model.LocationSample
import com.bitse.geotreasure.core.model.MapRenderState

enum class LocationPermissionState {
    UNKNOWN,
    GRANTED,
    DENIED,
    PERMANENTLY_DENIED,
}

data class MapFocusRequest(
    val sequence: Int,
    val point: GeoPoint,
)

data class MapUiState(
    val isLoading: Boolean = true,
    val landmarks: List<Landmark> = emptyList(),
    val selectedLandmarkId: String? = null,
    val userLocation: LocationSample? = null,
    val distanceToSelectedMeters: Double? = null,
    val privacyAccepted: Boolean = false,
    val permissionState: LocationPermissionState = LocationPermissionState.UNKNOWN,
    val permissionRequestCount: Int = 0,
    val usingDemoMap: Boolean = true,
    val message: String? = null,
    val exploredCenters: List<GeoPoint> = emptyList(),
    val locationSignalActive: Boolean = false,
    val completedLandmarkIds: Set<String> = emptySet(),
    val revealedLandmarkIds: Set<String> = emptySet(),
    val debugEnabled: Boolean = false,
    val debugPanelExpanded: Boolean = false,
    val debugRevealAll: Boolean = false,
    val debugShowScoutRange: Boolean = false,
    val debugAreaOverrides: Map<String, CheckInArea> = emptyMap(),
    val debugPointOverrides: Map<String, GeoPoint> = emptyMap(),
    val debugMoveSelected: Boolean = false,
) {
    val landmarksWithDebugOverrides: List<Landmark>
        get() = landmarks.map { landmark ->
            landmark.copy(
                point = debugPointOverrides[landmark.id] ?: landmark.point,
                checkInArea = debugAreaOverrides[landmark.id] ?: landmark.checkInArea,
            )
        }

    val selectedLandmark: Landmark?
        get() = landmarksWithDebugOverrides.firstOrNull { it.id == selectedLandmarkId }

    val visibleLandmarkIds: Set<String>
        get() = if (debugRevealAll) landmarks.mapTo(mutableSetOf()) { it.id }
            else revealedLandmarkIds + completedLandmarkIds +
                (if (debugEnabled) setOfNotNull(selectedLandmarkId) else emptySet())

    val renderState: MapRenderState
        get() = MapRenderState(
            userLocation = userLocation,
            landmarks = landmarksWithDebugOverrides,
            selectedLandmarkId = selectedLandmarkId,
            exploredCenters = exploredCenters,
            liveScoutCenter = if (locationSignalActive) userLocation?.point else null,
            completedLandmarkIds = completedLandmarkIds,
            revealedLandmarkIds = revealedLandmarkIds,
            visibleLandmarkIds = visibleLandmarkIds,
            debugRevealAll = debugRevealAll,
            debugShowScoutRange = debugShowScoutRange,
            debugDraggableLandmarkId = if (debugEnabled && debugMoveSelected) selectedLandmarkId else null,
        )
}

enum class DebugRangeShape {
    CIRCLE,
    RECTANGLE,
}

sealed interface MapAction {
    data object AcceptPrivacy : MapAction
    data object RequestLocationPermission : MapAction
    data object OpenAppSettings : MapAction
    data object Retry : MapAction
    data class PermissionSnapshot(val granted: Boolean) : MapAction
    data class PermissionResult(val granted: Boolean, val canAskAgain: Boolean) : MapAction
    data class SelectLandmark(val landmarkId: String) : MapAction
    data object ClearSelection : MapAction
    data class MoveDebugLandmark(val landmarkId: String, val point: GeoPoint) : MapAction
    data class OpenLandmark(val landmarkId: String) : MapAction
    data object ToggleDebugPanel : MapAction
    data object ToggleDebugRevealAll : MapAction
    data object ToggleDebugScoutRange : MapAction
    data object ToggleDebugMoveSelected : MapAction
    data class AdjustDebugRadius(val deltaMeters: Double) : MapAction
    data class SetDebugRangeShape(val shape: DebugRangeShape) : MapAction
    data object DebugCompleteSelectedLandmark : MapAction
}

sealed interface MapEffect {
    data object RequestLocationPermission : MapEffect
    data object OpenAppSettings : MapEffect
    data class NavigateToLandmark(val landmarkId: String) : MapEffect
    data class ShowMessage(val message: String) : MapEffect
}
