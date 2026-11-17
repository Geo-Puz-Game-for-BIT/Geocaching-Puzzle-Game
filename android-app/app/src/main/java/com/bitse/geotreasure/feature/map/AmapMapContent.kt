package com.bitse.geotreasure.feature.map

import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.maps.MapView
import com.bitse.geotreasure.core.map.AmapMapAdapter
import com.bitse.geotreasure.core.map.AmapRenderLocationSource
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.MapEvent
import com.bitse.geotreasure.core.model.MapRenderState
import kotlin.math.cos
import kotlinx.coroutines.delay

@Composable
fun AmapMapContent(
    state: MapRenderState,
    focusRequest: MapFocusRequest?,
    onEvent: (MapEvent) -> Unit,
    modifier: Modifier = Modifier,
    onMarkerScreens: (Map<String, Offset>) -> Unit = {},
    onFogFrame: (FogGeoFrame) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapView(context).apply { onCreate(Bundle()) }
    }
    val locationSource = remember { AmapRenderLocationSource() }
    val adapter = remember(mapView, locationSource) { AmapMapAdapter(mapView.map, locationSource) }

    // P01 迷雾：把地标屏幕坐标与地理锚定帧回传给调用方
    // （FogOverlay 用屏幕坐标开孔、用锚定帧把云场「套在地图上」）
    val latestState by rememberUpdatedState(state)
    fun pushFogScreens() {
        val screens = mutableMapOf<String, Offset>()
        latestState.landmarks.forEach { landmark ->
            adapter.screenPoint(landmark.point)?.let { point ->
                screens[landmark.id] = Offset(point.first, point.second)
            }
        }
        onMarkerScreens(screens)
        // 地理锚定帧：锚点 = 地标包围盒中心；pxPerMeter 用东向 500m 参考点实测投影求出
        val anchor = fogAnchorPoint(latestState.landmarks)
        val origin = adapter.screenPoint(anchor) ?: return
        val radLat = Math.toRadians(anchor.latitude)
        val east = GeoPoint(
            latitude = anchor.latitude,
            longitude = anchor.longitude + 500.0 / (111_320.0 * cos(radLat)),
        )
        val eastScreen = adapter.screenPoint(east) ?: return
        val metersPx = kotlin.math.hypot(
            (eastScreen.first - origin.first).toDouble(),
            (eastScreen.second - origin.second).toDouble(),
        ) / 500.0
        if (metersPx > 1e-5) {
            onFogFrame(FogGeoFrame(Offset(origin.first, origin.second), metersPx.toFloat()))
        }
    }

    LaunchedEffect(adapter) {
        adapter.observeEvents().collect { event ->
            onEvent(event)
            if (event is MapEvent.CameraMoved) pushFogScreens()
        }
    }

    // 地图初始化/首帧布局后 projection 才可用：前几帧轮询兜底
    LaunchedEffect(adapter) {
        repeat(6) { attempt ->
            delay(if (attempt == 0) 500L else 700L)
            pushFogScreens()
        }
    }

    LaunchedEffect(adapter, focusRequest) {
        focusRequest?.let { adapter.focusOn(it.point) }
    }

    DisposableEffect(lifecycleOwner, mapView, adapter) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adapter.release()
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize(),
        update = {
            adapter.render(latestState)
            pushFogScreens()
        },
    )
}
