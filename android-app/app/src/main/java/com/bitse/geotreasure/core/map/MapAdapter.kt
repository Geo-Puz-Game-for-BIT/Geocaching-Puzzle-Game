package com.bitse.geotreasure.core.map

import com.bitse.geotreasure.core.model.MapEvent
import com.bitse.geotreasure.core.model.MapRenderState
import com.bitse.geotreasure.core.model.GeoPoint
import kotlinx.coroutines.flow.Flow

interface MapAdapter {
    fun render(state: MapRenderState)

    fun focusOn(point: GeoPoint)

    fun observeEvents(): Flow<MapEvent>

    fun release()
}
