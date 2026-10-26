package com.bitse.geotreasure.core.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.amap.api.maps.model.Tile
import com.amap.api.maps.model.TileProvider
import com.bitse.geotreasure.core.location.offsetPoint
import com.bitse.geotreasure.core.model.FogOfWarRules
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.CircularCheckInArea
import com.bitse.geotreasure.core.model.PolygonCheckInArea
import com.bitse.geotreasure.core.model.Landmark
import com.bitse.geotreasure.core.model.LocalOffsetMeters
import com.bitse.geotreasure.core.model.MapRenderState
import com.bitse.geotreasure.core.model.campusRevealBlocks
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.tan

internal class FogTileProvider : TileProvider {
    @Volatile
    private var snapshot = FogSnapshot()

    fun update(state: MapRenderState): Boolean {
        val next = FogSnapshot(
            exploredCenters = state.exploredCenters,
            liveScoutCenter = state.liveScoutCenter,
            revealedLandmarkIds = state.revealedLandmarkIds,
            completedLandmarkIds = state.completedLandmarkIds,
            landmarks = state.landmarks,
        )
        if (next == snapshot) return false
        snapshot = next
        return true
    }

    override fun getTileWidth() = TILE_SIZE

    override fun getTileHeight() = TILE_SIZE

    override fun getTile(x: Int, y: Int, zoom: Int): Tile {
        val state = snapshot
        val bitmap = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(FOG_COLOR)
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }

        fun tilePoint(point: GeoPoint): Pair<Float, Float> {
            val scale = TILE_SIZE * (1L shl zoom).toDouble()
            val longitude = point.longitude.coerceIn(-180.0, 180.0)
            val latitudeRadians = Math.toRadians(point.latitude.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE))
            val worldX = (longitude + 180.0) / 360.0 * scale
            val worldY = (1.0 - asinh(tan(latitudeRadians)) / PI) / 2.0 * scale
            return (worldX - x * TILE_SIZE).toFloat() to (worldY - y * TILE_SIZE).toFloat()
        }

        fun clearCircle(center: GeoPoint, radiusMeters: Double) {
            val (cx, cy) = tilePoint(center)
            val (eastX, eastY) = tilePoint(offsetPoint(center, LocalOffsetMeters(radiusMeters, 0.0)))
            val (northX, northY) = tilePoint(offsetPoint(center, LocalOffsetMeters(0.0, radiusMeters)))
            val radius = max(hypot(eastX - cx, eastY - cy), hypot(northX - cx, northY - cy))
            if (cx + radius >= 0 && cx - radius <= TILE_SIZE && cy + radius >= 0 && cy - radius <= TILE_SIZE) {
                canvas.drawCircle(cx, cy, radius, clear)
            }
        }

        state.exploredCenters.forEach { clearCircle(it, FogOfWarRules.SCOUT_RADIUS_METERS) }
        state.liveScoutCenter?.let { clearCircle(it, FogOfWarRules.SCOUT_RADIUS_METERS) }
        state.landmarks.filter { it.id in state.revealedLandmarkIds }.forEach { landmark ->
            when (val area = landmark.checkInArea) {
                is CircularCheckInArea -> clearCircle(landmark.point, area.radiusMeters)
                is PolygonCheckInArea -> {
                    val path = Path()
                    area.vertices.forEachIndexed { index, offset ->
                        val (px, py) = tilePoint(offsetPoint(landmark.point, offset))
                        if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()
                    canvas.drawPath(path, clear)
                }
            }
        }
        campusRevealBlocks(state.landmarks)
            .filter { it.landmarkId in state.completedLandmarkIds }
            .forEach { block ->
                val path = Path()
                block.vertices.forEachIndexed { index, vertex ->
                    val (px, py) = tilePoint(vertex)
                    if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                canvas.drawPath(path, clear)
            }

        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        return Tile(TILE_SIZE, TILE_SIZE, output.toByteArray())
    }

    private data class FogSnapshot(
        val exploredCenters: List<GeoPoint> = emptyList(),
        val liveScoutCenter: GeoPoint? = null,
        val revealedLandmarkIds: Set<String> = emptySet(),
        val completedLandmarkIds: Set<String> = emptySet(),
        val landmarks: List<Landmark> = emptyList(),
    )

    companion object {
        private const val TILE_SIZE = 256
        private const val MAX_MERCATOR_LATITUDE = 85.05112878
        private const val FOG_COLOR = 0x66343A40
    }
}
