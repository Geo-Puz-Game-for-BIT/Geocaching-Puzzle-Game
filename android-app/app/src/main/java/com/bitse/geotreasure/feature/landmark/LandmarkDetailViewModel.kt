package com.bitse.geotreasure.feature.landmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bitse.geotreasure.core.checkin.BlockReason
import com.bitse.geotreasure.core.checkin.CheckInGate
import com.bitse.geotreasure.core.checkin.CheckInGateResult
import com.bitse.geotreasure.core.common.AppResult
import com.bitse.geotreasure.core.location.LocationProvider
import com.bitse.geotreasure.core.model.Landmark
import com.bitse.geotreasure.data.repository.LandmarkRepository
import java.util.UUID
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
    val gate: CheckInGateResult? = null,
    val errorMessage: String? = null,
) {
    val canStartTask: Boolean get() = gate is CheckInGateResult.Ready

    val gateMessage: String? get() = (gate as? CheckInGateResult.Blocked)?.reason?.message

    val distanceMeters: Double?
        get() = when (gate) {
            is CheckInGateResult.Ready -> gate.distanceMeters
            is CheckInGateResult.Blocked -> gate.distanceMeters
            null -> null
        }
}

sealed interface LandmarkDetailAction {
    data object Retry : LandmarkDetailAction
    data object StartTask : LandmarkDetailAction
    data object RefreshLocation : LandmarkDetailAction
}

sealed interface LandmarkDetailEffect {
    data class NavigateToCamera(val landmarkId: String, val attemptId: String) : LandmarkDetailEffect
}

class LandmarkDetailViewModel(
    private val landmarkId: String,
    private val repository: LandmarkRepository,
    private val locationProvider: LocationProvider,
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
            LandmarkDetailAction.Retry -> {
                loadLandmark()
                refreshGate()
            }

            LandmarkDetailAction.RefreshLocation -> refreshGate()

            // C-N03：StartTask 不读旧 gate，而是点按时重新取位 + 重新门控
            LandmarkDetailAction.StartTask -> startTask()
        }
    }

    private fun startTask() {
        val landmark = mutableUiState.value.landmark ?: return
        viewModelScope.launch {
            val locationResult = locationProvider.getCurrentLocation()
            val gate = when (locationResult) {
                is AppResult.Success -> CheckInGate.evaluate(locationResult.data, landmark.point, landmark.checkInArea)
                is AppResult.Failure -> CheckInGateResult.Blocked(BlockReason.NO_LOCATION)
            }
            mutableUiState.update { it.copy(gate = gate) }
            if (gate is CheckInGateResult.Ready) {
                // 每次新尝试独立 ID（作幂等键），不沿用固定草稿 id
                val attemptId = "att_${UUID.randomUUID()}"
                effectChannel.trySend(
                    LandmarkDetailEffect.NavigateToCamera(landmarkId = landmarkId, attemptId = attemptId),
                )
            }
        }
    }

    private fun loadLandmark() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.getLandmark(landmarkId)) {
                is AppResult.Success -> {
                    mutableUiState.value = LandmarkDetailUiState(isLoading = false, landmark = result.data)
                    refreshGate()
                }

                is AppResult.Failure -> mutableUiState.value = LandmarkDetailUiState(
                    isLoading = false,
                    errorMessage = result.error.message,
                )
            }
        }
    }

    /** 单次取位判断门控；失败归为「等待定位」，由用户手动重新定位。 */
    private fun refreshGate() {
        val landmark = mutableUiState.value.landmark ?: return
        viewModelScope.launch {
            val gate = when (val result = locationProvider.getCurrentLocation()) {
                is AppResult.Success -> CheckInGate.evaluate(result.data, landmark.point, landmark.checkInArea)
                is AppResult.Failure -> CheckInGateResult.Blocked(BlockReason.NO_LOCATION)
            }
            mutableUiState.update { it.copy(gate = gate) }
        }
    }

    companion object {
        fun factory(
            landmarkId: String,
            repository: LandmarkRepository,
            locationProvider: LocationProvider,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LandmarkDetailViewModel(landmarkId, repository, locationProvider) as T
        }
    }
}
