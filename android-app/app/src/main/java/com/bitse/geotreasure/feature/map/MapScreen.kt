package com.bitse.geotreasure.feature.map


import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationDisabled
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bitse.geotreasure.core.model.LocationSample
import com.bitse.geotreasure.core.model.LocationSourceKind
import com.bitse.geotreasure.core.model.MapEvent
import com.bitse.geotreasure.core.ui.compactDescription
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.bitse.geotreasure.core.ui.components.AppButton
import com.bitse.geotreasure.core.ui.components.BannerBar
import com.bitse.geotreasure.core.ui.components.BannerTone
import com.bitse.geotreasure.core.ui.components.LandmarkImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onOpenLandmark: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    var focusRequest by remember { mutableStateOf<MapFocusRequest?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val canAskAgain = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION)
        } ?: false
        viewModel.onAction(MapAction.PermissionResult(granted, canAskAgain))
    }

    DisposableEffect(lifecycleOwner, context, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
                viewModel.onAction(MapAction.PermissionSnapshot(granted))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                MapEffect.RequestLocationPermission -> permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                MapEffect.OpenAppSettings -> context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )

                is MapEffect.NavigateToLandmark -> onOpenLandmark(effect.landmarkId)
                is MapEffect.ShowMessage -> Unit
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        val shouldUseAmap = !uiState.usingDemoMap && uiState.privacyAccepted
        if (shouldUseAmap) {
            AmapMapContent(
                state = uiState.renderState,
                focusRequest = focusRequest,
                onEvent = { event ->
                    when (event) {
                        is MapEvent.LandmarkSelected -> viewModel.onAction(MapAction.SelectLandmark(event.landmarkId))
                        is MapEvent.LandmarkMoved -> viewModel.onAction(MapAction.MoveDebugLandmark(event.landmarkId, event.point))
                        is MapEvent.CameraMoved -> Unit
                    }
                },
            )
        } else {
            DemoMapContent(
                state = uiState.renderState,
                onLandmarkSelected = { viewModel.onAction(MapAction.SelectLandmark(it)) },
            )
        }

        MapHeader(
            usingDemoMap = uiState.usingDemoMap,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 78.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProgressCard(states = uiState.landmarks.map { it.id in uiState.completedLandmarkIds })
            if (!uiState.privacyAccepted) {
                BannerBar(
                    text = "启用地图与定位：同意高德地图与定位服务后才会初始化 SDK。演示地图不会上传位置。",
                    tone = BannerTone.Warn,
                    icon = Icons.Rounded.Map,
                    actionLabel = "同意并继续",
                    onAction = { viewModel.onAction(MapAction.AcceptPrivacy) },
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                )
            } else {
                PermissionBanner(
                    state = uiState.permissionState,
                    onRequest = { viewModel.onAction(MapAction.RequestLocationPermission) },
                    onOpenSettings = { viewModel.onAction(MapAction.OpenAppSettings) },
                )
            }
            uiState.message?.let { message ->
                BannerBar(
                    text = message,
                    tone = BannerTone.Error,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                )
            }
            if (uiState.debugEnabled) {
                OutlinedButton(onClick = { viewModel.onAction(MapAction.ToggleDebugPanel) }) {
                    Text(if (uiState.debugPanelExpanded) "关闭开发者调试" else "开发者调试")
                }
                if (uiState.debugPanelExpanded) {
                    DeveloperMapDebugPanel(
                        state = uiState,
                        onAction = viewModel::onAction,
                        onCopyLocation = {
                            val location = uiState.userLocation ?: return@DeveloperMapDebugPanel
                            val landmarkId = uiState.selectedLandmarkId ?: return@DeveloperMapDebugPanel
                            val row = listOf(
                                landmarkId,
                                "%.6f".format(Locale.US, location.point.latitude),
                                "%.6f".format(Locale.US, location.point.longitude),
                                location.accuracyMeters.toString(),
                                location.capturedAt.toString(),
                            ).joinToString(",")
                            context.getSystemService(ClipboardManager::class.java)
                                ?.setPrimaryClip(ClipData.newPlainText("地标坐标", row))
                        },
                    )
                }
            }
        }

        if (uiState.isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (shouldUseAmap) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.selectedLandmark?.let { landmark ->
                        FilledIconButton(
                            onClick = {
                                focusRequest = MapFocusRequest(
                                    sequence = (focusRequest?.sequence ?: 0) + 1,
                                    point = landmark.point,
                                )
                            },
                            modifier = Modifier.size(52.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.secondary,
                            ),
                        ) {
                            Icon(Icons.Rounded.LocationOn, contentDescription = "查看选中藏点")
                        }
                    }
                    uiState.userLocation?.let { location ->
                        FilledIconButton(
                            onClick = {
                                focusRequest = MapFocusRequest(
                                    sequence = (focusRequest?.sequence ?: 0) + 1,
                                    point = location.point,
                                )
                            },
                            modifier = Modifier.size(52.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Icon(Icons.Rounded.MyLocation, contentDescription = "回到当前位置")
                        }
                    }
                }
            }
            uiState.selectedLandmark?.let { landmark ->
                LandmarkPanel(
                    landmarkId = landmark.id,
                    name = landmark.name,
                    hint = landmark.hint,
                    checkInAreaDescription = landmark.checkInArea.compactDescription(),
                    distanceMeters = uiState.distanceToSelectedMeters,
                    userLocation = uiState.userLocation,
                    coordinateVerified = landmark.coordinateVerified,
                    completed = landmark.id in uiState.completedLandmarkIds,
                    onOpen = { viewModel.onAction(MapAction.OpenLandmark(landmark.id)) },
                    onClose = { viewModel.onAction(MapAction.ClearSelection) },
                )
            }
        }
    }
}

