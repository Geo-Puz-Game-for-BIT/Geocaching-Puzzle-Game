package com.bitse.geotreasure.ui.pages.puzzle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitse.geotreasure.domain.model.CampusModule
import com.bitse.geotreasure.domain.repository.GeoTreasureRepository
import com.bitse.geotreasure.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** 一个片区的进度快照：已收哪几格、还差哪几个，都由它算，不另外存一份 */
data class ModuleSnapshot(
    val module: CampusModule,
    val ownedSlots: List<Int>
) {
    val ownedCount: Int get() = ownedSlots.size
    val total: Int get() = module.total
    val complete: Boolean get() = ownedCount >= total
    val missingNames: List<String> get() = module.landmarks.filter { it.slot !in ownedSlots }.map { it.name }
}

data class PuzzleOverviewUiState(
    val modules: List<ModuleSnapshot>,
    val shards: Int,
    val totalShards: Int,
    val completedModules: Int
) {
    /** 第一个没集齐的片区；全齐时退回第一个 */
    val nextModule: ModuleSnapshot
        get() = modules.firstOrNull { !it.complete } ?: modules.first()
}

@HiltViewModel
class PuzzleViewModel @Inject constructor(
    repository: GeoTreasureRepository
) : ViewModel() {

    val uiState: StateFlow<PuzzleOverviewUiState> = repository.progress
        .map { progress ->
            PuzzleOverviewUiState(
                modules = repository.modules.map { ModuleSnapshot(it, progress.ownedSlots(it.id)) },
                shards = progress.totalShards,
                totalShards = repository.modules.sumOf { it.total },
                completedModules = progress.completedModules
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PuzzleOverviewUiState(
                modules = repository.modules.map {
                    ModuleSnapshot(it, repository.progress.value.ownedSlots(it.id))
                },
                shards = repository.progress.value.totalShards,
                totalShards = repository.modules.sumOf { it.total },
                completedModules = repository.progress.value.completedModules
            )
        )
}

data class ModulePuzzleUiState(
    val snapshot: ModuleSnapshot?,
    val completedModules: Int,
    val totalModules: Int,
    /** 底部 Tab 角标用的是全局碎片数，不是本片区的 4 块 */
    val shards: Int = 0,
    val totalShards: Int = 12
)

@HiltViewModel
class ModulePuzzleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: GeoTreasureRepository
) : ViewModel() {

    private val moduleId: String = savedStateHandle[Routes.ARG_MODULE_ID] ?: ""

    val uiState: StateFlow<ModulePuzzleUiState> = repository.progress
        .map { progress ->
            val module = repository.module(moduleId)
            ModulePuzzleUiState(
                snapshot = module?.let { ModuleSnapshot(it, progress.ownedSlots(it.id)) },
                completedModules = progress.completedModules,
                totalModules = repository.modules.size,
                shards = progress.totalShards,
                totalShards = repository.modules.sumOf { it.total }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ModulePuzzleUiState(
                snapshot = repository.module(moduleId)?.let {
                    ModuleSnapshot(it, repository.progress.value.ownedSlots(it.id))
                },
                completedModules = repository.progress.value.completedModules,
                totalModules = repository.modules.size,
                shards = repository.progress.value.totalShards,
                totalShards = repository.modules.sumOf { it.total }
            )
        )
}
