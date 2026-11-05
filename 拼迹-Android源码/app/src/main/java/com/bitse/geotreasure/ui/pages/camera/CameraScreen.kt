package com.bitse.geotreasure.ui.pages.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitse.geotreasure.domain.model.Landmark
import com.bitse.geotreasure.ui.components.AppButton
import com.bitse.geotreasure.ui.components.AppButtonStyle
import com.bitse.geotreasure.ui.components.BannerBar
import com.bitse.geotreasure.ui.components.BannerTone
import com.bitse.geotreasure.ui.components.LandmarkImage
import com.bitse.geotreasure.ui.components.PageHeader
import com.bitse.geotreasure.ui.theme.AppText
import com.bitse.geotreasure.ui.theme.CameraScrim
import com.bitse.geotreasure.ui.theme.CameraSurface
import com.bitse.geotreasure.ui.theme.Dimen
import com.bitse.geotreasure.ui.theme.ErrorRed
import com.bitse.geotreasure.ui.theme.Primary
import com.bitse.geotreasure.ui.theme.Radius
import java.io.File

/**
 * P03 · 拍照打卡。
 *
 * 取景区是唯一允许「深色铺满」的页面（PageHeader dark + CameraScrim），
 * 拍摄逻辑走 CameraX：预览用 Preview，快门用 ImageCapture，照片落在 cache/checkin/ 下。
 *
 * 注意：这里不判断「离得够不够近」「精度够不够」—— 那是服务端的事。
 * 界面只负责：显示距目标多远、把照片拍好、把照片和定位一起提交。
 */
@Composable
fun CameraRoute(
    onBack: () -> Unit,
    onFinished: (outcomeKey: String) -> Unit,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onPermissionResult(granted) }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.onPermissionResult(true)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CameraEvent.Finished -> onFinished(event.outcomeKey)
            }
        }
    }

    CameraScreen(
        state = state,
        onBack = onBack,
        onCaptured = viewModel::onCaptured,
        onRetake = viewModel::retake,
        onSubmit = viewModel::submit,
        onRetrySubmit = viewModel::retrySubmit,
        onOpenSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    )
}

@Composable
fun CameraScreen(
    state: CameraUiState,
    onBack: () -> Unit,
    onCaptured: (String) -> Unit,
    onRetake: () -> Unit,
    onSubmit: () -> Unit,
    onRetrySubmit: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val landmark = state.landmark
    val context = LocalContext.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(CameraScrim).statusBarsPadding()) {
        PageHeader(
            title = if (state.phase == CameraPhase.Review) "拍后预览" else "拍照打卡",
            subtitle = when {
                // 原型 p03:denied 的副标题是权限状态，不是地标名
                state.permission == CameraPermission.Denied -> "相机权限未开启"
                state.phase == CameraPhase.Review -> "${landmark?.name ?: ""} · 确认人物与地标同框"
                state.phase == CameraPhase.Error -> "${landmark?.name ?: ""} · 本次提交没有成功"
                else -> "${landmark?.name ?: ""} · 同框打卡"
            },
            onBack = onBack,
            dark = true
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.permission == CameraPermission.Denied ->
                    PermissionDeniedMask(onOpenSettings = onOpenSettings, onBack = onBack)

                state.phase == CameraPhase.Error ->
                    SubmitErrorMask(onRetry = onRetrySubmit, onBack = onBack)

                state.phase == CameraPhase.Submitting ->
                    SubmittingMask()

                state.phase == CameraPhase.Review ->
                    ReviewShot(
                        photoPath = state.photoPath,
                        landmarkName = landmark?.name ?: "",
                        onSubmit = onSubmit,
                        onRetake = onRetake
                    )

                else -> LiveViewfinder(
                    state = state,
                    landmark = landmark,
                    onImageCaptureReady = { imageCapture = it },
                    onShutter = {
                        val capture = imageCapture ?: return@LiveViewfinder
                        takePicture(
                            context = context,
                            imageCapture = capture,
                            onSaved = onCaptured,
                            onError = { /* 拍摄失败留在取景页，用户可再按一次 */ }
                        )
                    }
                )
            }
        }
    }
}

/* ---------------- 取景态 ---------------- */

