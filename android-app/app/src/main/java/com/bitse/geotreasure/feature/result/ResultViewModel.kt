package com.bitse.geotreasure.feature.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitse.geotreasure.domain.model.CampusModule
import com.bitse.geotreasure.domain.model.Landmark
import com.bitse.geotreasure.domain.repository.GeoTreasureRepository
import com.bitse.geotreasure.ui.navigation.OutcomeKeys
import com.bitse.geotreasure.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ResultUiState(
    val landmarkId: String,
    val landmark: Landmark?,
    val module: CampusModule?,
    val outcomeKey: String,
    val ownedSlots: List<Int>,
    val moduleOwnedCount: Int
) {
    val isSuccess: Boolean get() = outcomeKey == OutcomeKeys.SUCCESS
    val isPending: Boolean get() = outcomeKey == OutcomeKeys.PENDING
    val isConfirm: Boolean get() = outcomeKey == OutcomeKeys.CONFIRM
    val isFailure: Boolean get() = !isSuccess && !isPending && !isConfirm

    /** 成功时新归位的是哪一格（P04 的 2×2 里只高亮这一格） */
    val newSlot: Int? get() = landmark?.slot

    val moduleName: String get() = module?.name ?: ""
    val landmarkName: String get() = landmark?.name ?: ""

    /** 成功的副标题：「第 4 块碎片已归位，南校区点亮」 */
    val successLead: String
        get() {
            val done = moduleOwnedCount >= (module?.total ?: 4)
            return "服务端已确认：第 $moduleOwnedCount 块碎片已归位，$moduleName${if (done) "点亮" else "进度 $moduleOwnedCount/${module?.total ?: 4}"}。"
        }
}

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: GeoTreasureRepository
) : ViewModel() {

    private val landmarkId: String = savedStateHandle[Routes.ARG_LANDMARK_ID] ?: ""
    private val outcomeKey: String = savedStateHandle[Routes.ARG_OUTCOME] ?: OutcomeKeys.FAIL_NETWORK
    private val landmark = repository.landmark(landmarkId)
    private val module = landmark?.let { repository.moduleOfLandmark(it.id) }

    val uiState: StateFlow<ResultUiState> = repository.progress
        .map { progress ->
            ResultUiState(
                landmarkId = landmarkId,
                landmark = landmark,
                module = module,
                outcomeKey = outcomeKey,
                ownedSlots = progress.ownedSlots(module?.id ?: ""),
                moduleOwnedCount = progress.ownedCount(module?.id ?: "")
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ResultUiState(
                landmarkId = landmarkId,
                landmark = landmark,
                module = module,
                outcomeKey = outcomeKey,
                ownedSlots = repository.progress.value.ownedSlots(module?.id ?: ""),
                moduleOwnedCount = repository.progress.value.ownedCount(module?.id ?: "")
            )
        )
}
