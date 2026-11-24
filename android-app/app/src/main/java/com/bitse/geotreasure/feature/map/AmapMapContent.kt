package com.bitse.geotreasure.feature.map

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.bitse.geotreasure.core.map.AmapMapAdapter
import com.bitse.geotreasure.core.map.AmapRenderLocationSource
import com.bitse.geotreasure.core.map.CampusArtworkCatalog
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.MapEvent
import com.bitse.geotreasure.core.model.MapRenderState
import kotlin.math.cos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 单张校正底图解码内存预算（ARGB_8888 估算）：三张合计约 10MB，留足余量 */
private const val ARTWORK_DECODE_BUDGET_BYTES = 16L * 1024 * 1024

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
        // 地理锚定帧：锚点 = 地标包围盒中心；pxPerMeter 用东向 500m 参考点实测投影求出；
        // bearingDegrees = 该东向参考向量的屏幕方向角（地图旋转手势会实时改变它，迷雾随之旋转）
        val anchor = fogAnchorPoint(latestState.landmarks)
        val origin = adapter.screenPoint(anchor) ?: return
        val radLat = Math.toRadians(anchor.latitude)
        val east = GeoPoint(
            latitude = anchor.latitude,
            longitude = anchor.longitude + 500.0 / (111_320.0 * cos(radLat)),
        )
        val eastScreen = adapter.screenPoint(east) ?: return
        val dxEast = eastScreen.first - origin.first
        val dyEast = eastScreen.second - origin.second
        val metersPx = kotlin.math.hypot(dxEast.toDouble(), dyEast.toDouble()) / 500.0
        if (metersPx > 1e-5) {
            val bearingDeg = Math.toDegrees(kotlin.math.atan2(dyEast.toDouble(), dxEast.toDouble())).toFloat()
            onFogFrame(
                FogGeoFrame(
                    Offset(origin.first, origin.second),
                    metersPx.toFloat(),
                    bearingDegrees = bearingDeg,
                ),
            )
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

    // P01 底图嵌入：三张校正插画按各自经纬度矩形铺在地图上（一次性安装，失败显式提示）
    var artworkError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(adapter, context) {
        val specs = runCatching { CampusArtworkCatalog.load(context) }
            .getOrElse { failure ->
                artworkError = "底图配置不可用：${failure.message ?: "解析失败"}"
                return@LaunchedEffect
            }
        adapter.prepareArtworkBase()
        var installed = 0
        specs.forEach { spec ->
            runCatching {
                val bitmap = withContext(Dispatchers.IO) {
                    check(spec.decodeByteEstimate() <= ARTWORK_DECODE_BUDGET_BYTES) {
                        "${spec.campusId} 解码内存估算 ${spec.decodeByteEstimate() / 1024 / 1024}MB 超出预算"
                    }
                    context.assets.open(spec.assetPath).use { stream ->
                        BitmapFactory.decodeStream(stream)
                    } ?: error("无法解码 ${spec.assetPath}")
                }
                val descriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
                try {
                    adapter.installArtwork(spec, descriptor)
                } catch (failure: Throwable) {
                    // install 失败时 descriptor 仍归调用方释放
                    descriptor.recycle()
                    throw failure
                }
            }.onSuccess {
                installed++
            }.onFailure { failure ->
                if (failure is CancellationException) throw failure
                artworkError = "底图 ${spec.campusId} 加载失败：${failure.message ?: "未知错误"}"
            }
        }
        if (installed == 0) {
            artworkError = artworkError ?: "底图资源未安装"
        }
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

    Box(modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = {
                adapter.render(latestState)
                pushFogScreens()
            },
        )
        artworkError?.let { message ->
            // 资源不可用时明确报错，不静默冒充「底图已嵌入」
            Text(
                text = message,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
                    .background(Color(0xCC7A2E1C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