@Composable
private fun LiveViewfinder(
    state: CameraUiState,
    landmark: Landmark?,
    onImageCaptureReady: (ImageCapture) -> Unit,
    onShutter: () -> Unit
) {
    var cameraReady by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 相机还没起来时先铺一张地标图，避免黑屏；相机 ready 后由 PreviewView 盖住
        if (landmark != null) {
            LandmarkImage(
                landmarkId = landmark.id,
                modifier = Modifier.fillMaxSize().alpha(if (cameraReady) 0f else 0.55f),
                big = true,
                crop = true
            )
        }

        CameraPreview(
            modifier = Modifier.fillMaxSize().alpha(if (cameraReady) 1f else 0f),
            lensFacing = lensFacing,
            onReady = { cameraReady = true },
            onImageCaptureReady = onImageCaptureReady
        )

        // 地面压暗：原型 .shutterbar 自带「透明 → 55% 黑」的渐变，提示条才压得住画面。
        // 真机预览比原型那张静态底图亮得多，0.72 才开始上色会让白字浮在亮绿上 —— 提前到 0.55。
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x59101C18),
                        0.28f to Color.Transparent,
                        0.55f to Color.Transparent,
                        0.78f to Color(0x8C0E1614),
                        1f to Color(0xF20E1614)
                    )
                )
        )

        // 顶部两颗胶囊：距离 + 定位状态（原型 .camt 是居中的，不是靠左）
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusPill(
                icon = Icons.Outlined.Navigation,
                text = state.distanceText,
                good = state.distanceMeters != null && !state.isFar
            )
            StatusPill(
                // 原型是准星（crosshair），不是盾牌
                icon = Icons.Outlined.CenterFocusWeak,
                text = if (state.distanceMeters == null) "定位中" else state.locationText,
                good = state.distanceMeters != null && !state.isFar
            )
        }

        // 取景框 + 构图辅助：原型 inset 100 / 26 / 190，四角与虚线框共用同一矩形
        GuidesLayer(modifier = Modifier.fillMaxSize().padding(start = 26.dp, end = 26.dp, top = 100.dp, bottom = 190.dp))

        if (!state.online) {
            BannerBar(
                text = "网络中断，暂时拿不到数据",
                tone = BannerTone.Error,
                icon = Icons.Outlined.CloudOff,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp, start = 12.dp, end = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }

        Text(
            text = if (state.isFar) "看起来还有点远，仍可拍摄，能否通过由服务端判定"
            else "把地标放在上半部分，人站在它前面",
            // 原型 .camnote：左右 20、12.5px 纯白、紧贴快门上方。
            // 原型的 bottom:118 是按它 72px 快门 + 更薄的底栏算的；我们快门区（含「轻点拍摄」标签）
            // 高出约 30dp，照抄 118 会让这行字压在快门上 —— 抬到 150 才有同样的留白。
            color = Color.White,
            style = AppText.tiny.copy(fontSize = 12.5.sp, lineHeight = 19.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 150.dp, start = 20.dp, end = 20.dp)
        )

        ShutterBar(
            enabled = state.online,
            label = when {
                !state.online -> "联网后可拍"
                state.isFar -> "仍可拍摄"
                else -> "轻点拍摄"
            },
            onShutter = onShutter,
            onFlip = { lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // 全面屏手势条会压住「轻点拍摄」，快门区整体上抬一个导航条高度
                .navigationBarsPadding()
        )
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier,
    lensFacing: Int,
    onReady: () -> Unit,
    onImageCaptureReady: (ImageCapture) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val preview = remember { Preview.Builder().build() }
    val imageCapture = remember(lensFacing) {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    LaunchedEffect(imageCapture) { onImageCaptureReady(imageCapture) }

    DisposableEffect(lensFacing, lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                    preview,
                    imageCapture
                )
                onReady()
            }
        }, executor)
        onDispose {
            runCatching { future.get().unbindAll() }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
        update = { preview.setSurfaceProvider(it.surfaceProvider) }
    )
}

/**
 * 取景辅助层，对应原型 .frame + .guides（两者用同一个 inset 矩形）：
 *  - 四角 26×26 直角括号
 *  - 三分网格（33% / 66%）
 *  - 「地标完整入镜」虚线框：左右各留 5%、上沿在 40%、高 56%、圆角 12
 *  - 标签贴在虚线框顶边上（原型 top:-24px），不是浮在画面中间
 *  - 人形辅助线贴在框底 6% 处
 */
