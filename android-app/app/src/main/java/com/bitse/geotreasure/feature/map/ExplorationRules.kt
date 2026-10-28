package com.bitse.geotreasure.feature.map

import com.bitse.geotreasure.core.location.distanceMeters
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.FogOfWarRules
import com.bitse.geotreasure.core.model.LocationSample

internal fun updatedExploredCenters(centers: List<GeoPoint>, location: LocationSample): List<GeoPoint> {
    if (!location.accuracyMeters.isFinite() || location.accuracyMeters > FogOfWarRules.MAX_EXPLORATION_ACCURACY_METERS) {
        return centers
    }
    if (centers.any { distanceMeters(it, location.point) < FogOfWarRules.EXPLORATION_RECORD_DISTANCE_METERS }) return centers
    return centers + location.point
}
