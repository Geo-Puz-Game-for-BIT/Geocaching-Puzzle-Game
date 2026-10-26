package com.bitse.geotreasure.feature.puzzle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitse.geotreasure.app.ExplorationProgressStore
import com.bitse.geotreasure.core.model.CampusZone
import com.bitse.geotreasure.core.model.LiangxiangTreasureCatalog
import com.bitse.geotreasure.core.model.PuzzleFragmentDefinition
import com.bitse.geotreasure.core.model.PuzzleRevealMode
import com.bitse.geotreasure.core.model.PuzzleSectionDefinition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PuzzleUiState(
    val puzzleId: String,
    val revealMode: PuzzleRevealMode = LiangxiangTreasureCatalog.puzzle.defaultRevealMode,
    val ownedFragmentIds: Set<String> = DEMO_OWNED_FRAGMENT_IDS,
)

private val DEMO_OWNED_FRAGMENT_IDS = setOf(
    "fragment_north_lake",
    "fragment_north_library",
    "fragment_east_wencui",
)

internal fun ownedFragmentIdsFor(completedLandmarkIds: Set<String>): Set<String> =
    DEMO_OWNED_FRAGMENT_IDS + LiangxiangTreasureCatalog.landmarks
        .filter { it.id in completedLandmarkIds }
        .map { it.fragmentId }

sealed interface PuzzleAction {
    data class ChangeRevealMode(val revealMode: PuzzleRevealMode) : PuzzleAction
    data class SelectFragment(val fragmentId: String) : PuzzleAction
}

sealed interface PuzzleEffect

class PuzzleViewModel(puzzleId: String, progressStore: ExplorationProgressStore) : ViewModel() {
    private val mutableUiState = MutableStateFlow(PuzzleUiState(puzzleId))
    val uiState: StateFlow<PuzzleUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            progressStore.completedLandmarkIds.collect { completedIds ->
                mutableUiState.update { it.copy(ownedFragmentIds = ownedFragmentIdsFor(completedIds)) }
            }
        }
    }

    fun onAction(action: PuzzleAction) {
        when (action) {
            is PuzzleAction.ChangeRevealMode -> mutableUiState.update {
                it.copy(revealMode = action.revealMode)
            }

            is PuzzleAction.SelectFragment -> {
                if (action.fragmentId !in mutableUiState.value.ownedFragmentIds) return
            }
        }
    }

    companion object {
        fun factory(puzzleId: String, progressStore: ExplorationProgressStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PuzzleViewModel(puzzleId, progressStore) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleScreen(viewModel: PuzzleViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val puzzle = LiangxiangTreasureCatalog.puzzle
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(puzzle.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                PuzzleProgressCard(
                    ownedCount = state.ownedFragmentIds.size,
                    totalCount = puzzle.fragmentCount,
                )
            }
            item {
                RevealModeSelector(
                    selected = state.revealMode,
                    onSelected = { viewModel.onAction(PuzzleAction.ChangeRevealMode(it)) },
                )
            }
            items(puzzle.sections, key = { it.id }) { section ->
                CampusSection(
                    section = section,
                    revealMode = state.revealMode,
                    ownedFragmentIds = state.ownedFragmentIds,
                    onFragmentSelected = { viewModel.onAction(PuzzleAction.SelectFragment(it)) },
                )
            }
            item {
                Text(
                    text = "当前 12 个坐标用于开发演示。正式开放打卡前，需要逐点完成实地测量、拍照许可和安全检查。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PuzzleProgressCard(ownedCount: Int, totalCount: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("校园探索进度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("到达地标后自动点亮对应区域", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = "$ownedCount / $totalCount",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RevealModeSelector(
    selected: PuzzleRevealMode,
    onSelected: (PuzzleRevealMode) -> Unit,
) {
    val modes = listOf(PuzzleRevealMode.FOG_OF_WAR, PuzzleRevealMode.GRID)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                icon = {
                    Icon(
                        imageVector = if (mode == PuzzleRevealMode.FOG_OF_WAR) Icons.Rounded.VisibilityOff else Icons.Rounded.GridView,
                        contentDescription = null,
                    )
                },
                label = { Text(if (mode == PuzzleRevealMode.FOG_OF_WAR) "探索迷雾" else "规则分块") },
            )
        }
    }
}

@Composable
private fun CampusSection(
    section: PuzzleSectionDefinition,
    revealMode: PuzzleRevealMode,
    ownedFragmentIds: Set<String>,
    onFragmentSelected: (String) -> Unit,
) {
    val ownedCount = section.fragments.count { it.id in ownedFragmentIds }
    Card {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Map, contentDescription = null, tint = zoneColor(section.campusZone))
                    Text(section.campusZone.displayName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text("$ownedCount / ${section.fragments.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            section.fragments.chunked(section.columns).forEach { rowFragments ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowFragments.forEach { fragment ->
                        FragmentTile(
                            fragment = fragment,
                            owned = fragment.id in ownedFragmentIds,
                            revealMode = revealMode,
                            zone = section.campusZone,
                            modifier = Modifier.weight(1f),
                            onSelected = { onFragmentSelected(fragment.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FragmentTile(
    fragment: PuzzleFragmentDefinition,
    owned: Boolean,
    revealMode: PuzzleRevealMode,
    zone: CampusZone,
    modifier: Modifier,
    onSelected: () -> Unit,
) {
    val fogged = revealMode == PuzzleRevealMode.FOG_OF_WAR && !owned
    val backgroundColor = when {
        fogged -> MaterialTheme.colorScheme.inverseSurface
        owned -> zoneColor(zone).copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (fogged) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .aspectRatio(1.45f)
            .background(backgroundColor, MaterialTheme.shapes.small)
            .border(1.dp, zoneColor(zone).copy(alpha = if (owned) 0.8f else 0.25f), MaterialTheme.shapes.small)
            .clickable(enabled = owned, onClick = onSelected),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = when {
                    owned -> Icons.Rounded.CheckCircle
                    fogged -> Icons.Rounded.VisibilityOff
                    else -> Icons.Rounded.Lock
                },
                contentDescription = null,
                tint = if (owned) zoneColor(zone) else contentColor,
            )
            Text(
                text = if (fogged) "未探索" else fragment.title,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                textAlign = TextAlign.Center,
                fontWeight = if (owned) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

private fun CampusZone.displayName(): String = when (this) {
    CampusZone.NORTH -> "北校区"
    CampusZone.EAST -> "东校区"
    CampusZone.SOUTH -> "南校区"
}

@Composable
private fun zoneColor(zone: CampusZone): Color = when (zone) {
    CampusZone.NORTH -> MaterialTheme.colorScheme.primary
    CampusZone.EAST -> MaterialTheme.colorScheme.tertiary
    CampusZone.SOUTH -> MaterialTheme.colorScheme.secondary
}