@Composable
private fun GuidesLayer(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val boxH = maxHeight

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val corner = 26.dp.toPx()
            val stroke = 2.5.dp.toPx()
            val white = Color(0xD9FFFFFF)

            // 四角
            listOf(
                Offset(0f, 0f) to listOf(Offset(corner, 0f), Offset(0f, corner)),
                Offset(w, 0f) to listOf(Offset(w - corner, 0f), Offset(w, corner)),
                Offset(0f, h) to listOf(Offset(corner, h), Offset(0f, h - corner)),
                Offset(w, h) to listOf(Offset(w - corner, h), Offset(w, h - corner))
            ).forEach { (origin, ends) ->
                ends.forEach { end ->
                    drawLine(white, origin, end, strokeWidth = stroke, cap = StrokeCap.Round)
                }
            }

            // 三分网格
            val gridColor = Color(0x59FFFFFF)
            val g = 1.dp.toPx()
            drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), g)
            drawLine(gridColor, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), g)
            drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), g)
            drawLine(gridColor, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), g)

            // 地标虚线框
            val insetX = w * 0.05f
            drawRoundRect(
                color = Color(0x80FFFFFF),
                topLeft = Offset(insetX, h * 0.40f),
                size = Size(w - insetX * 2f, h * 0.56f),
                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )
            )
        }

        // 虚线框顶边上的标签
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = boxH * 0.40f - 24.dp)
                .background(Color(0xE0FFFFFF), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text("地标完整入镜", style = AppText.caption, color = Color(0xFF2E5145))
        }

        PersonGuide(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -(boxH * 0.06f))
                .width(44.dp)
                .height(92.dp)
        )
    }
}

@Composable
private fun PersonGuide(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 2.dp.toPx()
        val color = Color(0xB3FFFFFF)
        val dashed = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f)))
        val cx = size.width / 2f
        val headR = size.width * 0.19f

        drawCircle(color, radius = headR, center = Offset(cx, headR), style = dashed)
        drawLine(color, Offset(cx, headR * 2), Offset(cx, size.height * 0.62f), stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f)))
        drawLine(color, Offset(cx, size.height * 0.62f), Offset(cx - size.width * 0.3f, size.height), stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f)))
        drawLine(color, Offset(cx, size.height * 0.62f), Offset(cx + size.width * 0.3f, size.height), stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f)))
        drawLine(color, Offset(cx, size.height * 0.36f), Offset(cx - size.width * 0.42f, size.height * 0.56f), stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f)))
        drawLine(color, Offset(cx, size.height * 0.36f), Offset(cx + size.width * 0.42f, size.height * 0.56f), stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f)))
    }
}

@Composable
private fun StatusPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, good: Boolean) {
    Row(
        modifier = Modifier
            .background(if (good) Color(0xC810201B) else Color(0xC84A2E00), Radius.chip)
            .border(1.dp, if (good) Color(0x4DFFFFFF) else Color(0x66F5E6C8), Radius.chip)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (good) Color(0xFF9FE3C4) else Color(0xFFF0CE93),
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            style = AppText.caption,
            color = Color(0xFFEAF3EE)
        )
    }
}

@Composable
private fun ShutterBar(
    enabled: Boolean,
    label: String,
    onShutter: () -> Unit,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(44.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(Dimen.shutter)
                    .clip(CircleShape)
                    .border(3.dp, if (enabled) Color.White else Color(0x66FFFFFF), CircleShape)
                    .clickable(enabled = enabled, onClick = onShutter),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(if (enabled) Color.White else Color(0x66FFFFFF))
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(text = label, style = AppText.caption, color = Color(0xB3FFFFFF))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF))
                    .clickable(onClick = onFlip),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = "翻转镜头", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(text = "翻转", style = AppText.caption, color = Color(0xB3FFFFFF))
        }
    }
}

/* ---------------- 拍后预览 ---------------- */

