package com.bitse.geotreasure.feature.landmark

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitse.geotreasure.core.ui.compactDescription

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandmarkDetailScreen(
    viewModel: LandmarkDetailViewModel,
    onBack: () -> Unit,
    onOpenCamera: (String, String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LandmarkDetailEffect.NavigateToCamera -> onOpenCamera(effect.landmarkId, effect.draftId)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("地标详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            uiState.errorMessage != null -> ErrorContent(
                message = uiState.errorMessage.orEmpty(),
                onRetry = { viewModel.onAction(LandmarkDetailAction.Retry) },
                modifier = Modifier.padding(padding),
            )

            uiState.landmark != null -> {
                val landmark = requireNotNull(uiState.landmark)
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Icon(Icons.Rounded.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(14.dp))
                            Text(landmark.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "进入${landmark.checkInArea.compactDescription()}后完成拍照任务",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoBlock(Icons.Rounded.Flag, "难度", "${landmark.difficulty} / 5", Modifier.weight(1f))
                        InfoBlock(Icons.Rounded.Landscape, "地形", "${landmark.terrain} / 5", Modifier.weight(1f))
                    }
                    Column {
                        Text("任务提示", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(landmark.hint ?: "到达地标后查看现场提示。")
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { viewModel.onAction(LandmarkDetailAction.StartTask) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                        Text("  开始拍照任务")
                    }
                }
            }
        }
    }
}
@Composable
private fun InfoBlock(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, tonalElevation = 2.dp) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("重试") }
    }
}
