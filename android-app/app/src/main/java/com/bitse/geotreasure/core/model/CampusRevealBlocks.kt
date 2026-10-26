package com.bitse.geotreasure.core.model

import com.bitse.geotreasure.core.location.localOffsetMeters
import com.bitse.geotreasure.core.location.offsetPoint

data class CampusRevealBlock(
    val landmarkId: String,
    val campusZone: CampusZone,
    val pieceIndex: Int,
    val vertices: List<GeoPoint>,
)

fun campusRevealBlocks(landmarks: List<Landmark>): List<CampusRevealBlock> =
    landmarks.groupBy { it.campusZone }.flatMap { (zone, zoneLandmarks) ->
        if (zoneLandmarks.isEmpty()) return@flatMap emptyList()
        val origin = zoneLandmarks.first().point
        val anchors = zoneLandmarks.map { localOffsetMeters(origin, it.point) }
        val minEast = anchors.minOf { it.eastMeters } - BLOCK_PADDING_METERS
        val maxEast = anchors.maxOf { it.eastMeters } + BLOCK_PADDING_METERS
        val minNorth = anchors.minOf { it.northMeters } - BLOCK_PADDING_METERS
        val maxNorth = anchors.maxOf { it.northMeters } + BLOCK_PADDING_METERS
        val bounds = listOf(
            LocalOffsetMeters(minEast, minNorth),
            LocalOffsetMeters(maxEast, minNorth),
            LocalOffsetMeters(maxEast, maxNorth),
            LocalOffsetMeters(minEast, maxNorth),
        )

        zoneLandmarks.mapIndexed { index, landmark ->
            val anchor = anchors[index]
            val cell = anchors.foldIndexed(bounds) { otherIndex, polygon, other ->
                if (otherIndex == index) polygon else {
                    val eastNormal = other.eastMeters - anchor.eastMeters
                    val northNormal = other.northMeters - anchor.northMeters
                    val limit = (other.eastMeters * other.eastMeters + other.northMeters * other.northMeters -
                        anchor.eastMeters * anchor.eastMeters - anchor.northMeters * anchor.northMeters) / 2.0
                    clipToHalfPlane(polygon, eastNormal, northNormal, limit)
                }
            }
            CampusRevealBlock(landmark.id, zone, landmark.pieceIndex, cell.map { offsetPoint(origin, it) })
        }
    }

private fun clipToHalfPlane(
    polygon: List<LocalOffsetMeters>,
    eastNormal: Double,
    northNormal: Double,
    limit: Double,
): List<LocalOffsetMeters> {
    if (polygon.isEmpty()) return polygon
    fun side(point: LocalOffsetMeters) = point.eastMeters * eastNormal + point.northMeters * northNormal - limit
    val clipped = mutableListOf<LocalOffsetMeters>()
    var previous = polygon.last()
    var previousSide = side(previous)
    polygon.forEach { current ->
        val currentSide = side(current)
        if ((previousSide <= 0.0) != (currentSide <= 0.0)) {
            val fraction = previousSide / (previousSide - currentSide)
            clipped += LocalOffsetMeters(
                previous.eastMeters + fraction * (current.eastMeters - previous.eastMeters),
                previous.northMeters + fraction * (current.northMeters - previous.northMeters),
            )
        }
        if (currentSide <= 0.0) clipped += current
        previous = current
        previousSide = currentSide
    }
    return clipped
}

private const val BLOCK_PADDING_METERS = 80.0
