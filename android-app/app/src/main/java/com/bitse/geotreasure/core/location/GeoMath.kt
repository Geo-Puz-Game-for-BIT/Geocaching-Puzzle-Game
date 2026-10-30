package com.bitse.geotreasure.core.location

import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.CheckInArea
import com.bitse.geotreasure.core.model.CircularCheckInArea
import com.bitse.geotreasure.core.model.LocalOffsetMeters
import com.bitse.geotreasure.core.model.PolygonCheckInArea
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

fun distanceMeters(from: GeoPoint, to: GeoPoint): Double {
    val latitudeDelta = Math.toRadians(to.latitude - from.latitude)
    val longitudeDelta = Math.toRadians(to.longitude - from.longitude)
    val fromLatitude = Math.toRadians(from.latitude)
    val toLatitude = Math.toRadians(to.latitude)
    val haversine = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
        cos(fromLatitude) * cos(toLatitude) *
        sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
    val angularDistance = 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
    return EARTH_RADIUS_METERS * angularDistance
}

fun isWithinRadius(
    distanceMeters: Double,
    radiusMeters: Double,
    accuracyMeters: Float,
): Boolean = distanceMeters <= radiusMeters && accuracyMeters <= MAX_CHECK_IN_ACCURACY_METERS

fun offsetPoint(
    origin: GeoPoint,
    offset: LocalOffsetMeters,
): GeoPoint {
    val latitudeRadians = Math.toRadians(origin.latitude)
    val latitudeDelta = offset.northMeters / EARTH_RADIUS_METERS
    val longitudeScale = max(cos(latitudeRadians), 0.000001)
    val longitudeDelta = offset.eastMeters / (EARTH_RADIUS_METERS * longitudeScale)
    return GeoPoint(
        latitude = origin.latitude + Math.toDegrees(latitudeDelta),
        longitude = origin.longitude + Math.toDegrees(longitudeDelta),
    )
}

fun localOffsetMeters(
    origin: GeoPoint,
    point: GeoPoint,
): LocalOffsetMeters {
    val latitudeRadians = Math.toRadians(origin.latitude)
    return LocalOffsetMeters(
        eastMeters = Math.toRadians(point.longitude - origin.longitude) * EARTH_RADIUS_METERS * cos(latitudeRadians),
        northMeters = Math.toRadians(point.latitude - origin.latitude) * EARTH_RADIUS_METERS,
    )
}

fun isWithinCheckInArea(
    point: GeoPoint,
    areaCenter: GeoPoint,
    area: CheckInArea,
    accuracyMeters: Float,
): Boolean {
    if (accuracyMeters > MAX_CHECK_IN_ACCURACY_METERS) return false
    return when (area) {
        is CircularCheckInArea -> distanceMeters(areaCenter, point) <= area.radiusMeters
        is PolygonCheckInArea -> pointInPolygon(localOffsetMeters(areaCenter, point), area.vertices)
    }
}

private fun pointInPolygon(
    point: LocalOffsetMeters,
    vertices: List<LocalOffsetMeters>,
): Boolean {
    var inside = false
    var previousIndex = vertices.lastIndex
    vertices.forEachIndexed { currentIndex, current ->
        val previous = vertices[previousIndex]
        val crossesHorizontalRay = (current.northMeters > point.northMeters) !=
            (previous.northMeters > point.northMeters)
        if (crossesHorizontalRay) {
            val intersectionEast = (previous.eastMeters - current.eastMeters) *
                (point.northMeters - current.northMeters) /
                (previous.northMeters - current.northMeters) + current.eastMeters
            if (point.eastMeters < intersectionEast) inside = !inside
        }
        previousIndex = currentIndex
    }
    return inside
}

// 正式打卡门控精度上限：≤30m（2026-10-30 团队拍板）。
// 战争迷雾探索阈值见 FogOfWarRules.MAX_EXPLORATION_ACCURACY_METERS（保持 50m，两者勿混用）。
private const val MAX_CHECK_IN_ACCURACY_METERS = 30f
