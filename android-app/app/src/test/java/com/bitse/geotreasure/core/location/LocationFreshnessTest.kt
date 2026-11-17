package com.bitse.geotreasure.core.location

import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.LocationSample
import com.bitse.geotreasure.core.model.LocationSourceKind
import kotlinx.datetime.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFreshnessTest {

    private val now = Instant.fromEpochMilliseconds(1_000_000_000_000L)

    private fun sample(ageSeconds: Long): LocationSample = LocationSample(
        point = GeoPoint(39.0, 116.0),
        accuracyMeters = 10f,
        capturedAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - ageSeconds * 1000L),
        source = LocationSourceKind.GPS,
    )

    @Test
    fun nullSample_isNotFresh() {
        assertFalse(LocationFreshness.isFresh(null, now))
    }

    @Test
    fun ageWithinWindow_isFresh() {
        assertTrue(LocationFreshness.isFresh(sample(0L), now))
        assertTrue(LocationFreshness.isFresh(sample(5L), now))
    }

    @Test
    fun ageBeyondWindow_isStale() {
        assertFalse(LocationFreshness.isFresh(sample(6L), now))
    }

    @Test
    fun futureTimestamp_isNotFresh() {
        val future = LocationSample(
            point = GeoPoint(39.0, 116.0),
            accuracyMeters = 10f,
            capturedAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + 1000L),
            source = LocationSourceKind.GPS,
        )
        assertFalse(LocationFreshness.isFresh(future, now))
    }
}
