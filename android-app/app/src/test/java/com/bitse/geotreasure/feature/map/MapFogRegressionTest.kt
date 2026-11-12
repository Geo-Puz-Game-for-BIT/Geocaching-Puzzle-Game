package com.bitse.geotreasure.feature.map

import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.FogOfWarRules
import com.bitse.geotreasure.core.model.LiangxiangTreasureCatalog
import com.bitse.geotreasure.core.model.LocalOffsetMeters
import com.bitse.geotreasure.core.model.LocationSample
import com.bitse.geotreasure.core.model.PolygonCheckInArea
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapFogRegressionTest {
    @Test
    fun `poor accuracy does not open fog and earlier centers remain`() {
        val first = GeoPoint(39.7366, 116.1666)
        val history = updatedExploredCenters(emptyList(), sample(first, 18f))
        val poorSample = sample(GeoPoint(39.7339, 116.1680), 80f)

        assertEquals(listOf(first), updatedExploredCenters(history, poorSample))
        assertEquals(emptyList<GeoPoint>(), updatedExploredCenters(emptyList(), poorSample))
        assertEquals(history, updatedExploredCenters(history, sample(first, 18f)))
    }

    @Test
    fun `demo projection gives historical centers their own map positions`() {
        val projection = DemoMapProjection(LiangxiangTreasureCatalog.landmarks)
        val first = LiangxiangTreasureCatalog.landmarks.first().point
        val second = LiangxiangTreasureCatalog.landmarks.last().point

        assertNotEquals(projection.position(first), projection.position(second))
        assertEquals(15.0, FogOfWarRules.SCOUT_RADIUS_METERS, 0.001)
        assertTrue(projection.radiusFractions(FogOfWarRules.SCOUT_RADIUS_METERS).xFraction > 0f)
        assertTrue(projection.radiusFractions(FogOfWarRules.SCOUT_RADIUS_METERS).yFraction > 0f)
    }

    @Test
    fun `first reliable location opens fog and walking samples stay continuous`() {
        val first = LiangxiangTreasureCatalog.landmarks.first().point
        val nearby = com.bitse.geotreasure.core.location.offsetPoint(first, LocalOffsetMeters(8.0, 0.0))
        val next = com.bitse.geotreasure.core.location.offsetPoint(first, LocalOffsetMeters(12.0, 0.0))

        val initial = updatedExploredCenters(emptyList(), sample(first, 18f))
        assertEquals(listOf(first), initial)
        assertEquals(initial, updatedExploredCenters(initial, sample(nearby, 18f)))

        val advanced = updatedExploredCenters(initial, sample(next, 18f, 2_000), sample(nearby, 18f, 1_000))
        assertEquals(listOf(first, next), advanced)
    }

    @Test
    fun `large location jump bridges fog centers along the path`() {
        val start = LiangxiangTreasureCatalog.landmarks.first().point
        val end = com.bitse.geotreasure.core.location.offsetPoint(start, LocalOffsetMeters(42.0, 0.0))

        val centers = updatedExploredCenters(listOf(start), sample(end, 18f, 6_000), sample(start, 18f))

        assertTrue(centers.size > 2)
        assertEquals(start, centers.first())
        assertTrue(com.bitse.geotreasure.core.location.distanceMeters(centers.last(), end) < FogOfWarRules.SCOUT_RADIUS_METERS)
        centers.zipWithNext().forEach { (from, to) ->
            assertTrue(
                "Fog centers leave a gap: ${com.bitse.geotreasure.core.location.distanceMeters(from, to)} m",
                com.bitse.geotreasure.core.location.distanceMeters(from, to) <=
                    FogOfWarRules.SCOUT_RADIUS_METERS * 2,
            )
        }
    }

    @Test
    fun `revisiting explored area does not add duplicate holes`() {
        val start = LiangxiangTreasureCatalog.landmarks.first().point
        val outbound = com.bitse.geotreasure.core.location.offsetPoint(start, LocalOffsetMeters(20.0, 0.0))
        val returning = com.bitse.geotreasure.core.location.offsetPoint(start, LocalOffsetMeters(5.0, 0.0))
        val history = listOf(start, outbound)

        val centers = updatedExploredCenters(history, sample(returning, 18f, 3_000), sample(outbound, 18f))

        assertEquals(history, centers)
    }

    @Test
    fun `returning to explored area can reveal an unexplored detour`() {
        val start = LiangxiangTreasureCatalog.landmarks.first().point
        val outbound = com.bitse.geotreasure.core.location.offsetPoint(start, LocalOffsetMeters(30.0, 0.0))
        val returning = com.bitse.geotreasure.core.location.offsetPoint(start, LocalOffsetMeters(0.0, 5.0))
        val history = listOf(start, outbound)

        val centers = updatedExploredCenters(history, sample(returning, 18f, 5_000), sample(outbound, 18f))

        assertTrue(centers.size > history.size)
        assertTrue(centers.any {
            com.bitse.geotreasure.core.location.distanceMeters(it, returning) < FogOfWarRules.SCOUT_RADIUS_METERS
        })
    }

    @Test
    fun `poor accuracy cannot create a bridge or a new permanent center`() {
        val start = LiangxiangTreasureCatalog.landmarks.first().point
        val end = com.bitse.geotreasure.core.location.offsetPoint(start, LocalOffsetMeters(42.0, 0.0))
        val history = listOf(start)

        assertEquals(history, updatedExploredCenters(history, sample(end, 70f, 6_000), sample(start, 18f)))
        assertEquals(
            listOf(start, end),
            updatedExploredCenters(history, sample(end, 18f, 6_000), sample(start, 70f)),
        )
    }

    @Test
    fun `stale or implausible location jump does not reveal the route`() {
        val start = LiangxiangTreasureCatalog.landmarks.first().point
        val end = com.bitse.geotreasure.core.location.offsetPoint(start, LocalOffsetMeters(42.0, 0.0))
        val history = listOf(start)

        assertEquals(
            listOf(start, end),
            updatedExploredCenters(history, sample(end, 18f, 1_000), sample(start, 18f)),
        )
        assertEquals(
            listOf(start, end),
            updatedExploredCenters(history, sample(end, 18f, 20_000), sample(start, 18f)),
        )
        assertEquals(
            listOf(start, end),
            updatedExploredCenters(history, sample(end, 18f), sample(start, 18f, 1_000)),
        )
    }

    @Test
    fun `live scout is visible immediately but poor accuracy is not saved`() {
        val location = sample(GeoPoint(39.7366, 116.1666), 57f)
        val active = MapUiState(userLocation = location, locationSignalActive = true)
        val stale = active.copy(locationSignalActive = false)

        assertEquals(location.point, active.renderState.liveScoutCenter)
        assertEquals(emptyList<GeoPoint>(), updatedExploredCenters(emptyList(), location))
        assertEquals(null, stale.renderState.liveScoutCenter)
    }

    @Test
    fun `landmark appears only after accurate entry into its check in area`() {
        val landmark = LiangxiangTreasureCatalog.landmarks.first { it.id == "lmk_north_student_service" }
        val outside = com.bitse.geotreasure.core.location.offsetPoint(landmark.point, LocalOffsetMeters(90.0, 0.0))
        val inside = com.bitse.geotreasure.core.location.offsetPoint(landmark.point, LocalOffsetMeters(20.0, 0.0))

        assertTrue(discoverLandmarks(listOf(landmark), emptySet(), sample(outside, 2f)).isEmpty())
        assertTrue(discoverLandmarks(listOf(landmark), emptySet(), sample(inside, 70f)).isEmpty())
        assertEquals(listOf(landmark), discoverLandmarks(listOf(landmark), emptySet(), sample(inside, 2f)))
        assertTrue(discoverLandmarks(listOf(landmark), setOf(landmark.id), sample(inside, 2f)).isEmpty())

        val hidden = MapUiState(landmarks = listOf(landmark))
        assertTrue(hidden.visibleLandmarkIds.isEmpty())
        assertEquals(setOf(landmark.id), hidden.copy(revealedLandmarkIds = setOf(landmark.id)).visibleLandmarkIds)
        assertEquals(setOf(landmark.id), hidden.copy(debugRevealAll = true).visibleLandmarkIds)
    }

    @Test
    fun `landmark reveals before entering its check in area`() {
        val landmark = LiangxiangTreasureCatalog.landmarks.first { it.id == "lmk_north_student_service" }
        val revealOnly = com.bitse.geotreasure.core.location.offsetPoint(
            landmark.point,
            LocalOffsetMeters(70.0, 0.0),
        )
        val outsideReveal = com.bitse.geotreasure.core.location.offsetPoint(
            landmark.point,
            LocalOffsetMeters(85.0, 0.0),
        )

        assertEquals(listOf(landmark), discoverLandmarks(listOf(landmark), emptySet(), sample(revealOnly, 2f)))
        assertTrue(discoverLandmarks(listOf(landmark), emptySet(), sample(outsideReveal, 2f)).isEmpty())
    }

    @Test
    fun `north lake reveals from any measured anchor`() {
        val lake = LiangxiangTreasureCatalog.landmarks.first { it.id == "lmk_north_lake" }
        val anchor = lake.revealPoints[1]

        assertEquals(listOf(lake), discoverLandmarks(listOf(lake), emptySet(), sample(anchor, 2f)))
    }

    @Test
    fun `polygon landmark is discovered within its area`() {
        val landmark = LiangxiangTreasureCatalog.landmarks.first { it.checkInArea is PolygonCheckInArea }
        assertEquals(listOf(landmark), discoverLandmarks(listOf(landmark), emptySet(), sample(landmark.point, 2f)))
    }

    @Test
    fun `debug landmark move changes only rendered coordinates and exposes selected marker`() {
        val landmark = LiangxiangTreasureCatalog.landmarks.first()
        val moved = GeoPoint(landmark.point.latitude + 0.0001, landmark.point.longitude)
        val state = MapUiState(
            landmarks = listOf(landmark),
            selectedLandmarkId = landmark.id,
            debugEnabled = true,
            debugMoveSelected = true,
            debugPointOverrides = mapOf(landmark.id to moved),
        )

        assertEquals(landmark.point, state.landmarks.single().point)
        assertEquals(moved, state.renderState.landmarks.single().point)
        assertEquals(landmark.id, state.renderState.debugDraggableLandmarkId)
        assertEquals(setOf(landmark.id), state.visibleLandmarkIds)
        assertEquals(null, state.copy(debugEnabled = false).renderState.debugDraggableLandmarkId)
    }

    @Test
    fun `rectangle radius decreases by ten meters on each adjustment`() {
        val original = PolygonCheckInArea(
            listOf(
                LocalOffsetMeters(-100.0, -65.0),
                LocalOffsetMeters(100.0, -65.0),
                LocalOffsetMeters(100.0, 65.0),
                LocalOffsetMeters(-100.0, 65.0),
            ),
        )
        val first = original.withDebugRadius(original.debugRadiusMeters() - 10.0)
        val second = first.withDebugRadius(first.debugRadiusMeters() - 10.0)

        assertEquals(100.0, original.debugRadiusMeters(), 0.001)
        assertEquals(90.0, first.debugRadiusMeters(), 0.001)
        assertEquals(80.0, second.debugRadiusMeters(), 0.001)
    }

    private fun sample(point: GeoPoint, accuracyMeters: Float, elapsedMillis: Long = 0L) = LocationSample(
        point = point,
        accuracyMeters = accuracyMeters,
        capturedAt = Instant.fromEpochMilliseconds(1_761_177_600_000L + elapsedMillis),
    )
}
