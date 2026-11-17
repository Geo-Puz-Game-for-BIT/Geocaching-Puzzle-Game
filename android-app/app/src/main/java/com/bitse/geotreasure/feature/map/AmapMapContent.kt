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
import com.bitse.geotreasure.core.model.MapEvent
import com.bitse.geotreasure.core.model.MapRenderState
import kotlinx.coroutines.delay

@Composable
fun AmapMapContent(
    state: MapRenderState,
    focusRequest: MapFocusRequest?,
    onEvent: (MapEvent) -> Unit,
    modifier: Modifier = Modifier,
    onMarkerScreens: (Map<String, Offset>) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapView(context).apply { onCreate(Bundle()) }
    }
    val locationSource = remember { AmapRenderLocationSource() }
    val adapter = remember(mapView, locationSource) { AmapMapAdapter(mapView.map, locationSource) }

    // P01 迷雾：把全部地标的屏幕像素坐标回传给调用方（FogOverlay 用来在 marker/已打卡处开孔）
    val latestState by rememberUpdatedState(state)
    fun pushMarkerScreens() {
        val screens = mutableMapOf<String, Offset>()
        latestState.landmarks.forEach { landmark ->
            adapter.screenPoint(landmark.point)?.let { point ->
                screens[landmark.id] = Offset(point.first, point.second)
            }
        }
        onMarkerScreens(screens)
    }

    LaunchedEffect(adapter) {
        adapter.observeEvents().collect { event ->
            onEvent(event)
            if (event is MapEvent.CameraMoved) pushMarkerScreens()
        }
    }

    // 地图初始化/首帧布局后 projection 才可用：前几帧轮询兜底
    LaunchedEffect(adapter) {
        repeat(6) { attempt ->
            delay(if (attempt == 0) 500L else 700L)
            pushMarkerScreens()
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
            pushMarkerScreens()
        },
    )
}
