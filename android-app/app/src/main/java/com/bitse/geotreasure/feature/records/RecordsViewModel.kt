package com.bitse.geotreasure.ui.pages.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitse.geotreasure.domain.model.CheckInPhoto
import com.bitse.geotreasure.domain.model.UserProgress
import com.bitse.geotreasure.domain.repository.GeoTreasureRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class RecordsUiState(
    val progress: UserProgress,
    val photos: List<CheckInPhoto>,
    val pendingCount: Int,
    val totalShards: Int,
    val moduleNames: Map<String, String>
) {
    val shards: Int get() = progress.totalShards
    val completedModules: Int get() = progress.completedModules
}

@HiltViewModel
class RecordsViewModel @Inject constructor(
    private val repository: GeoTreasureRepository
) : ViewModel() {

    val uiState: StateFlow<RecordsUiState> = combine(
        repository.progress,
        repository.photos,
        repository.pendingCount
    ) { progress, photos, pending ->
        RecordsUiState(
            progress = progress,
            photos = photos,
            pendingCount = pending,
            totalShards = repository.modules.sumOf { it.total },
            moduleNames = repository.modules.associate { it.id to it.name }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecordsUiState(
            progress = repository.progress.value,
            photos = repository.photos.value,
            pendingCount = repository.pendingCount.value,
            totalShards = repository.modules.sumOf { it.total },
            moduleNames = repository.modules.associate { it.id to it.name }
        )
    )

    fun deletePhoto(photoId: String) = repository.deletePhoto(photoId)
}
