package com.bitse.geotreasure.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bitse.geotreasure.app.ExplorationProgressStore
import com.bitse.geotreasure.core.camera.CameraXManager
import com.bitse.geotreasure.core.camera.FaceCheckResult
import com.bitse.geotreasure.core.camera.FaceQualityChecker
import com.bitse.geotreasure.core.camera.PhotoCompressor
import com.bitse.geotreasure.core.common.AppResult
import com.bitse.geotreasure.core.location.LocationProvider
import com.bitse.geotreasure.core.model.CheckInDraft
import com.bitse.geotreasure.core.model.LocalPhotoRef
import com.bitse.geotreasure.core.model.UploadState
import com.bitse.geotreasure.data.remote.CheckInApiException
import com.bitse.geotreasure.data.repository.CheckInDraftStore
import com.bitse.geotreasure.data.repository.CheckInRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import java.io.File

data class CameraUiState(
    val landmarkId: String,
    val draftId: String,
    val hasCameraPermission: Boolean = false,
    val cameraBound: Boolean = false,
    val photoPath: String? = null,
    val isCapturing: Boolean = false,
    val isProcessing: Boolean = false,
    val uploadState: UploadState = UploadState.EMPTY,
    val restoredDraft: Boolean = false,
    val errorMessage: String? = null,
    val faceCheckMessage: String? = null,
)

sealed interface CameraAction {
    data object Capture : CameraAction
    data object Retake : CameraAction
    data object Confirm : CameraAction // 兼作「重试」入口（幂等提交）
}

sealed interface CameraEffect {
    data class NavigateToResult(val checkInId: String, val landmarkId: String) : CameraEffect
}

