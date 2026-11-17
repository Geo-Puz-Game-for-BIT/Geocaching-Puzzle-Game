package com.bitse.geotreasure.core.map

import com.bitse.geotreasure.core.location.localOffsetMeters
import com.bitse.geotreasure.core.location.offsetPoint
import com.bitse.geotreasure.core.model.CampusZone
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.LocalOffsetMeters
import com.bitse.geotreasure.core.model.LocationSample
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusMapFlowTest {
    private val origin = GeoPoint(39.73, 116.17)
    private val north = rectangle(-100.0, 0.0, 0.0, 100.0)
    private val layout = CampusMapLayout(
        schoolOutline = rectangle(-110.0, -130.0, 130.0, 110.0),
        overviewAssetKey = "school-overview",
        rooms = listOf(
            CampusMapRoom(CampusZone.NORTH, north, "north"),
            CampusMapRoom(CampusZone.EAST, rectangle(20.0, 0.0, 120.0, 100.0), "east"),
            CampusMapRoom(CampusZone.SOUTH, rectangle(-100.0, -120.0, 120.0, -20.0), "south"),
        ),
        doors = listOf(CampusMapDoor(
            id = "north-east-gate",
            firstZone = CampusZone.NORTH,
            firstSide = rectangle(-15.0, 40.0, 0.0, 60.0),
            secondZone = CampusZone.EAST,
            secondSide = rectangle(20.0, 40.0, 35.0, 60.0),
            transitOutline = rectangle(-15.0, 40.0, 35.0, 60.0),
        )),
    )

    @Test
    fun `gate crossing switches room only after two reliable fixes on the exit side`() {
        val initial = CampusMapFlow.update(CampusMapState(), sample(-50.0, 50.0, 0), time(0), layout).state
        assertEquals(CampusZone.NORTH, initial.room)
        val atGate = CampusMapFlow.update(initial, sample(-8.0, 50.0, 1), time(1), layout)
        assertEquals("north-east-gate", atGate.state.armedDoorId)
        val betweenRooms = CampusMapFlow.update(atGate.state, sample(10.0, 50.0, 2), time(2), layout)
        assertNull(betweenRooms.state.warning)
        val firstExitFix = CampusMapFlow.update(betweenRooms.state, sample(28.0, 50.0, 3), time(3), layout)
        assertEquals(CampusZone.NORTH, firstExitFix.state.room)
        assertNull(firstExitFix.transition)
        val crossed = CampusMapFlow.update(firstExitFix.state, sample(30.0, 50.0, 4), time(4), layout)
        assertEquals(CampusZone.EAST, crossed.state.room)
        assertEquals(CampusDoorTransition("north-east-gate", CampusZone.NORTH, CampusZone.EAST), crossed.transition)
    }

    @Test
    fun `approaching and leaving away from a gate warns without switching rooms`() {
        val initial = CampusMapFlow.update(CampusMapState(), sample(-50.0, 80.0, 0), time(0), layout).state
        val nearEdge = CampusMapFlow.update(initial, sample(-5.0, 80.0, 1), time(1), layout)
        assertEquals(CampusBoundaryWarning.APPROACHING_OUTSIDE_GATE, nearEdge.state.warning)
        val outside = CampusMapFlow.update(nearEdge.state, sample(10.0, 80.0, 2), time(2), layout)
        assertEquals(CampusBoundaryWarning.OUTSIDE_WITHOUT_GATE, outside.state.warning)
        assertEquals(CampusZone.NORTH, outside.state.room)
        assertNull(outside.transition)
    }

    @Test
    fun `low accuracy and stale samples cannot trigger room transition`() {
        val initial = CampusMapFlow.update(CampusMapState(), sample(-50.0, 50.0, 0), time(0), layout).state
        val atGate = CampusMapFlow.update(initial, sample(-8.0, 50.0, 1), time(1), layout).state
        val lowAccuracy = CampusMapFlow.update(atGate, sample(28.0, 50.0, 2, 80f), time(2), layout)
        val stale = CampusMapFlow.update(atGate, sample(28.0, 50.0, 2), time(10), layout)
        assertEquals(CampusZone.NORTH, lowAccuracy.state.room)
        assertNull(lowAccuracy.transition)
        assertEquals(CampusZone.NORTH, stale.state.room)
        assertNull(stale.transition)
    }

    @Test
    fun `repeated target fix timestamp does not count as a second gate crossing sample`() {
        val initial = CampusMapFlow.update(CampusMapState(), sample(-50.0, 50.0, 0), time(0), layout).state
        val atGate = CampusMapFlow.update(initial, sample(-8.0, 50.0, 1), time(1), layout).state
        val first = CampusMapFlow.update(atGate, sample(28.0, 50.0, 2), time(2), layout)
        val repeated = CampusMapFlow.update(first.state, sample(28.0, 50.0, 2), time(3), layout)

        assertEquals(1, repeated.state.candidateFixes)
        assertEquals(CampusZone.NORTH, repeated.state.room)
        assertNull(repeated.transition)
    }

    @Test
    fun `leaving the gate corridor disarms it and warns outside`() {
        val initial = CampusMapFlow.update(CampusMapState(), sample(-50.0, 50.0, 0), time(0), layout).state
        val atGate = CampusMapFlow.update(initial, sample(-8.0, 50.0, 1), time(1), layout).state
        val offCorridor = CampusMapFlow.update(atGate, sample(10.0, 80.0, 2), time(2), layout)

        assertNull(offCorridor.state.armedDoorId)
        assertEquals(CampusBoundaryWarning.OUTSIDE_WITHOUT_GATE, offCorridor.state.warning)
        assertNull(offCorridor.transition)
    }

    @Test
    fun `viewport center and extent remain within the selected map bounds`() {
        val clamped = CampusMapFlow.clampViewport(north, point(50.0, 50.0), 40.0, 40.0)
        val offset = localOffsetMeters(origin, clamped.center)
        assertEquals(-20.0, offset.eastMeters, 0.1)
        assertEquals(50.0, offset.northMeters, 0.1)
        assertFalse(clamped.needsMaskOrLetterbox)

        val oversized = CampusMapFlow.clampViewport(north, point(50.0, 50.0), 200.0, 200.0)
        assertEquals(100.0, oversized.visibleWidthMeters, 0.1)
        assertEquals(100.0, oversized.visibleHeightMeters, 0.1)
        assertTrue(oversized.needsMaskOrLetterbox)
    }

    @Test
    fun `without a known room only the campus overview mode is available`() {
        val state = CampusMapFlow.selectViewMode(CampusMapState(), CampusMapViewMode.FOLLOW_USER)
        assertEquals(CampusMapViewMode.OVERVIEW, state.viewMode)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `degenerate room outline is rejected before rendering`() {
        CampusMapRoom(
            CampusZone.NORTH,
            listOf(point(0.0, 0.0), point(10.0, 0.0), point(20.0, 0.0)),
            "north",
        )
    }

    private fun point(east: Double, north: Double): GeoPoint =
        offsetPoint(origin, LocalOffsetMeters(east, north))

    private fun rectangle(west: Double, south: Double, east: Double, north: Double) = listOf(
        point(west, south), point(east, south), point(east, north), point(west, north),
    )

    private fun time(seconds: Long): Instant = Instant.fromEpochMilliseconds(seconds * 1_000)

    private fun sample(east: Double, north: Double, seconds: Long, accuracy: Float = 3f) = LocationSample(
        point = point(east, north),
        accuracyMeters = accuracy,
        capturedAt = time(seconds),
    )
}
