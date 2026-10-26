package com.bitse.geotreasure.feature.map

import com.bitse.geotreasure.core.location.localOffsetMeters
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.Landmark

internal data class DemoMapPosition(val xFraction: Float, val yFraction: Float)

internal class DemoMapProjection(landmarks: List<Landmark>) {
    private val origin = landmarks.first().point
    private val offsets = landmarks.map { localOffsetMeters(origin, it.point) }
    private val minEast = offsets.minOf { it.eastMeters } - PADDING_METERS
    private val maxNorth = offsets.maxOf { it.northMeters } + PADDING_METERS
    private val eastSpan = offsets.maxOf { it.eastMeters } - minEast + PADDING_METERS
    private val northSpan = maxNorth - offsets.minOf { it.northMeters } + PADDING_METERS

    fun position(point: GeoPoint): DemoMapPosition {
        val offset = localOffsetMeters(origin, point)
        return DemoMapPosition(
            xFraction = ((offset.eastMeters - minEast) / eastSpan).toFloat(),
            yFraction = ((maxNorth - offset.northMeters) / northSpan).toFloat(),
        )
    }

    fun radiusFractions(radiusMeters: Double): DemoMapPosition = DemoMapPosition(
        xFraction = (radiusMeters / eastSpan).toFloat(),
        yFraction = (radiusMeters / northSpan).toFloat(),
    )

    private companion object {
        const val PADDING_METERS = 180.0
    }
}
