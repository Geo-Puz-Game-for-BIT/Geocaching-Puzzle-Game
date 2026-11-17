package com.bitse.geotreasure.core.map

import com.bitse.geotreasure.core.location.localOffsetMeters
import com.bitse.geotreasure.core.location.offsetPoint
import com.bitse.geotreasure.core.model.CampusZone
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.LocalOffsetMeters
import com.bitse.geotreasure.core.model.LocationSample
import kotlinx.datetime.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class CampusMapRoom(
    val zone: CampusZone,
    val outline: List<GeoPoint>,
    val vectorAssetKey: String,
) {
    init {
        validateOutline(outline)
        require(vectorAssetKey.isNotBlank())
    }
}

data class CampusMapDoor(
    val id: String,
    val firstZone: CampusZone,
    val firstSide: List<GeoPoint>,
    val secondZone: CampusZone,
    val secondSide: List<GeoPoint>,
    val transitOutline: List<GeoPoint>,
) {
    init {
        require(id.isNotBlank())
        require(firstZone != secondZone)
        validateOutline(firstSide)
        validateOutline(secondSide)
        validateOutline(transitOutline)
    }

    fun sideFor(zone: CampusZone): List<GeoPoint>? = when (zone) {
        firstZone -> firstSide
        secondZone -> secondSide
        else -> null
    }

    fun opposite(zone: CampusZone): CampusZone? = when (zone) {
        firstZone -> secondZone
        secondZone -> firstZone
        else -> null
    }
}

data class CampusMapLayout(
    val schoolOutline: List<GeoPoint>,
    val overviewAssetKey: String,
    val rooms: List<CampusMapRoom>,
    val doors: List<CampusMapDoor>,
) {
    init {
        validateOutline(schoolOutline)
        require(overviewAssetKey.isNotBlank())
        require(rooms.map { it.zone }.toSet() == CampusZone.entries.toSet())
        require(rooms.size == CampusZone.entries.size)
        require((rooms.map { it.vectorAssetKey } + overviewAssetKey).toSet().size == rooms.size + 1)
        require(doors.map { it.id }.toSet().size == doors.size)
    }

    fun roomAt(point: GeoPoint): CampusZone? = rooms.firstOrNull { contains(it.outline, point) }?.zone

    fun outlineFor(zone: CampusZone): List<GeoPoint> = rooms.first { it.zone == zone }.outline
}

enum class CampusMapViewMode { OVERVIEW, CAMPUS_OVERVIEW, FOLLOW_USER }

enum class CampusBoundaryWarning { APPROACHING_OUTSIDE_GATE, OUTSIDE_WITHOUT_GATE }

data class CampusMapState(
    val room: CampusZone? = null,
    val viewMode: CampusMapViewMode = CampusMapViewMode.OVERVIEW,
    val armedDoorId: String? = null,
    val armedAt: Instant? = null,
    val candidateRoom: CampusZone? = null,
    val candidateFixes: Int = 0,
    val candidateFixAt: Instant? = null,
    val warning: CampusBoundaryWarning? = null,
)

data class CampusDoorTransition(
    val doorId: String,
    val from: CampusZone,
    val to: CampusZone,
)

data class CampusMapUpdate(
    val state: CampusMapState,
    val transition: CampusDoorTransition? = null,
)

data class CampusViewportConstraint(
    val center: GeoPoint,
    val visibleWidthMeters: Double,
    val visibleHeightMeters: Double,
    val needsMaskOrLetterbox: Boolean,
)

object CampusMapFlow {
    const val MAX_LOCATION_ACCURACY_METERS = 30f
    const val MAX_LOCATION_AGE_MILLIS = 5_000L
    const val APPROACH_WARNING_METERS = 25.0
    const val DOOR_TRANSIT_TIMEOUT_MILLIS = 120_000L

    fun selectViewMode(state: CampusMapState, mode: CampusMapViewMode): CampusMapState =
        state.copy(viewMode = if (state.room == null && mode != CampusMapViewMode.OVERVIEW) {
            CampusMapViewMode.OVERVIEW
        } else {
            mode
        })

