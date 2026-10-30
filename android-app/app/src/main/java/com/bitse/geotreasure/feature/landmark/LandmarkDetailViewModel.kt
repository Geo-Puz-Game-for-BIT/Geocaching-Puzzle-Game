package com.bitse.geotreasure.feature.landmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bitse.geotreasure.core.common.AppResult
import com.bitse.geotreasure.core.model.Landmark
import com.bitse.geotreasure.data.repository.LandmarkRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LandmarkDetailUiState(
    val isLoading: Boolean = true,
    val landmark: Landmark? = null,
    val errorMessage: String? = null,
)

sealed interface LandmarkDetailAction {
    data object Retry : LandmarkDetailAction
    data object StartTask : LandmarkDetailAction
}

sealed interface LandmarkDetailEffect {
    data class NavigateToCamera(val landmarkId: String, val draftId: String) : LandmarkDetailEffect
}

class LandmarkDetailViewModel(
    private val landmarkId: String,
    private val repository: LandmarkRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(LandmarkDetailUiState())
    val uiState: StateFlow<LandmarkDetailUiState> = mutableUiState.asStateFlow()

    private val effectChannel = Channel<LandmarkDetailEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        loadLandmark()
    }

    fun onAction(action: LandmarkDetailAction) {
        when (action) {
            LandmarkDetailAction.Retry -> loadLandmark()
            LandmarkDetailAction.StartTask -> effectChannel.trySend(
                LandmarkDetailEffect.NavigateToCamera(
                    landmarkId = landmarkId,
                    draftId = "dft_demo_${landmarkId.removePrefix("lmk_")}",
                ),
            )
        }
    }

    private fun loadLandmark() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.getLandmark(landmarkId)) {
                is AppResult.Success -> mutableUiState.value = LandmarkDetailUiState(
                    isLoading = false,
                    landmark = result.data,
                )

                is AppResult.Failure -> mutableUiState.value = LandmarkDetailUiState(
                    isLoading = false,
                    errorMessage = result.error.message,
                )
            }
        }
    }

    companion object {
        fun factory(
            landmarkId: String,
            repository: LandmarkRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LandmarkDetailViewModel(landmarkId, repository) as T
        }
    }
}
