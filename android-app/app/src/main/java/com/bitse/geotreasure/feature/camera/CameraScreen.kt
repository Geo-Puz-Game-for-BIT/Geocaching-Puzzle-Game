package com.bitse.geotreasure.feature.camera

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

data class CameraUiState(
    val landmarkId: String,
    val draftId: String,
    val hasDemoPhoto: Boolean = false,
)

sealed interface CameraAction {
    data object CaptureDemo : CameraAction
    data object Retake : CameraAction
    data object Confirm : CameraAction
}

sealed interface CameraEffect {
    data class NavigateToResult(val checkInId: String, val landmarkId: String) : CameraEffect
}

class CameraViewModel(landmarkId: String, draftId: String) : ViewModel() {
    private val mutableUiState = MutableStateFlow(CameraUiState(landmarkId, draftId))
    val uiState: StateFlow<CameraUiState> = mutableUiState.asStateFlow()
    private val effectChannel = Channel<CameraEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    fun onAction(action: CameraAction) {
        when (action) {
            CameraAction.CaptureDemo -> mutableUiState.value = mutableUiState.value.copy(hasDemoPhoto = true)
            CameraAction.Retake -> mutableUiState.value = mutableUiState.value.copy(hasDemoPhoto = false)
            CameraAction.Confirm -> effectChannel.trySend(
                CameraEffect.NavigateToResult("chk_demo01", mutableUiState.value.landmarkId),
            )
        }
    }

    companion object {
        fun factory(landmarkId: String, draftId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CameraViewModel(landmarkId, draftId) as T
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
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is CameraEffect.NavigateToResult) onResult(effect.checkInId, effect.landmarkId)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("拍照任务") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = MaterialTheme.shapes.small,
                color = if (state.hasDemoPhoto) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (state.hasDemoPhoto) Icons.Rounded.CheckCircle else Icons.Rounded.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            if (state.hasDemoPhoto) "演示照片已写入草稿" else "CameraX 将由 C 在此接入",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("草稿：${state.draftId}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (state.hasDemoPhoto) {
                Button(onClick = { viewModel.onAction(CameraAction.Retake) }, modifier = Modifier.fillMaxWidth()) {
                    Text("重拍")
                }
                Button(onClick = { viewModel.onAction(CameraAction.Confirm) }, modifier = Modifier.fillMaxWidth()) {
                    Text("确认并提交演示打卡")
                }
            } else {
                Button(onClick = { viewModel.onAction(CameraAction.CaptureDemo) }, modifier = Modifier.fillMaxWidth()) {
                    Text("模拟拍照")
                }
            }
        }
    }
}
