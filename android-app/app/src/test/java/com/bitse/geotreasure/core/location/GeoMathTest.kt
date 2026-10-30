package com.bitse.geotreasure.core.location

import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.CircularCheckInArea
import com.bitse.geotreasure.core.model.LocalOffsetMeters
import com.bitse.geotreasure.core.model.PolygonCheckInArea
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {
    @Test
    fun distanceMeters_samePoint_returnsZero() {
        val point = GeoPoint(39.9594, 116.3162)

        assertTrue(distanceMeters(point, point) < 0.01)
    }

    @Test
    fun isWithinRadius_rejectsPoorAccuracy() {
        assertFalse(isWithinRadius(distanceMeters = 10.0, radiusMeters = 30.0, accuracyMeters = 70f))
    }

    @Test
    fun isWithinRadius_rejectsAccuracyAbove30m() {
        // 40m 在旧 50m 阈值内、新 30m 阈值外，用于锁定正式打卡门控 = 30m
        assertFalse(isWithinRadius(distanceMeters = 10.0, radiusMeters = 30.0, accuracyMeters = 40f))
    }

    @Test
    fun isWithinRadius_acceptsCloseAccurateLocation() {
        assertTrue(isWithinRadius(distanceMeters = 10.0, radiusMeters = 30.0, accuracyMeters = 12f))
    }

    @Test
    fun isWithinCheckInArea_supportsCircularAreas() {
        val center = GeoPoint(39.7335, 116.1649)
        val nearbyPoint = offsetPoint(center, LocalOffsetMeters(eastMeters = 15.0, northMeters = 0.0))

        assertTrue(isWithinCheckInArea(nearbyPoint, center, CircularCheckInArea(20.0), accuracyMeters = 10f))
        assertFalse(isWithinCheckInArea(nearbyPoint, center, CircularCheckInArea(10.0), accuracyMeters = 10f))
    }

    @Test
    fun isWithinCheckInArea_supportsPolygonAreas() {
        val center = GeoPoint(39.7335, 116.1649)
        val rectangle = PolygonCheckInArea(
            vertices = listOf(
                LocalOffsetMeters(-20.0, -10.0),
                LocalOffsetMeters(20.0, -10.0),
                LocalOffsetMeters(20.0, 10.0),
                LocalOffsetMeters(-20.0, 10.0),
            ),
        )

        assertTrue(isWithinCheckInArea(center, center, rectangle, accuracyMeters = 8f))
        assertFalse(
            isWithinCheckInArea(
                offsetPoint(center, LocalOffsetMeters(eastMeters = 25.0, northMeters = 0.0)),
                center,
                rectangle,
                accuracyMeters = 8f,
            ),
        )
        assertFalse(isWithinCheckInArea(center, center, rectangle, accuracyMeters = 70f))
    }
}
