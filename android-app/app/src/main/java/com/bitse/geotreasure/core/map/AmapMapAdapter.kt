package com.bitse.geotreasure.core.map

import android.graphics.Color
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.Circle
import com.amap.api.maps.model.CircleOptions
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.maps.model.Polygon
import com.amap.api.maps.model.PolygonOptions
import com.amap.api.maps.model.TileOverlayOptions
import com.bitse.geotreasure.core.location.offsetPoint
import com.bitse.geotreasure.core.model.CircularCheckInArea
import com.bitse.geotreasure.core.model.FogOfWarRules
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.Landmark
import com.bitse.geotreasure.core.model.LocationSample
import com.bitse.geotreasure.core.model.MapEvent
import com.bitse.geotreasure.core.model.MapRenderState
import com.bitse.geotreasure.core.model.PolygonCheckInArea
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal class AmapMapAdapter(
    private val map: AMap,
    private val locationSource: AmapRenderLocationSource,
) : MapAdapter {
    private val events = MutableSharedFlow<MapEvent>(extraBufferCapacity = 8)
    private val landmarkOverlays = mutableMapOf<String, LandmarkOverlay>()
    private val normalLandmarkIcon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
    private val selectedLandmarkIcon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
    private var cameraInitialized = false
    private var cameraFocusedOnUser = false
    private var renderedLocation: LocationSample? = null
    private var renderedLiveScoutCenter: GeoPoint? = null
    private var renderedDebugShowScoutRange = false
    private var scoutCircle: Circle? = null
    private val fogTiles = FogTileProvider()
    private val fogOverlay = map.addTileOverlay(
        TileOverlayOptions()
            .tileProvider(fogTiles)
            .zIndex(FOG_Z_INDEX)
            .memoryCacheEnabled(false)
            .diskCacheEnabled(false),
    )

    init {
        map.setLocationSource(locationSource)
        map.myLocationStyle = MyLocationStyle()
            .myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER)
            .radiusFillColor(Color.TRANSPARENT)
            .strokeColor(Color.TRANSPARENT)
            .strokeWidth(0f)
            .interval(LOCATION_INTERVAL_MILLIS)
            .showMyLocation(true)
        map.isMyLocationEnabled = true
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isCompassEnabled = true
        map.setOnMarkerClickListener { marker ->
            val landmarkId = marker.`object` as? String ?: return@setOnMarkerClickListener false
            focusOn(GeoPoint(marker.position.latitude, marker.position.longitude))
            events.tryEmit(MapEvent.LandmarkSelected(landmarkId))
            true
        }
        map.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChange(position: com.amap.api.maps.model.CameraPosition?) {
                position?.target?.let { target ->
                    events.tryEmit(MapEvent.CameraMoved(GeoPoint(target.latitude, target.longitude)))
                }
            }

            override fun onCameraChangeFinish(position: com.amap.api.maps.model.CameraPosition?) {
                position?.target?.let { target ->
                    events.tryEmit(MapEvent.CameraMoved(GeoPoint(target.latitude, target.longitude)))
                }
            }
        })
    }

    override fun render(state: MapRenderState) {
        reconcileLandmarks(state.landmarks.filter { it.id in state.visibleLandmarkIds }, state.selectedLandmarkId)
        if (fogTiles.update(state)) fogOverlay.clearTileCache()
        fogOverlay.isVisible = !state.debugRevealAll
        if (
            state.userLocation != renderedLocation ||
            state.liveScoutCenter != renderedLiveScoutCenter ||
            state.debugShowScoutRange != renderedDebugShowScoutRange
        ) {
            locationSource.update(state.userLocation)
            updateScoutCircle(state)
            renderedLocation = state.userLocation
            renderedLiveScoutCenter = state.liveScoutCenter
            renderedDebugShowScoutRange = state.debugShowScoutRange
        }
        if (!cameraFocusedOnUser && state.userLocation != null) {
            val point = state.userLocation.point
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), DEFAULT_ZOOM))
            cameraInitialized = true
            cameraFocusedOnUser = true
        } else if (!cameraInitialized) {
            val focus = state.userLocation?.point ?: state.landmarks.firstOrNull()?.point
            focus?.let {
                map.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), DEFAULT_ZOOM),
                )
                cameraInitialized = true
            }
        }
    }

    private fun reconcileLandmarks(
        landmarks: List<Landmark>,
        selectedLandmarkId: String?,
    ) {
        val incomingIds = landmarks.mapTo(mutableSetOf()) { it.id }
        landmarkOverlays.keys
            .filterNot(incomingIds::contains)
            .forEach { landmarkId ->
                landmarkOverlays.remove(landmarkId)?.remove()
            }

        landmarks.forEach { landmark ->
            val selected = landmark.id == selectedLandmarkId
            val existing = landmarkOverlays[landmark.id]
            if (existing == null) {
                landmarkOverlays[landmark.id] = createLandmarkOverlay(landmark, selected)
            } else if (existing.landmark != landmark || existing.selected != selected) {
                existing.update(landmark, selected)
            }
        }
    }

    private fun createLandmarkOverlay(
        landmark: Landmark,
        selected: Boolean,
    ): LandmarkOverlay {
        val marker = map.addMarker(
            MarkerOptions()
                .position(LatLng(landmark.point.latitude, landmark.point.longitude))
                .title(landmark.name)
                .icon(iconFor(selected)),
        ).apply {
            `object` = landmark.id
        }
        return LandmarkOverlay(marker, createAreaOverlay(landmark, selected), landmark, selected)
    }

    private fun createAreaOverlay(landmark: Landmark, selected: Boolean): AreaOverlay = when (val area = landmark.checkInArea) {
        is CircularCheckInArea -> CircleAreaOverlay(
            map.addCircle(
                CircleOptions()
                    .center(LatLng(landmark.point.latitude, landmark.point.longitude))
                    .radius(area.radiusMeters)
                    .applyAreaStyle(selected),
            ),
        )

        is PolygonCheckInArea -> PolygonAreaOverlay(
            map.addPolygon(
                PolygonOptions()
                    .addAll(areaPoints(landmark, area))
                    .applyAreaStyle(selected),
            ),
        )
    }

    private fun CircleOptions.applyAreaStyle(selected: Boolean) =
        strokeWidth(if (selected) 4f else 2f)
            .strokeColor(if (selected) SELECTED_STROKE_COLOR else DEFAULT_STROKE_COLOR)
            .fillColor(if (selected) SELECTED_FILL_COLOR else DEFAULT_FILL_COLOR)

    private fun PolygonOptions.applyAreaStyle(selected: Boolean) =
        strokeWidth(if (selected) 4f else 2f)
            .strokeColor(if (selected) SELECTED_STROKE_COLOR else DEFAULT_STROKE_COLOR)
            .fillColor(if (selected) SELECTED_FILL_COLOR else DEFAULT_FILL_COLOR)

    private fun areaPoints(
        landmark: Landmark,
        area: PolygonCheckInArea,
    ): List<LatLng> = area.vertices.map { offset ->
        val point = offsetPoint(landmark.point, offset)
        LatLng(point.latitude, point.longitude)
    }

    private fun updateAreaOverlay(
        overlay: AreaOverlay,
        landmark: Landmark,
        selected: Boolean,
    ): AreaOverlay = when {
        overlay is CircleAreaOverlay && landmark.checkInArea is CircularCheckInArea -> overlay.apply {
            circle.center = LatLng(landmark.point.latitude, landmark.point.longitude)
            circle.radius = landmark.checkInArea.radiusMeters
            circle.strokeWidth = if (selected) 4f else 2f
            circle.strokeColor = if (selected) SELECTED_STROKE_COLOR else DEFAULT_STROKE_COLOR
            circle.fillColor = if (selected) SELECTED_FILL_COLOR else DEFAULT_FILL_COLOR
        }

        overlay is PolygonAreaOverlay && landmark.checkInArea is PolygonCheckInArea -> overlay.apply {
            polygon.points = areaPoints(landmark, landmark.checkInArea)
            polygon.strokeWidth = if (selected) 4f else 2f
            polygon.strokeColor = if (selected) SELECTED_STROKE_COLOR else DEFAULT_STROKE_COLOR
            polygon.fillColor = if (selected) SELECTED_FILL_COLOR else DEFAULT_FILL_COLOR
        }

        else -> {
            overlay.remove()
            createAreaOverlay(landmark, selected)
        }
    }

    private fun iconFor(selected: Boolean) =
        if (selected) selectedLandmarkIcon else normalLandmarkIcon

    private fun updateScoutCircle(state: MapRenderState) {
        val liveScoutCenter = state.liveScoutCenter
        if (!state.debugShowScoutRange || liveScoutCenter == null) {
            scoutCircle?.remove()
            scoutCircle = null
        } else {
            val center = LatLng(liveScoutCenter.latitude, liveScoutCenter.longitude)
            if (scoutCircle == null) {
                scoutCircle = map.addCircle(
                    CircleOptions()
                        .center(center)
                        .radius(FogOfWarRules.SCOUT_RADIUS_METERS)
                        .strokeWidth(3f)
                        .strokeColor(SCOUT_STROKE_COLOR)
                        .fillColor(SCOUT_FILL_COLOR)
                        .zIndex(SCOUT_Z_INDEX),
                )
            } else {
                scoutCircle?.center = center
                scoutCircle?.radius = FogOfWarRules.SCOUT_RADIUS_METERS
            }
        }
    }

    private fun LandmarkOverlay.update(landmark: Landmark, selected: Boolean) {
        val position = LatLng(landmark.point.latitude, landmark.point.longitude)
        marker.position = position
        marker.title = landmark.name
        marker.setIcon(iconFor(selected))
        areaOverlay = updateAreaOverlay(areaOverlay, landmark, selected)
        this.landmark = landmark
        this.selected = selected
    }

    override fun focusOn(point: GeoPoint) {
        cameraFocusedOnUser = true
        map.stopAnimation()
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(point.latitude, point.longitude),
                DEFAULT_ZOOM,
            ),
            CAMERA_ANIMATION_MILLIS,
            null,
        )
        cameraInitialized = true
    }

    override fun observeEvents(): Flow<MapEvent> = events.asSharedFlow()

    override fun release() {
        map.isMyLocationEnabled = false
        locationSource.deactivate()
        landmarkOverlays.values.forEach(LandmarkOverlay::remove)
        landmarkOverlays.clear()
        scoutCircle?.remove()
        fogOverlay.remove()
        scoutCircle = null
        map.setOnMarkerClickListener(null)
        map.setOnCameraChangeListener(null)
    }

    private data class LandmarkOverlay(
        val marker: Marker,
        var areaOverlay: AreaOverlay,
        var landmark: Landmark,
        var selected: Boolean,
    ) {
        fun remove() {
            marker.remove()
            areaOverlay.remove()
        }
    }

    private sealed interface AreaOverlay {
        fun remove()
    }

    private data class CircleAreaOverlay(val circle: Circle) : AreaOverlay {
        override fun remove() = circle.remove()
    }

    private data class PolygonAreaOverlay(val polygon: Polygon) : AreaOverlay {
        override fun remove() = polygon.remove()
    }

    companion object {
        private const val DEFAULT_ZOOM = 17f
        private const val CAMERA_ANIMATION_MILLIS = 220L
        private const val LOCATION_INTERVAL_MILLIS = 3_000L
        private const val SCOUT_Z_INDEX = 31f
        private const val FOG_Z_INDEX = 30f
        private val SCOUT_STROKE_COLOR = 0xCC66D7B0.toInt()
        private val SCOUT_FILL_COLOR = 0x2266D7B0
        private val SELECTED_STROKE_COLOR = 0xFFE07A23.toInt()
        private val DEFAULT_STROKE_COLOR = 0xAA1C7C54.toInt()
        private const val SELECTED_FILL_COLOR = 0x22E07A23
        private const val DEFAULT_FILL_COLOR = 0x161C7C54
    }
}
