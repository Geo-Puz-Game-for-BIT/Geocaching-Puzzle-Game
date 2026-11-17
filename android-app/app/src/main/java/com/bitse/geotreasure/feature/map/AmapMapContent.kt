package com.bitse.geotreasure.feature.map

import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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

@Composable
fun AmapMapContent(
    state: MapRenderState,
    focusRequest: MapFocusRequest?,
    onEvent: (MapEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapView(context).apply { onCreate(Bundle()) }
    }
    val locationSource = remember { AmapRenderLocationSource() }
    val adapter = remember(mapView, locationSource) { AmapMapAdapter(mapView.map, locationSource) }
    LaunchedEffect(adapter) {
        adapter.observeEvents().collect(onEvent)
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
        update = { adapter.render(state) },
    )
}
