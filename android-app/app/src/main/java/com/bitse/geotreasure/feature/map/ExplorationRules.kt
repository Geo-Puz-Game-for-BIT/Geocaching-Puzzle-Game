package com.bitse.geotreasure.feature.map

import com.bitse.geotreasure.core.location.distanceMeters
import com.bitse.geotreasure.core.location.isWithinCheckInArea
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.FogOfWarRules
import com.bitse.geotreasure.core.model.Landmark
import com.bitse.geotreasure.core.model.LocationSample
import kotlin.math.ceil

internal fun updatedExploredCenters(
    centers: List<GeoPoint>,
    location: LocationSample,
    previousLocation: LocationSample? = null,
): List<GeoPoint> {
    if (!location.accuracyMeters.isFinite() || location.accuracyMeters > FogOfWarRules.MAX_EXPLORATION_ACCURACY_METERS) {
        return centers
    }
    val previous = centers.lastOrNull() ?: return listOf(location.point)
    if (!distanceMeters(previous, location.point).isFinite()) return centers

    // Only bridge a continuous, plausible movement. A fresh point after a GPS jump
    // may open its own area, but must not reveal the unobserved route between them.
    if (previousLocation == null) return centers.withNewCenter(location.point)
    val elapsedMillis = location.capturedAt.toEpochMilliseconds() - previousLocation.capturedAt.toEpochMilliseconds()
    val previousDistance = distanceMeters(previousLocation.point, location.point)
    if (elapsedMillis <= 0 || elapsedMillis > MAX_BRIDGE_INTERVAL_MILLIS ||
        previousLocation.accuracyMeters > FogOfWarRules.MAX_EXPLORATION_ACCURACY_METERS ||
        !previousLocation.accuracyMeters.isFinite() || !previousDistance.isFinite() ||
        previousDistance > MAX_BRIDGE_SPEED_METERS_PER_SECOND * elapsedMillis / 1_000.0 + BRIDGE_DISTANCE_TOLERANCE_METERS
    ) {
        return centers.withNewCenter(location.point)
    }

    val steps = ceil(previousDistance / FogOfWarRules.EXPLORATION_RECORD_DISTANCE_METERS).toInt().coerceAtLeast(1)
    val additions = mutableListOf<GeoPoint>()
    (1..steps).forEach { step ->
        val candidate = if (step == steps) location.point else {
            interpolate(previousLocation.point, location.point, step.toDouble() / steps)
        }
        if (centers.none { distanceMeters(it, candidate) < FogOfWarRules.EXPLORATION_RECORD_DISTANCE_METERS } &&
            additions.none { distanceMeters(it, candidate) < FogOfWarRules.EXPLORATION_RECORD_DISTANCE_METERS }
        ) {
            additions += candidate
        }
    }
    return centers + additions
}

private const val MAX_BRIDGE_INTERVAL_MILLIS = 10_000L
private const val MAX_BRIDGE_SPEED_METERS_PER_SECOND = 8.0
private const val BRIDGE_DISTANCE_TOLERANCE_METERS = 5.0

private fun List<GeoPoint>.withNewCenter(point: GeoPoint): List<GeoPoint> =
    if (any { distanceMeters(it, point) < FogOfWarRules.EXPLORATION_RECORD_DISTANCE_METERS }) this else this + point

private fun interpolate(from: GeoPoint, to: GeoPoint, fraction: Double): GeoPoint = GeoPoint(
    latitude = from.latitude + (to.latitude - from.latitude) * fraction,
    longitude = from.longitude + (to.longitude - from.longitude) * fraction,
)

internal fun discoverLandmarks(
    landmarks: List<Landmark>,
    alreadyRevealed: Set<String>,
    location: LocationSample,
): List<Landmark> {
    if (!location.accuracyMeters.isFinite() || location.accuracyMeters > FogOfWarRules.MAX_EXPLORATION_ACCURACY_METERS) {
        return emptyList()
    }
    return landmarks.filter { landmark ->
        landmark.id !in alreadyRevealed &&
            landmark.revealPointsOrCenter().any { revealPoint ->
                isWithinCheckInArea(
                    location.point,
                    revealPoint,
                    revealArea(landmark.checkInArea),
                    location.accuracyMeters,
                )
            }
    }
}

internal const val REVEAL_MARGIN_METERS = 25.0

private fun Landmark.revealPointsOrCenter(): List<GeoPoint> =
    if (revealPoints.isEmpty()) listOf(point) else revealPoints

private fun revealArea(area: com.bitse.geotreasure.core.model.CheckInArea): com.bitse.geotreasure.core.model.CheckInArea =
    when (area) {
        is com.bitse.geotreasure.core.model.CircularCheckInArea ->
            com.bitse.geotreasure.core.model.CircularCheckInArea(area.radiusMeters + REVEAL_MARGIN_METERS)
        is com.bitse.geotreasure.core.model.PolygonCheckInArea -> {
            val minEast = area.vertices.minOf { it.eastMeters }
            val maxEast = area.vertices.maxOf { it.eastMeters }
            val minNorth = area.vertices.minOf { it.northMeters }
            val maxNorth = area.vertices.maxOf { it.northMeters }
            com.bitse.geotreasure.core.model.PolygonCheckInArea(
                listOf(
                    com.bitse.geotreasure.core.model.LocalOffsetMeters(minEast - REVEAL_MARGIN_METERS, minNorth - REVEAL_MARGIN_METERS),
                    com.bitse.geotreasure.core.model.LocalOffsetMeters(maxEast + REVEAL_MARGIN_METERS, minNorth - REVEAL_MARGIN_METERS),
                    com.bitse.geotreasure.core.model.LocalOffsetMeters(maxEast + REVEAL_MARGIN_METERS, maxNorth + REVEAL_MARGIN_METERS),
                    com.bitse.geotreasure.core.model.LocalOffsetMeters(minEast - REVEAL_MARGIN_METERS, maxNorth + REVEAL_MARGIN_METERS),
                ),
            )
        }
    }
