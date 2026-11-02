package com.bitse.geotreasure.feature.map

import com.bitse.geotreasure.core.location.distanceMeters
import com.bitse.geotreasure.core.location.isWithinCheckInArea
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.FogOfWarRules
import com.bitse.geotreasure.core.model.Landmark
import com.bitse.geotreasure.core.model.LocationSample
import kotlin.math.ceil

internal fun updatedExploredCenters(centers: List<GeoPoint>, location: LocationSample): List<GeoPoint> {
    if (!location.accuracyMeters.isFinite() || location.accuracyMeters > FogOfWarRules.MAX_EXPLORATION_ACCURACY_METERS) {
        return centers
    }
    val previous = centers.lastOrNull() ?: return listOf(location.point)
    val distance = distanceMeters(previous, location.point)
    if (distance < FogOfWarRules.EXPLORATION_RECORD_DISTANCE_METERS) return centers

    val steps = ceil(distance / FogOfWarRules.EXPLORATION_RECORD_DISTANCE_METERS).toInt()
    return centers + (1..steps).map { step ->
        if (step == steps) {
            location.point
        } else {
            interpolate(previous, location.point, step.toDouble() / steps)
        }
    }
}

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
            isWithinCheckInArea(location.point, landmark.point, landmark.checkInArea, location.accuracyMeters)
    }
}
