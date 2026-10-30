package com.bitse.geotreasure.core.map

import android.location.Location
import android.os.SystemClock
import com.amap.api.maps.LocationSource
import com.bitse.geotreasure.core.model.LocationSample

internal class AmapRenderLocationSource : LocationSource {
    private var listener: LocationSource.OnLocationChangedListener? = null
    private var latestSample: LocationSample? = null

    override fun activate(listener: LocationSource.OnLocationChangedListener) {
        this.listener = listener
        latestSample?.let(::dispatch)
    }

    override fun deactivate() {
        listener = null
    }

    fun update(sample: LocationSample?) {
        latestSample = sample
        sample?.let(::dispatch)
    }

    private fun dispatch(sample: LocationSample) {
        listener?.onLocationChanged(
            Location(PROVIDER_NAME).apply {
                latitude = sample.point.latitude
                longitude = sample.point.longitude
                accuracy = sample.accuracyMeters
                time = sample.capturedAt.toEpochMilliseconds()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            },
        )
    }

    companion object {
        private const val PROVIDER_NAME = "geotreasure"
    }
}