class CameraViewModel(
    private val landmarkId: String,
    private val draftId: String,
    private val draftStore: CheckInDraftStore,
    private val cameraManager: CameraXManager,
    private val checkInRepository: CheckInRepository,
    private val progressStore: ExplorationProgressStore,
    private val locationProvider: LocationProvider,
    private val faceQualityChecker: FaceQualityChecker,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(CameraUiState(landmarkId, draftId))
    val uiState: StateFlow<CameraUiState> = mutableUiState.asStateFlow()
    private val effectChannel = Channel<CameraEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private var draftCreatedAt: Instant? = null
    private var attachedPreviewView: PreviewView? = null
    private var uploadedPhotoId: String? = null

    init {
        restoreDraft()
    }

    private fun restoreDraft() {
        viewModelScope.launch {
            val saved = draftStore.load()
            if (saved != null && saved.id == draftId) {
                draftCreatedAt = saved.createdAt
                uploadedPhotoId = saved.uploadedPhotoId
                val photoPath = saved.localPhoto?.absolutePath
                if (photoPath != null && File(photoPath).exists()) {
                    mutableUiState.update {
                        it.copy(
                            photoPath = photoPath,
                            uploadState = saved.uploadState,
                            restoredDraft = true,
                            errorMessage = if (saved.uploadState == UploadState.FAILED) {
                                "上次提交失败，请重试"
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        mutableUiState.update { it.copy(hasCameraPermission = granted) }
    }

    fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        if (mutableUiState.value.cameraBound) return
        mutableUiState.update { it.copy(cameraBound = true) }
        attachedPreviewView = previewView
        cameraManager.bind(lifecycleOwner, previewView) { e ->
            mutableUiState.update { it.copy(errorMessage = e.message) }
        }
    }

    fun onAction(action: CameraAction) {
        when (action) {
            CameraAction.Capture -> capture()
            CameraAction.Retake -> retake()
            CameraAction.Confirm -> submitFlow()
        }
    }

    private fun capture() {
        if (mutableUiState.value.isCapturing) return
        mutableUiState.update { it.copy(isCapturing = true, errorMessage = null, faceCheckMessage = null) }
        val rotation = attachedPreviewView?.display?.rotation ?: Surface.ROTATION_0
        cameraManager.takePhoto(
            rotation = rotation,
            onCaptured = { file ->
                uploadedPhotoId = null
                mutableUiState.update {
                    it.copy(photoPath = file.absolutePath, isCapturing = false, uploadState = UploadState.CAPTURED)
                }
                persistDraft(
                    localPhoto = LocalPhotoRef(file.absolutePath, "image/jpeg", file.length()),
                    uploadState = UploadState.CAPTURED,
                )
            },
            onError = { e ->
                mutableUiState.update { it.copy(isCapturing = false, errorMessage = e.message) }
            },
        )
    }

    private fun retake() {
        CameraXManager.deletePhoto(mutableUiState.value.photoPath)
        uploadedPhotoId = null
        mutableUiState.update {
            it.copy(
                photoPath = null,
                restoredDraft = false,
                uploadState = UploadState.EMPTY,
                errorMessage = null,
                faceCheckMessage = null,
            )
        }
        persistDraft(localPhoto = null, uploadState = UploadState.EMPTY)
    }

    /** 拍照 → 本地初检 → 上传照片 → 领 challenge → POST /checkins → 等 receipt。失败可重试。 */
    private fun submitFlow() {
        val state = mutableUiState.value
        if (state.isProcessing || state.uploadState == UploadState.CONFIRMED) return
        val photoPath = state.photoPath ?: return

        mutableUiState.update { it.copy(isProcessing = true, errorMessage = null, faceCheckMessage = null) }
        persistDraft(
            localPhoto = LocalPhotoRef(photoPath, "image/jpeg", File(photoPath).length()),
            uploadState = UploadState.UPLOADING,
        )

        viewModelScope.launch(Dispatchers.IO) {
            val processed = PhotoCompressor.process(File(photoPath))

            val bitmap = PhotoCompressor.decodeUpright(processed.file.absolutePath)
            if (bitmap == null) {
                onFaceCheckFailed("照片读取失败，请重拍")
                return@launch
            }
            val faceResult = faceQualityChecker.check(bitmap)
            if (faceResult is FaceCheckResult.Failure) {
                onFaceCheckFailed(faceResult.reason.message)
                return@launch
            }

            val location = when (val loc = locationProvider.getCurrentLocation()) {
                is AppResult.Success -> loc.data
                is AppResult.Failure -> {
                    onSubmitFailed(IllegalStateException(loc.error.message ?: "定位失败，请重试"))
                    return@launch
                }
            }

            var photoId = uploadedPhotoId
            if (photoId == null) {
                photoId = try {
                    checkInRepository.uploadPhoto(processed.file).photoId.also {
                        uploadedPhotoId = it
                        persistDraft(
                            localPhoto = LocalPhotoRef(
                                processed.file.absolutePath,
                                "image/jpeg",
                                processed.file.length(),
                            ),
                            uploadState = UploadState.UPLOADED,
                        )
                    }
                } catch (e: Throwable) {
                    onSubmitFailed(e)
                    return@launch
                }
            }

            try {
                val receipt = checkInRepository.submitCheckIn(
                    landmarkId = landmarkId,
                    photoId = photoId,
                    location = location,
                    idempotencyKey = draftId,
                )
                withContext(Dispatchers.Main) { onCheckInSuccess(receipt.checkInId) }
            } catch (e: Throwable) {
                onSubmitFailed(e)
            }
        }
    }

    private fun onFaceCheckFailed(message: String) {
        viewModelScope.launch {
            val path = mutableUiState.value.photoPath
            mutableUiState.update {
                it.copy(isProcessing = false, uploadState = UploadState.CAPTURED, faceCheckMessage = message)
            }
            persistDraft(
                localPhoto = path?.let { LocalPhotoRef(it, "image/jpeg", File(it).length()) },
                uploadState = UploadState.CAPTURED,
            )
        }
    }

    private fun onSubmitFailed(throwable: Throwable) {
        viewModelScope.launch {
            val path = mutableUiState.value.photoPath
            mutableUiState.update {
                it.copy(
                    isProcessing = false,
                    uploadState = UploadState.FAILED,
                    errorMessage = checkInFailureMessage(throwable),
                )
            }
            persistDraft(
                localPhoto = path?.let { LocalPhotoRef(it, "image/jpeg", File(it).length()) },
                uploadState = UploadState.FAILED,
            )
        }
    }

    private fun onCheckInSuccess(checkInId: String) {
        // 只有服务端确认成功后才发碎片 + 清草稿，不因本地拍照成功提前发奖
        progressStore.markLandmarkCompleted(landmarkId)
        viewModelScope.launch { draftStore.clear() }
        mutableUiState.update { it.copy(isProcessing = false, uploadState = UploadState.CONFIRMED) }
        effectChannel.trySend(CameraEffect.NavigateToResult(checkInId = checkInId, landmarkId = landmarkId))
    }

    private fun persistDraft(localPhoto: LocalPhotoRef?, uploadState: UploadState) {
        viewModelScope.launch {
            val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            val createdAt = draftCreatedAt ?: now.also { draftCreatedAt = it }
            draftStore.save(
                CheckInDraft(
                    id = draftId,
                    landmarkId = landmarkId,
                    localPhoto = localPhoto,
                    uploadedPhotoId = uploadedPhotoId,
                    uploadState = uploadState,
                    createdAt = createdAt,
                    updatedAt = now,
                ),
            )
        }
    }

    override fun onCleared() {
        cameraManager.unbind()
        faceQualityChecker.close()
    }

    companion object {
        fun factory(
            landmarkId: String,
            draftId: String,
            draftStore: CheckInDraftStore,
            cameraManager: CameraXManager,
            checkInRepository: CheckInRepository,
            progressStore: ExplorationProgressStore,
            locationProvider: LocationProvider,
            faceQualityChecker: FaceQualityChecker,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CameraViewModel(
                landmarkId = landmarkId,
                draftId = draftId,
                draftStore = draftStore,
                cameraManager = cameraManager,
                checkInRepository = checkInRepository,
                progressStore = progressStore,
                locationProvider = locationProvider,
                faceQualityChecker = faceQualityChecker,
            ) as T
        }
    }
}

/** 把服务端/网络异常映射为可读提示（可 JVM 单测）。 */
internal fun checkInFailureMessage(throwable: Throwable): String = when (throwable) {
    is CheckInApiException -> when (throwable.code) {
        "OUT_OF_RANGE" -> "已离开打卡范围，请回到地标附近重试"
        "LOCATION_ACCURACY_TOO_LOW" -> "定位精度不足，请到开阔处重试"
        "CHALLENGE_EXPIRED" -> "挑战码已过期，请重新提交"
        "NOT_CONFIGURED" -> throwable.message ?: "打卡服务端未配置"
        else -> throwable.message ?: "打卡失败，请重试"
    }

    else -> throwable.message ?: "打卡失败，请重试"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onBack: () -> Unit,
    onResult: (String, String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is CameraEffect.NavigateToResult) onResult(effect.checkInId, effect.landmarkId)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onPermissionResult(granted) }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionResult(granted)
        if (!granted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("拍照打卡") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val photoPath = state.photoPath
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                when {
                    !state.hasCameraPermission -> PermissionHint(
                        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    )

                    photoPath != null -> PhotoPreview(photoPath)
                    else -> LivePreview(viewModel, lifecycleOwner)
                }
            }

            state.faceCheckMessage?.let { msg ->
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            state.errorMessage?.let { msg ->
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            ActionButtons(state = state, onAction = viewModel::onAction)
        }
    }
}

@Composable
private fun LivePreview(viewModel: CameraViewModel, lifecycleOwner: LifecycleOwner) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(previewView) {
        viewModel.bindCamera(lifecycleOwner, previewView)
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun PhotoPreview(photoPath: String) {
    val bitmap = remember(photoPath) {
        PhotoCompressor.decodeUpright(photoPath)?.asImageBitmap()
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "已拍照片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("照片已保存", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PermissionHint(onRequestPermission: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.NoPhotography, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            Text("需要相机权限才能拍照", fontWeight = FontWeight.SemiBold)
            Button(onClick = onRequestPermission) {
                Text("授予权限")
            }
        }
    }
}

@Composable
private fun ActionButtons(state: CameraUiState, onAction: (CameraAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.photoPath != null) {
            OutlinedButton(
                onClick = { onAction(CameraAction.Retake) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isCapturing && !state.isProcessing,
            ) {
                Text("重拍")
            }
            Button(
                onClick = { onAction(CameraAction.Confirm) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isCapturing && !state.isProcessing,
            ) {
                Text(
                    when {
                        state.isProcessing -> "正在提交打卡…"
                        state.uploadState == UploadState.FAILED -> "重试"
                        else -> "确认并提交打卡"
                    },
                )
            }
        } else {
            Button(
                onClick = { onAction(CameraAction.Capture) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.hasCameraPermission && !state.isCapturing,
            ) {
                if (state.isCapturing) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                } else {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                }
                Text(if (state.isCapturing) "  正在拍照…" else "  拍照")
            }
        }
    }
}
