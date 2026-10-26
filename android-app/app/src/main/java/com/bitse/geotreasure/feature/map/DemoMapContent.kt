package com.bitse.geotreasure.feature.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import com.bitse.geotreasure.core.model.CampusZone
import com.bitse.geotreasure.core.model.CheckInArea
import com.bitse.geotreasure.core.model.CircularCheckInArea
import com.bitse.geotreasure.core.model.FogOfWarRules
import com.bitse.geotreasure.core.model.Landmark
import com.bitse.geotreasure.core.model.MapRenderState
import com.bitse.geotreasure.core.model.PolygonCheckInArea
import com.bitse.geotreasure.core.model.campusRevealBlocks

@Composable
fun DemoMapContent(
    state: MapRenderState,
    onLandmarkSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tertiary = MaterialTheme.colorScheme.tertiary
    val projection = remember(state.landmarks) {
        state.landmarks.takeIf { it.isNotEmpty() }?.let(::DemoMapProjection)
    }
    val revealBlocks = remember(state.landmarks) { campusRevealBlocks(state.landmarks) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFE9EEE7)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val gridColor = Color(0xFFC8D2C8)
            val blockColor = Color(0xFFF7F7F1)
            val roadColor = Color(0xFFFFFFFF)
            val step = size.minDimension / 7f
            for (index in 0..8) {
                drawLine(
                    color = gridColor,
                    start = Offset(index * step, 0f),
                    end = Offset(index * step, size.height),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f)),
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, index * step),
                    end = Offset(size.width, index * step),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f)),
                )
            }
            drawRoundRect(
                color = blockColor,
                topLeft = Offset(size.width * 0.08f, size.height * 0.16f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.34f, size.height * 0.19f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f),
            )
            drawLine(
                color = roadColor,
                start = Offset(0f, size.height * 0.55f),
                end = Offset(size.width, size.height * 0.42f),
                strokeWidth = 34f,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = tertiary.copy(alpha = 0.14f),
                radius = 72f,
                center = center,
            )
            drawCircle(
                color = tertiary,
                radius = 18f,
                center = center,
                style = Stroke(width = 8f),
            )
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            state.landmarks.forEach { landmark ->
                val position = projection?.position(landmark.point) ?: return@forEach
                DemoLandmarkMarker(
                    landmark = landmark,
                    selected = landmark.id == state.selectedLandmarkId,
                    color = landmark.campusZone.markerColor(),
                    onClick = { onLandmarkSelected(landmark.id) },
                    modifier = Modifier.offset(
                        x = maxWidth * position.xFraction - 24.dp,
                        y = maxHeight * position.yFraction - 24.dp,
                    ),
                )
            }
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            if (!state.debugRevealAll) {
                drawRect(color = Color(0x66343A40))
                projection?.let { mapProjection ->
                    state.exploredCenters.forEach { explored ->
                        val position = mapProjection.position(explored)
                        val radius = mapProjection.radiusFractions(FogOfWarRules.SCOUT_RADIUS_METERS)
                        drawOval(
                            color = Color.Transparent,
                            topLeft = Offset(size.width * (position.xFraction - radius.xFraction), size.height * (position.yFraction - radius.yFraction)),
                            size = Size(size.width * radius.xFraction * 2, size.height * radius.yFraction * 2),
                            blendMode = BlendMode.Clear,
                        )
                    }
                    state.liveScoutCenter?.let { center ->
                        val position = mapProjection.position(center)
                        val radius = mapProjection.radiusFractions(FogOfWarRules.SCOUT_RADIUS_METERS)
                        drawOval(
                            color = Color.Transparent,
                            topLeft = Offset(size.width * (position.xFraction - radius.xFraction), size.height * (position.yFraction - radius.yFraction)),
                            size = Size(size.width * radius.xFraction * 2, size.height * radius.yFraction * 2),
                            blendMode = BlendMode.Clear,
                        )
                    }
                    if (state.debugShowScoutRange && state.liveScoutCenter != null) {
                        val position = mapProjection.position(state.liveScoutCenter)
                        val radius = mapProjection.radiusFractions(FogOfWarRules.SCOUT_RADIUS_METERS)
                        drawOval(
                            color = Color(0xCC66D7B0),
                            topLeft = Offset(size.width * (position.xFraction - radius.xFraction), size.height * (position.yFraction - radius.yFraction)),
                            size = Size(size.width * radius.xFraction * 2, size.height * radius.yFraction * 2),
                            style = Stroke(width = 3.dp.toPx()),
                        )
                    }
                }
                revealBlocks.filter { it.landmarkId in state.completedLandmarkIds }.forEach { block ->
                    val mapProjection = projection ?: return@forEach
                    val path = Path()
                    block.vertices.forEachIndexed { index, vertex ->
                        val position = mapProjection.position(vertex)
                        val x = size.width * position.xFraction
                        val y = size.height * position.yFraction
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(path, color = Color.Transparent, blendMode = BlendMode.Clear)
                }
            }
        }
        if (state.userLocation != null && projection != null) {
            val position = projection.position(state.userLocation.point)
            Icon(
                imageVector = Icons.Rounded.MyLocation,
                contentDescription = "当前位置",
                tint = tertiary,
                modifier = Modifier
                    .offset(x = maxWidth * position.xFraction - 15.dp, y = maxHeight * position.yFraction - 15.dp)
                    .size(30.dp),
            )
        }
    }
}

@Composable
private fun DemoLandmarkMarker(
    landmark: Landmark,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(96.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            DemoCheckInArea(
                area = landmark.checkInArea,
                color = color,
                modifier = Modifier.fillMaxSize(),
            )
        }
        MapMarkerButton(
            landmark = landmark,
            selected = selected,
            color = color,
            onClick = onClick,
        )
    }
}

@Composable
private fun DemoCheckInArea(
    area: CheckInArea,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        when (area) {
            is CircularCheckInArea -> {
                drawCircle(color = color.copy(alpha = 0.14f), radius = size.minDimension * 0.45f)
                drawCircle(
                    color = color.copy(alpha = 0.75f),
                    radius = size.minDimension * 0.45f,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }

            is PolygonCheckInArea -> {
                val minEast = area.vertices.minOf { it.eastMeters }
                val maxEast = area.vertices.maxOf { it.eastMeters }
                val minNorth = area.vertices.minOf { it.northMeters }
                val maxNorth = area.vertices.maxOf { it.northMeters }
                val eastSpan = (maxEast - minEast).coerceAtLeast(1.0)
                val northSpan = (maxNorth - minNorth).coerceAtLeast(1.0)
                val padding = size.minDimension * 0.08f
                val path = Path()
                area.vertices.forEachIndexed { index, vertex ->
                    val x = padding + ((vertex.eastMeters - minEast) / eastSpan).toFloat() * (size.width - padding * 2)
                    val y = padding + ((maxNorth - vertex.northMeters) / northSpan).toFloat() * (size.height - padding * 2)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color = color.copy(alpha = 0.14f))
                drawPath(path, color = color.copy(alpha = 0.75f), style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}

@Composable
private fun MapMarkerButton(
    landmark: Landmark,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(if (selected) 52.dp else 44.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondary else color,
            contentColor = Color.White,
        ),
    ) {
        Icon(Icons.Rounded.LocationOn, contentDescription = "选择${landmark.name}")
    }
}

@Composable
private fun CampusZone.markerColor(): Color = when (this) {
    CampusZone.NORTH -> MaterialTheme.colorScheme.primary
    CampusZone.EAST -> MaterialTheme.colorScheme.tertiary
    CampusZone.SOUTH -> MaterialTheme.colorScheme.secondary
}