@Composable
private fun ReviewShot(
    photoPath: String?,
    landmarkName: String,
    onSubmit: () -> Unit,
    onRetake: () -> Unit
) {
    val bitmap = remember(photoPath) {
        photoPath?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clip(Radius.cardLarge)
                .background(CameraSurface),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "刚拍的照片",
                    modifier = Modifier.fillMaxSize(),
                    // 拍后预览必须 contain，否则建筑两侧会被切掉
                    contentScale = ContentScale.Fit
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .background(Color(0x9E0A1210), Radius.chip)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Color(0xFF9FE3C4), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text("已拍摄 · 还没有提交", style = AppText.caption, color = Color.White)
            }
        }

        Text(
            text = "确认人物与$landmarkName 同框后再提交。\n现在还没有发出去，重拍不会留下记录。",
            style = AppText.small,
            color = Color(0xFFC9D6D1),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppButton(
                text = "重拍",
                onClick = onRetake,
                style = AppButtonStyle.Outline,
                icon = Icons.Outlined.Refresh,
                modifier = Modifier.weight(1f)
            )
            AppButton(
                text = "确认提交",
                onClick = onSubmit,
                icon = Icons.Outlined.CheckCircle,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/* ---------------- 提交中 / 失败 / 无权限 ---------------- */

@Composable
private fun SubmittingMask() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xD10B0F0E)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(18.dp))
            Text("正在提交打卡", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("照片和定位一起上传，别退出页面", style = AppText.small, color = Color(0xFFC9D6D1))
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.6f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF))
            ) {
                Box(Modifier.fillMaxWidth(0.72f).height(4.dp).clip(CircleShape).background(Color.White))
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "提交已经发起。就算关掉这一页，也会在「我的记录」里保留，\n用同一条请求恢复结果，不会重复提交。",
                style = AppText.caption,
                color = Color(0xFF8FA49D),
                textAlign = TextAlign.Center,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun SubmitErrorMask(onRetry: () -> Unit, onBack: () -> Unit) {
    FullMask(
        icon = Icons.Outlined.CloudOff,
        iconTint = ErrorRed,
        iconBg = Color(0xFF2A1512),
        title = "没提交成功",
        body = "这一次的提交没有成功。打卡需要联网完成，请检查网络后重试。",
        primaryText = "重试提交",
        primaryIcon = Icons.Outlined.Refresh,
        onPrimary = onRetry,
        secondaryText = "返回地标详情",
        onSecondary = onBack
    )
}

@Composable
private fun PermissionDeniedMask(onOpenSettings: () -> Unit, onBack: () -> Unit) {
    FullMask(
        icon = Icons.Outlined.PhotoCamera,
        iconTint = Color(0xFFC9D6D1),
        iconBg = Color(0xFF243430),
        title = "还没拿到相机权限",
        body = "拍照打卡需要相机。去设置里打开「相机」权限，回来就能直接拍。",
        primaryText = "去设置",
        primaryIcon = null,
        onPrimary = onOpenSettings,
        secondaryText = "返回详情",
        onSecondary = onBack
    )
}

@Composable
private fun FullMask(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    body: String,
    primaryText: String,
    primaryIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    onPrimary: () -> Unit,
    secondaryText: String,
    onSecondary: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(76.dp).background(iconBg, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = AppText.small,
            color = Color(0xFFC9D6D1),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(20.dp))
        AppButton(
            text = primaryText,
            onClick = onPrimary,
            icon = primaryIcon,
            modifier = Modifier.width(220.dp)
        )
        Spacer(Modifier.height(8.dp))
        AppButton(
            text = secondaryText,
            onClick = onSecondary,
            style = AppButtonStyle.Text,
            modifier = Modifier.width(220.dp)
        )
    }
}

/* ---------------- 拍照落盘 ---------------- */

private fun takePicture(
    context: android.content.Context,
    imageCapture: ImageCapture,
    onSaved: (String) -> Unit,
    onError: () -> Unit
) {
    val dir = File(context.cacheDir, "checkin").apply { mkdirs() }
    val file = File(dir, "checkin_${System.currentTimeMillis()}.jpg")
    val options = ImageCapture.OutputFileOptions.Builder(file).build()

    imageCapture.takePicture(
        options,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onSaved(file.absolutePath)
            }

            override fun onError(exception: ImageCaptureException) {
                onError()
            }
        }
    )
}
