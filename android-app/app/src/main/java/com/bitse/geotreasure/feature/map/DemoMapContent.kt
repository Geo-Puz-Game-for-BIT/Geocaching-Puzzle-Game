package com.bitse.geotreasure.feature.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import com.bitse.geotreasure.core.model.CheckInArea
import com.bitse.geotreasure.core.model.CircularCheckInArea
import com.bitse.geotreasure.core.model.FogOfWarRules
import com.bitse.geotreasure.core.model.Landmark
import com.bitse.geotreasure.core.model.MapRenderState
import com.bitse.geotreasure.core.model.PolygonCheckInArea

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

        // P01 迷雾 v3：卡通云层铺满全图，已打卡地标开软边孔（画在底图之上、水滴之下，对齐原型层级）
        if (!state.debugRevealAll) {
            FogOverlay(
                landmarks = state.landmarks,
                completedLandmarkIds = state.completedLandmarkIds,
                holeScreenPos = { landmark ->
                    projection?.position(landmark.point)?.let { position ->
                        Offset(position.xFraction * constraints.maxWidth, position.yFraction * constraints.maxHeight)
                    }
                },
                // 演示路径 marker 由 Compose 画在本层之上，无需开小孔
                modifier = Modifier.fillMaxSize(),
            )
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            // P01 地标标记（对齐原型 dropPin）：倒立水滴锚点=地标坐标，已打卡绿底白勾 / 未打卡绿底白问号
            state.landmarks.filter { it.id in state.visibleLandmarkIds }.forEach { landmark ->
                val position = projection?.position(landmark.point) ?: return@forEach
                DemoLandmarkMarker(
                    landmark = landmark,
                    selected = landmark.id == state.selectedLandmarkId,
                    isDone = landmark.id in state.completedLandmarkIds,
                    onClick = { onLandmarkSelected(landmark.id) },
                    modifier = Modifier.offset(
                        x = maxWidth * position.xFraction - 17.dp,
                        y = maxHeight * position.yFraction - 34.dp,
                    ),
                )
            }
        }
        // 旧深灰迷雾已由 FogOverlay（卡通云层）替代；此画布仅保留侦查圈调试环
        Canvas(Modifier.fillMaxSize()) {
            if (state.debugShowScoutRange) {
                projection?.let { mapProjection ->
                    state.liveScoutCenter?.let { center ->
                        val position = mapProjection.position(center)
                        val radius = mapProjection.radiusFractions(FogOfWarRules.SCOUT_RADIUS_METERS)
                        drawOval(
                            color = Color(0xCC66D7B0),
                            topLeft = Offset(size.width * (position.xFraction - radius.xFraction), size.height * (position.yFraction - radius.yFraction)),
                            size = Size(size.width * radius.xFraction * 2, size.height * radius.yFraction * 2),
                            style = Stroke(width = 3.dp.toPx()),
                        )
                    }
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
    isDone: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val areaColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier.size(96.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        if (selected) {
            // 选中态：打卡区域高亮圈（水滴尖端锚点在地标坐标上，圈心即 34dp/2 = 17dp 处）
            DemoCheckInArea(
                area = landmark.checkInArea,
                color = areaColor,
                modifier = Modifier
                    .offset(x = 17.dp - 48.dp, y = 34.dp - 48.dp)
                    .size(96.dp),
            )
        }
        LandmarkPin(
            isDone = isDone,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
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

