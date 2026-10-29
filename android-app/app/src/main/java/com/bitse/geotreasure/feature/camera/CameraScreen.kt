package com.bitse.geotreasure.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
import com.bitse.geotreasure.core.camera.CameraXManager
import com.bitse.geotreasure.core.model.CheckInDraft
import com.bitse.geotreasure.core.model.LocalPhotoRef
import com.bitse.geotreasure.core.model.UploadState
import com.bitse.geotreasure.data.repository.CheckInDraftStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import java.io.File

data class CameraUiState(
    val landmarkId: String,
    val draftId: String,
    val hasCameraPermission: Boolean = false,
    val cameraBound: Boolean = false,
    val photoPath: String? = null,
    val isCapturing: Boolean = false,
    val restoredDraft: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface CameraAction {
    data object Capture : CameraAction
    data object Retake : CameraAction
    data object Confirm : CameraAction
}

sealed interface CameraEffect {
    data class NavigateToResult(val checkInId: String, val landmarkId: String) : CameraEffect
}

class CameraViewModel(
    private val landmarkId: String,
    private val draftId: String,
    private val draftStore: CheckInDraftStore,
    private val cameraManager: CameraXManager,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(CameraUiState(landmarkId, draftId))
    val uiState: StateFlow<CameraUiState> = mutableUiState.asStateFlow()
    private val effectChannel = Channel<CameraEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private var draftCreatedAt: Instant? = null

    init {
        restoreDraft()
    }

    private fun restoreDraft() {
        viewModelScope.launch {
            val saved = draftStore.load()
            val photoPath = saved?.localPhoto?.absolutePath
            if (saved != null && saved.id == draftId && photoPath != null && File(photoPath).exists()) {
                draftCreatedAt = saved.createdAt
                mutableUiState.update { it.copy(photoPath = photoPath, restoredDraft = true) }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        mutableUiState.update { it.copy(hasCameraPermission = granted) }
    }

    fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        if (mutableUiState.value.cameraBound) return
        mutableUiState.update { it.copy(cameraBound = true) }
        cameraManager.bind(lifecycleOwner, previewView) { e ->
            mutableUiState.update { it.copy(errorMessage = e.message) }
        }
    }

    fun onAction(action: CameraAction) {
        when (action) {
            CameraAction.Capture -> capture()
            CameraAction.Retake -> retake()
            CameraAction.Confirm -> confirm()
        }
    }

    private fun capture() {
        if (mutableUiState.value.isCapturing) return
        mutableUiState.update { it.copy(isCapturing = true) }
        cameraManager.takePhoto(
            onCaptured = { file ->
                mutableUiState.update { it.copy(photoPath = file.absolutePath, isCapturing = false) }
                persistDraft(
                    localPhoto = LocalPhotoRef(
                        absolutePath = file.absolutePath,
                        mimeType = "image/jpeg",
                        sizeBytes = file.length(),
                    ),
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
        mutableUiState.update { it.copy(photoPath = null, restoredDraft = false) }
        persistDraft(localPhoto = null, uploadState = UploadState.EMPTY)
    }

    private fun confirm() {
        val photoPath = mutableUiState.value.photoPath ?: return
        persistDraft(
            localPhoto = LocalPhotoRef(photoPath, "image/jpeg", File(photoPath).length()),
            uploadState = UploadState.CONFIRMED,
        )
        effectChannel.trySend(
            CameraEffect.NavigateToResult(checkInId = draftId, landmarkId = landmarkId),
        )
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
                    uploadState = uploadState,
                    createdAt = createdAt,
                    updatedAt = now,
                ),
            )
        }
    }

    override fun onCleared() {
        cameraManager.unbind()
    }

    companion object {
        fun factory(
            landmarkId: String,
            draftId: String,
            draftStore: CheckInDraftStore,
            cameraManager: CameraXManager,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CameraViewModel(landmarkId, draftId, draftStore, cameraManager) as T
        }
    }
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
        BitmapFactory.decodeFile(photoPath)?.asImageBitmap()
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
                enabled = !state.isCapturing,
            ) {
                Text("重拍")
            }
            Button(
                onClick = { onAction(CameraAction.Confirm) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isCapturing,
            ) {
                Text("确认并提交打卡")
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
