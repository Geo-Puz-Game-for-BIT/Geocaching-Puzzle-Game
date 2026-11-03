package com.bitse.geotreasure.core.checkin

import com.bitse.geotreasure.core.location.offsetPoint
import com.bitse.geotreasure.core.model.CircularCheckInArea
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.LocalOffsetMeters
import com.bitse.geotreasure.core.model.LocationSample
import com.bitse.geotreasure.core.model.LocationSourceKind
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckInGateTest {

    private val landmark = GeoPoint(latitude = 39.7300, longitude = 116.1700)
    private val radius = CircularCheckInArea(radiusMeters = 100.0)

    private fun sample(offsetMeters: Double, accuracyMeters: Float): LocationSample {
        val point = if (offsetMeters == 0.0) landmark else {
            offsetPoint(landmark, LocalOffsetMeters(offsetMeters, 0.0))
        }
        return LocationSample(
            point = point,
            accuracyMeters = accuracyMeters,
            capturedAt = Instant.fromEpochMilliseconds(0L),
            source = LocationSourceKind.GPS,
        )
    }

    @Test
    fun nullLocation_isBlockedNoLocation() {
        val result = CheckInGate.evaluate(null, landmark, radius)
        assertTrue(result is CheckInGateResult.Blocked)
        assertEquals(BlockReason.NO_LOCATION, (result as CheckInGateResult.Blocked).reason)
    }

    @Test
    fun poorAccuracy_isBlockedAccuracyTooLow() {
        val result = CheckInGate.evaluate(sample(0.0, accuracyMeters = 40f), landmark, radius)
        assertTrue(result is CheckInGateResult.Blocked)
        assertEquals(BlockReason.ACCURACY_TOO_LOW, (result as CheckInGateResult.Blocked).reason)
    }

    @Test
    fun outOfRange_isBlocked() {
        val result = CheckInGate.evaluate(sample(150.0, accuracyMeters = 10f), landmark, radius)
        assertTrue(result is CheckInGateResult.Blocked)
        assertEquals(BlockReason.OUT_OF_RANGE, (result as CheckInGateResult.Blocked).reason)
    }

    @Test
    fun inRangeAndAccurate_isReady() {
        val result = CheckInGate.evaluate(sample(50.0, accuracyMeters = 10f), landmark, radius)
        assertTrue(result is CheckInGateResult.Ready)
    }
}