    fun update(
        state: CampusMapState,
        sample: LocationSample,
        now: Instant,
        layout: CampusMapLayout,
    ): CampusMapUpdate {
        val age = now.toEpochMilliseconds() - sample.capturedAt.toEpochMilliseconds()
        if (!sample.accuracyMeters.isFinite() || sample.accuracyMeters < 0f ||
            sample.accuracyMeters > MAX_LOCATION_ACCURACY_METERS ||
            age !in 0..MAX_LOCATION_AGE_MILLIS || !sample.point.isValid()
        ) {
            return CampusMapUpdate(state.copy(
                warning = null,
                candidateRoom = null,
                candidateFixes = 0,
                candidateFixAt = null,
            ))
        }

        val detected = layout.roomAt(sample.point)
        val current = state.room ?: return CampusMapUpdate(state.copy(room = detected))
        val armed = layout.doors.firstOrNull { door ->
            door.id == state.armedDoorId && state.armedAt != null &&
                now.toEpochMilliseconds() - state.armedAt.toEpochMilliseconds() in 0..DOOR_TRANSIT_TIMEOUT_MILLIS
        }
        val entry = layout.doors.firstOrNull { door ->
            door.sideFor(current)?.let { contains(it, sample.point) } == true
        }
        val door = entry ?: armed?.takeIf { contains(it.transitOutline, sample.point) }
        val armedAt = if (entry != null) now else if (door != null) state.armedAt else null

        if (detected == current) {
            val nearEdge = distanceToEdge(layout.outlineFor(current), sample.point) <= APPROACH_WARNING_METERS
            return CampusMapUpdate(state.copy(
                armedDoorId = door?.id,
                armedAt = armedAt,
                candidateRoom = null,
                candidateFixes = 0,
                candidateFixAt = null,
                warning = if (door == null && nearEdge) CampusBoundaryWarning.APPROACHING_OUTSIDE_GATE else null,
            ))
        }

        val exitSide = detected?.let { door?.sideFor(it) }
        val canCross = detected != null && door?.opposite(current) == detected &&
            exitSide != null && contains(exitSide, sample.point) &&
            armedAt != null && sample.capturedAt.toEpochMilliseconds() >= armedAt.toEpochMilliseconds()
        if (canCross) {
            val newFix = state.candidateFixAt == null || sample.capturedAt > state.candidateFixAt
            val fixes = if (state.candidateRoom == detected) state.candidateFixes + if (newFix) 1 else 0 else 1
            if (fixes >= 2) {
                return CampusMapUpdate(
                    state.copy(
                        room = detected,
                        armedDoorId = null,
                        armedAt = null,
                        candidateRoom = null,
                        candidateFixes = 0,
                        candidateFixAt = null,
                        warning = null,
                    ),
                    CampusDoorTransition(door.id, current, detected),
                )
            }
            return CampusMapUpdate(state.copy(
                armedDoorId = door.id,
                armedAt = armedAt,
                candidateRoom = detected,
                candidateFixes = fixes,
                candidateFixAt = if (newFix) sample.capturedAt else state.candidateFixAt,
                warning = null,
            ))
        }

        return CampusMapUpdate(state.copy(
            armedDoorId = door?.id,
            armedAt = armedAt,
            candidateRoom = null,
            candidateFixes = 0,
            candidateFixAt = null,
            warning = if (door == null) CampusBoundaryWarning.OUTSIDE_WITHOUT_GATE else null,
        ))
    }

    fun clampViewport(
        outline: List<GeoPoint>,
        proposedCenter: GeoPoint,
        requestedWidthMeters: Double,
        requestedHeightMeters: Double,
    ): CampusViewportConstraint {
        validateOutline(outline)
        require(requestedWidthMeters > 0 && requestedHeightMeters > 0)
        require(proposedCenter.isValid())
        val origin = outline.first()
        val offsets = outline.map { localOffsetMeters(origin, it) }
        val minEast = offsets.minOf { it.eastMeters }
        val maxEast = offsets.maxOf { it.eastMeters }
        val minNorth = offsets.minOf { it.northMeters }
        val maxNorth = offsets.maxOf { it.northMeters }
        val width = min(requestedWidthMeters, maxEast - minEast)
        val height = min(requestedHeightMeters, maxNorth - minNorth)
        val target = localOffsetMeters(origin, proposedCenter)
        val east = target.eastMeters.coerceIn(minEast + width / 2, maxEast - width / 2)
        val north = target.northMeters.coerceIn(minNorth + height / 2, maxNorth - height / 2)
        return CampusViewportConstraint(
            center = offsetPoint(origin, LocalOffsetMeters(east, north)),
            visibleWidthMeters = width,
            visibleHeightMeters = height,
            needsMaskOrLetterbox = width < requestedWidthMeters || height < requestedHeightMeters,
        )
    }
}

private fun GeoPoint.isValid(): Boolean = latitude.isFinite() && longitude.isFinite() &&
    latitude in -90.0..90.0 && longitude in -180.0..180.0

private fun validateOutline(outline: List<GeoPoint>) {
    require(outline.size >= 3 && outline.all { it.isValid() })
    val origin = outline.first()
    val offsets = outline.map { localOffsetMeters(origin, it) }
    val twiceArea = offsets.indices.sumOf { index ->
        val current = offsets[index]
        val next = offsets[(index + 1) % offsets.size]
        current.eastMeters * next.northMeters - next.eastMeters * current.northMeters
    }
    require(abs(twiceArea) > 0.2) { "Campus map outline must enclose an area" }
}

private fun contains(outline: List<GeoPoint>, point: GeoPoint): Boolean {
    if (!point.isValid()) return false
    if (distanceToEdge(outline, point) <= 0.5) return true
    val origin = outline.first()
    val vertices = outline.map { localOffsetMeters(origin, it) }
    val target = localOffsetMeters(origin, point)
    var inside = false
    var previous = vertices.last()
    vertices.forEach { vertex ->
        if ((vertex.northMeters > target.northMeters) != (previous.northMeters > target.northMeters)) {
            val east = (previous.eastMeters - vertex.eastMeters) *
                (target.northMeters - vertex.northMeters) /
                (previous.northMeters - vertex.northMeters) + vertex.eastMeters
            if (target.eastMeters < east) inside = !inside
        }
        previous = vertex
    }
    return inside
}

private fun distanceToEdge(outline: List<GeoPoint>, point: GeoPoint): Double {
    val origin = outline.first()
    val vertices = outline.map { localOffsetMeters(origin, it) }
    val target = localOffsetMeters(origin, point)
    return vertices.indices.minOf { index ->
        val start = vertices[index]
        val end = vertices[(index + 1) % vertices.size]
        val east = end.eastMeters - start.eastMeters
        val north = end.northMeters - start.northMeters
        val lengthSquared = east * east + north * north
        val fraction = if (lengthSquared == 0.0) 0.0 else (
            (target.eastMeters - start.eastMeters) * east +
                (target.northMeters - start.northMeters) * north
            ) / lengthSquared
        val clamped = max(0.0, min(1.0, fraction))
        val deltaEast = target.eastMeters - (start.eastMeters + clamped * east)
        val deltaNorth = target.northMeters - (start.northMeters + clamped * north)
        sqrt(deltaEast * deltaEast + deltaNorth * deltaNorth)
    }
}
