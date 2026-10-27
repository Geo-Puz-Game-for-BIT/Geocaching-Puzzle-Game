package com.bitse.geotreasure.core.map

import com.amap.api.maps.AMap
import com.amap.api.maps.model.BaseHoleOptions
import com.amap.api.maps.model.CircleHoleOptions
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Polygon
import com.amap.api.maps.model.PolygonHoleOptions
import com.amap.api.maps.model.PolygonOptions
import com.bitse.geotreasure.core.location.offsetPoint
import com.bitse.geotreasure.core.model.CircularCheckInArea
import com.bitse.geotreasure.core.model.FogOfWarRules
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.Landmark
import com.bitse.geotreasure.core.model.MapRenderState
import com.bitse.geotreasure.core.model.PolygonCheckInArea
import com.bitse.geotreasure.core.model.campusRevealBlocks

internal class FogPolygonOverlay(private val map: AMap) {
    private var polygon: Polygon? = null
    private var renderedFog: FogGeometry? = null

    fun render(state: MapRenderState) {
        if (state.landmarks.isEmpty()) return
        if (polygon == null) {
            val latitudes = state.landmarks.map { it.point.latitude }
            val longitudes = state.landmarks.map { it.point.longitude }
            val south = latitudes.min() - CAMPUS_MARGIN_DEGREES
            val north = latitudes.max() + CAMPUS_MARGIN_DEGREES
            val west = longitudes.min() - CAMPUS_MARGIN_DEGREES
            val east = longitudes.max() + CAMPUS_MARGIN_DEGREES
            polygon = map.addPolygon(
                PolygonOptions()
                    .add(LatLng(south, west), LatLng(south, east), LatLng(north, east), LatLng(north, west))
                    .strokeWidth(0f)
                    .fillColor(FOG_COLOR)
                    .zIndex(FOG_Z_INDEX),
            )
        }

        val geometry = FogGeometry(
            exploredCenters = state.exploredCenters,
            liveScoutCenter = state.liveScoutCenter,
            revealedLandmarkIds = state.revealedLandmarkIds,
            completedLandmarkIds = state.completedLandmarkIds,
            landmarks = state.landmarks,
        )
        if (geometry != renderedFog) {
            val holes = buildList<BaseHoleOptions> {
                geometry.exploredCenters.forEach { add(circleHole(it, FogOfWarRules.SCOUT_RADIUS_METERS)) }
                geometry.liveScoutCenter?.let { add(circleHole(it, FogOfWarRules.SCOUT_RADIUS_METERS)) }
                geometry.landmarks.filter { it.id in geometry.revealedLandmarkIds }.forEach { landmark ->
                    when (val area = landmark.checkInArea) {
                        is CircularCheckInArea -> add(circleHole(landmark.point, area.radiusMeters))
                        is PolygonCheckInArea -> add(
                            PolygonHoleOptions().addAll(area.vertices.map { offset ->
                                offsetPoint(landmark.point, offset).toLatLng()
                            }),
                        )
                    }
                }
                campusRevealBlocks(geometry.landmarks)
                    .filter { it.landmarkId in geometry.completedLandmarkIds }
                    .forEach { block -> add(PolygonHoleOptions().addAll(block.vertices.map { it.toLatLng() })) }
            }
            polygon?.holeOptions = holes
            renderedFog = geometry
        }
        polygon?.isVisible = !state.debugRevealAll
    }

    fun remove() {
        polygon?.remove()
        polygon = null
        renderedFog = null
    }

    private fun circleHole(point: GeoPoint, radiusMeters: Double) =
        CircleHoleOptions().center(point.toLatLng()).radius(radiusMeters)

    private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)

    private data class FogGeometry(
        val exploredCenters: List<GeoPoint>,
        val liveScoutCenter: GeoPoint?,
        val revealedLandmarkIds: Set<String>,
        val completedLandmarkIds: Set<String>,
        val landmarks: List<Landmark>,
    )

    companion object {
        private const val CAMPUS_MARGIN_DEGREES = 0.04
        private const val FOG_Z_INDEX = 30f
        private const val FOG_COLOR = 0x66343A40
    }
}
