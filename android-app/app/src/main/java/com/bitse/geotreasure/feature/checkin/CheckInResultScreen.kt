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
import com.bitse.geotreasure.app.ExplorationProgressStore
import com.bitse.geotreasure.core.model.LiangxiangTreasureCatalog
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

data class CheckInResultUiState(val checkInId: String, val grantedFragmentTitle: String?)
sealed interface CheckInResultAction { data object OpenPuzzle : CheckInResultAction }
sealed interface CheckInResultEffect { data class NavigateToPuzzle(val puzzleId: String) : CheckInResultEffect }

class CheckInResultViewModel(
    checkInId: String,
    landmarkId: String,
    progressStore: ExplorationProgressStore,
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

    init {
        progressStore.markLandmarkCompleted(landmarkId)
    }

    fun onAction(action: CheckInResultAction) {
        if (action == CheckInResultAction.OpenPuzzle) {
            effectChannel.trySend(CheckInResultEffect.NavigateToPuzzle("pzl_campus_01"))
        }
    }

    companion object {
        fun factory(
            checkInId: String,
            landmarkId: String,
            progressStore: ExplorationProgressStore,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CheckInResultViewModel(checkInId, landmarkId, progressStore) as T
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
            modifier = Modifier.padding(padding).fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.Celebration, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Text("打卡成功", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(state.grantedFragmentTitle?.let { "点亮「$it」拼图碎片" } ?: "已记录地标进度")
            Text(state.checkInId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Button(
                onClick = { viewModel.onAction(CheckInResultAction.OpenPuzzle) },
                modifier = Modifier.padding(top = 24.dp).fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Extension, contentDescription = null)
                Text("  查看拼图")
            }
        }
    }
}
