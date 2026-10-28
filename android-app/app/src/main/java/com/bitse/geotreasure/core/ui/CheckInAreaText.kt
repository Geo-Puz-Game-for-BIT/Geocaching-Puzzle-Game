package com.bitse.geotreasure.core.ui

import com.bitse.geotreasure.core.model.CheckInArea
import com.bitse.geotreasure.core.model.CircularCheckInArea
import com.bitse.geotreasure.core.model.PolygonCheckInArea
import kotlin.math.roundToInt

fun CheckInArea.compactDescription(): String = when (this) {
    is CircularCheckInArea -> "半径 ${radiusMeters.roundToInt()} 米圆形区域"
    is PolygonCheckInArea -> {
        val width = vertices.maxOf { it.eastMeters } - vertices.minOf { it.eastMeters }
        val height = vertices.maxOf { it.northMeters } - vertices.minOf { it.northMeters }
        "约 ${width.roundToInt()} × ${height.roundToInt()} 米自定义区域"
    }
}
