package com.bitse.geotreasure.core.model

import kotlinx.datetime.Instant

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

data class LocalOffsetMeters(
    val eastMeters: Double,
    val northMeters: Double,
)

sealed interface CheckInArea

data class CircularCheckInArea(
    val radiusMeters: Double,
) : CheckInArea {
    init {
        require(radiusMeters > 0.0) { "Circle radius must be positive" }
    }
}

data class PolygonCheckInArea(
    val vertices: List<LocalOffsetMeters>,
) : CheckInArea {
    init {
        require(vertices.size >= 3) { "Polygon check-in area requires at least three vertices" }
    }
}

data class LocationSample(
    val point: GeoPoint,
    val accuracyMeters: Float,
    val capturedAt: Instant,
    val source: LocationSourceKind = LocationSourceKind.UNKNOWN,
)

enum class LocationSourceKind {
    GPS,
    WIFI,
    CELL,
    NETWORK,
    CACHE,
    DEMO,
    UNKNOWN,
}

data class Landmark(
    val id: String,
    val name: String,
    val point: GeoPoint,
    val campusZone: CampusZone,
    val fragmentId: String,
    val pieceIndex: Int,
    val coordinateVerified: Boolean,
    val checkInArea: CheckInArea,
    val difficulty: Int,
    val terrain: Int,
    val hint: String?,
)

enum class CampusZone {
    NORTH,
    EAST,
    SOUTH,
}

enum class PuzzleRevealMode {
    GRID,
    FOG_OF_WAR,
}

data class PuzzleFragmentDefinition(
    val id: String,
    val landmarkId: String,
    val title: String,
    val pieceIndex: Int,
)

data class PuzzleSectionDefinition(
    val id: String,
    val campusZone: CampusZone,
    val rows: Int,
    val columns: Int,
    val fragments: List<PuzzleFragmentDefinition>,
)

data class PuzzleDefinition(
    val id: String,
    val title: String,
    val defaultRevealMode: PuzzleRevealMode,
    val sections: List<PuzzleSectionDefinition>,
) {
    val fragmentCount: Int
        get() = sections.sumOf { it.fragments.size }
}

data class CheckInDraft(
    val id: String,
    val landmarkId: String,
    val localPhoto: LocalPhotoRef?,
    val uploadState: UploadState,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class LocalPhotoRef(
    val absolutePath: String,
    val mimeType: String,
    val sizeBytes: Long,
)

data class CheckInChallenge(
    val id: String,
    val landmarkId: String,
    val expiresAt: Instant,
)

data class FragmentGrant(
    val fragmentId: String,
    val puzzleId: String,
    val pieceIndex: Int,
    val grantedAt: Instant,
)

data class CheckInReceipt(
    val checkInId: String,
    val status: CheckInStatus,
    val fragmentGrant: FragmentGrant,
)

data class PuzzlePlacement(
    val fragmentId: String,
    val slotIndex: Int?,
    val offsetX: Float,
    val offsetY: Float,
    val updatedAt: Instant,
)

enum class UploadState {
    EMPTY,
    CAPTURED,
    UPLOADING,
    UPLOADED,
    FAILED,
    CONFIRMED,
}

enum class CheckInStatus {
    COMPLETED,
    ALREADY_COMPLETED,
}

data class UploadedPhoto(
    val photoId: String,
    val objectKey: String,
    val uploadedAt: Instant,
)

data class SubmitCheckInRequest(
    val landmarkId: String,
    val challengeId: String,
    val photoId: String,
    val location: LocationSample,
)

data class MapRenderState(
    val userLocation: LocationSample?,
    val landmarks: List<Landmark>,
    val selectedLandmarkId: String?,
    val exploredCenters: List<GeoPoint> = emptyList(),
    val liveScoutCenter: GeoPoint? = null,
    val revealedLandmarkIds: Set<String> = emptySet(),
    val visibleLandmarkIds: Set<String> = emptySet(),
    val completedLandmarkIds: Set<String> = emptySet(),
    val debugRevealAll: Boolean = false,
    val debugShowScoutRange: Boolean = false,
)

sealed interface MapEvent {
    data class LandmarkSelected(val landmarkId: String) : MapEvent

    data class CameraMoved(val center: GeoPoint) : MapEvent
}

data class PuzzleSlot(
    val index: Int,
    val centerX: Float,
    val centerY: Float,
)

data class PuzzleState(
    val puzzleId: String,
    val ownedFragmentIds: Set<String>,
    val placements: List<PuzzlePlacement>,
    val completed: Boolean,
)

const val MAX_PHOTO_BYTES = 5_242_880L