@Composable
private fun DeveloperMapDebugPanel(
    state: MapUiState,
    onAction: (MapAction) -> Unit,
    onCopyLocation: () -> Unit,
) {
    val context = LocalContext.current
    var landmarkMenuExpanded by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("地图调试", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Box {
                OutlinedButton(onClick = { landmarkMenuExpanded = true }) {
                    Text(state.selectedLandmark?.name ?: "选择调试地标")
                }
                DropdownMenu(expanded = landmarkMenuExpanded, onDismissRequest = { landmarkMenuExpanded = false }) {
                    state.landmarksWithDebugOverrides.forEach { landmark ->
                        DropdownMenuItem(
                            text = { Text(landmark.name) },
                            onClick = {
                                landmarkMenuExpanded = false
                                if (state.selectedLandmarkId != landmark.id) onAction(MapAction.SelectLandmark(landmark.id))
                            },
                        )
                    }
                }
            }
            val location = state.userLocation
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (location == null) {
                        "已探索 ${state.exploredCenters.size} 点 · 等待定位"
                    } else {
                        "已探索 ${state.exploredCenters.size} 点 · ${location.source.displayName()} ±${location.accuracyMeters.roundToInt()}m\n" +
                            "${"%.6f".format(Locale.US, location.point.latitude)}, ${"%.6f".format(Locale.US, location.point.longitude)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onCopyLocation,
                    enabled = !state.usingDemoMap && state.locationSignalActive &&
                        location?.source == LocationSourceKind.GPS && location.accuracyMeters <= 20f &&
                        state.selectedLandmarkId != null,
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "复制选中地标的当前 GCJ-02 坐标")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { onAction(MapAction.ToggleDebugRevealAll) }) {
                    Text(if (state.debugRevealAll) "恢复迷雾" else "显示全图")
                }
                Button(onClick = { onAction(MapAction.ToggleDebugScoutRange) }) {
                    Text(if (state.debugShowScoutRange) "隐藏侦查圈" else "显示侦查圈")
                }
            }
            state.selectedLandmark?.let { landmark ->
                Text("调试地标：${landmark.name}", style = MaterialTheme.typography.bodySmall)
                Text(landmark.checkInArea.compactDescription(), style = MaterialTheme.typography.bodySmall)
                if (!state.usingDemoMap) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { onAction(MapAction.ToggleDebugMoveSelected) }) {
                            Text(if (state.debugMoveSelected) "结束拖动" else "拖动地标")
                        }
                        Text(
                            if (state.debugMoveSelected) "长按地图标记并拖动" else "仅本次会话生效",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Text(
                        "当前 GCJ-02：${"%.6f".format(Locale.US, landmark.point.latitude)}, " +
                            "${"%.6f".format(Locale.US, landmark.point.longitude)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    IconButton(onClick = {
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                            ClipData.newPlainText(
                                "调试地标坐标",
                                "${landmark.id},${"%.6f".format(Locale.US, landmark.point.latitude)}," +
                                    "${"%.6f".format(Locale.US, landmark.point.longitude)}",
                            ),
                        )
                    }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "复制调试地标坐标")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { onAction(MapAction.AdjustDebugRadius(-10.0)) }) {
                        Text("范围 -10m")
                    }
                    OutlinedButton(onClick = { onAction(MapAction.AdjustDebugRadius(10.0)) }) {
                        Text("范围 +10m")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { onAction(MapAction.SetDebugRangeShape(DebugRangeShape.CIRCLE)) }) {
                        Text("圆形")
                    }
                    OutlinedButton(onClick = { onAction(MapAction.SetDebugRangeShape(DebugRangeShape.RECTANGLE)) }) {
                        Text("矩形")
                    }
                    OutlinedButton(onClick = { onAction(MapAction.DebugCompleteSelectedLandmark) }) {
                        Text("完成地标")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(
    states: List<Boolean>,
    modifier: Modifier = Modifier,
) {
    if (states.isEmpty()) return
    val completed = states.count { it }
    val total = states.size
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "校园地标探索",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$completed",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "/$total",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                states.forEach { done ->
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(
                                if (done) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MapHeader(
    usingDemoMap: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Explore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "拼迹 · 校园地标寻宝",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (usingDemoMap) "离线演示地图" else "高德地图 · GCJ-02",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PermissionBanner(
    state: LocationPermissionState,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    when (state) {
        LocationPermissionState.UNKNOWN -> BannerBar(
            text = "显示当前位置：允许精确定位后可查看距离、精度和到点范围。",
            tone = BannerTone.Info,
            icon = Icons.Rounded.MyLocation,
            actionLabel = "授权定位",
            onAction = onRequest,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)),
        )

        LocationPermissionState.DENIED -> BannerBar(
            text = "定位权限已拒绝：地图仍可浏览；重新授权后才会计算你与地标的距离。",
            tone = BannerTone.Warn,
            icon = Icons.Rounded.LocationDisabled,
            actionLabel = "再次授权",
            onAction = onRequest,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)),
        )

        LocationPermissionState.PERMANENTLY_DENIED -> BannerBar(
            text = "需要在系统设置中授权：定位权限已被永久拒绝，请打开应用设置并允许精确位置。",
            tone = BannerTone.Error,
            icon = Icons.Rounded.Settings,
            actionLabel = "打开设置",
            onAction = onOpenSettings,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)),
        )

        LocationPermissionState.GRANTED -> Unit
    }
}

