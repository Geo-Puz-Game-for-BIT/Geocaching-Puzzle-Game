package com.bitse.geotreasure.feature.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.bitse.geotreasure.core.model.LiangxiangTreasureCatalog
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.bitse.geotreasure.core.ui.components.AppButton

data class CheckInResultUiState(val checkInId: String, val grantedFragmentTitle: String?)
sealed interface CheckInResultAction { data object OpenPuzzle : CheckInResultAction }
sealed interface CheckInResultEffect { data class NavigateToPuzzle(val puzzleId: String) : CheckInResultEffect }

/**
 * 打卡结果页：仅展示服务端已确认的成功结果。
 * 碎片发放已由 CameraViewModel 在收到 CheckInReceipt 后调用 markLandmarkCompleted 完成，
 * 本页不再重复标记，避免「本地拍照成功即发奖」。
 */
class CheckInResultViewModel(
    checkInId: String,
    landmarkId: String,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        CheckInResultUiState(
            checkInId,
            LiangxiangTreasureCatalog.landmarks.firstOrNull { it.id == landmarkId }?.name,
        ),
    )
    val uiState: StateFlow<CheckInResultUiState> = mutableUiState.asStateFlow()
    private val effectChannel = Channel<CheckInResultEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    fun onAction(action: CheckInResultAction) {
        if (action == CheckInResultAction.OpenPuzzle) {
            effectChannel.trySend(CheckInResultEffect.NavigateToPuzzle("pzl_campus_01"))
        }
    }

    companion object {
        fun factory(
            checkInId: String,
            landmarkId: String,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CheckInResultViewModel(checkInId, landmarkId) as T
        }
    }
}
@Composable
fun CheckInResultScreen(
    viewModel: CheckInResultViewModel,
    onOpenPuzzle: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            if (effect is CheckInResultEffect.NavigateToPuzzle) onOpenPuzzle(effect.puzzleId)
        }
    }
    Scaffold { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Celebration,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("打卡成功", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            // 碎片到账卡：仅服务端确认（非空）才显示碎片；未确认态不写成成功
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = state.grantedFragmentTitle?.let { "点亮「$it」拼图碎片" } ?: "已记录地标进度",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                state.checkInId,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            AppButton(
                text = "查看拼图",
                icon = Icons.Rounded.Extension,
                onClick = { viewModel.onAction(CheckInResultAction.OpenPuzzle) },
                modifier = Modifier.padding(top = 24.dp).fillMaxWidth(),
            )
        }
    }
}