@Composable
private fun LandmarkPanel(
    landmarkId: String,
    name: String,
    hint: String?,
    checkInAreaDescription: String,
    distanceMeters: Double?,
    userLocation: LocationSample?,
    coordinateVerified: Boolean,
    completed: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LandmarkImage(
                    landmarkId = landmarkId,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (completed) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    "已打卡",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = distanceMeters?.let { "距离约 ${it.roundToInt()} 米 · $checkInAreaDescription" }
                            ?: "等待定位 · $checkInAreaDescription",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    userLocation?.let { location ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${location.source.displayName()}定位 · SDK 估计精度 ±${location.accuracyMeters.roundToInt()} 米",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (location.isReliableForCheckIn()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                    if (!coordinateVerified) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "演示坐标 · 待实地核验",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "取消选中地标")
                }
            }
            Spacer(Modifier.height(10.dp))
            AppButton(
                text = if (completed) "查看详情" else "查看任务",
                onClick = onOpen,
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
            )
            hint?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
private fun LocationSourceKind.displayName(): String = when (this) {
    LocationSourceKind.GPS -> "GPS"
    LocationSourceKind.WIFI -> "Wi-Fi"
    LocationSourceKind.CELL -> "基站"
    LocationSourceKind.NETWORK -> "网络"
    LocationSourceKind.CACHE -> "缓存"
    LocationSourceKind.DEMO -> "演示"
    LocationSourceKind.UNKNOWN -> "未知来源"
}

private fun LocationSample.isReliableForCheckIn(): Boolean =
    source == LocationSourceKind.GPS && accuracyMeters <= 30f

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
