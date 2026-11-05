package com.bitse.geotreasure.ui.pages.camera

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitse.geotreasure.core.LocationProvider
import com.bitse.geotreasure.core.NetworkMonitor
import com.bitse.geotreasure.domain.model.CampusModule
import com.bitse.geotreasure.domain.model.CheckInOutcome
import com.bitse.geotreasure.domain.model.FailReason
import com.bitse.geotreasure.domain.model.Landmark
import com.bitse.geotreasure.domain.repository.GeoTreasureRepository
import com.bitse.geotreasure.ui.navigation.OutcomeKeys
import com.bitse.geotreasure.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CameraPermission { Unknown, Granted, Denied }

/** P03 的四个阶段：取景 → 拍后预览 → 提交中 → 提交失败（可重试） */
enum class CameraPhase { Live, Review, Submitting, Error }

/**
 * 「距离偏远」的提示阈值。
 *
 * 只影响界面提示（胶囊变红、快门文案变「仍可拍摄」），**不参与任何判定**：
 * 快门不禁用，照片照常能提交，是否通过由服务端在提交后判定 —— 与原型
 * p03:far 的口径一致（「只给提醒、不禁用快门」）。
 */
const val FAR_HINT_METERS = 50f

data class CameraUiState(
    val landmark: Landmark?,
    val module: CampusModule?,
    val permission: CameraPermission = CameraPermission.Unknown,
    val phase: CameraPhase = CameraPhase.Live,
    /** 距目标多少米；null = 还没定到位 */
    val distanceMeters: Float? = null,
    val online: Boolean = true,
    /** 拍完落在 cache/checkin 下的绝对路径 */
    val photoPath: String? = null
) {
    /** 原型 p03:far —— 离得有点远，只提醒，不拦 */
    val isFar: Boolean get() = (distanceMeters ?: 0f) >= FAR_HINT_METERS

    val distanceText: String
        get() {
            val meters = distanceMeters ?: return "正在定位…"
            return "距目标约 ${meters.toInt()} m" + if (isFar) " · 可能偏远" else ""
        }

    val locationText: String get() = if (isFar) "定位信号一般" else "定位正常"
}

sealed interface CameraEvent {
    /** 有结局了，交给导航层去 result/{landmarkId}/{outcome} */
    data class Finished(val outcomeKey: String) : CameraEvent
}

@HiltViewModel
class CameraViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GeoTreasureRepository,
    private val locationProvider: LocationProvider,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    private val landmarkId: String = savedStateHandle[Routes.ARG_LANDMARK_ID] ?: ""
    private val landmark = repository.landmark(landmarkId)
    private val module = landmark?.let { repository.moduleOfLandmark(it.id) }

    private val _uiState = MutableStateFlow(
        CameraUiState(landmark = landmark, module = module)
    )
    val uiState: StateFlow<CameraUiState> = _uiState

    private val _events = MutableSharedFlow<CameraEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<CameraEvent> = _events

    private var locationJob: Job? = null

    init {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _uiState.update { it.copy(online = online) }
            }
        }
        startLocating()
    }

    private fun startLocating() {
        val target = landmark ?: return
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationProvider.distanceTo(target.latitude, target.longitude).collect { meters ->
                _uiState.update { it.copy(distanceMeters = meters) }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(permission = if (granted) CameraPermission.Granted else CameraPermission.Denied)
        }
        if (granted) startLocating()
    }

    /** 快门按下、照片已经落盘 */
    fun onCaptured(path: String) {
        _uiState.update { it.copy(photoPath = path, phase = CameraPhase.Review) }
    }

    fun retake() {
        _uiState.update { it.copy(phase = CameraPhase.Live, photoPath = null) }
    }

    /** 提交：一律等服务端/仓库返回结局，前端不判定半径与精度 */
    fun submit() {
        if (_uiState.value.phase == CameraPhase.Submitting) return
        val path = _uiState.value.photoPath
        _uiState.update { it.copy(phase = CameraPhase.Submitting) }

        viewModelScope.launch {
            if (!_uiState.value.online) {
                // 请求根本发不出去 —— 留在拍照页显示「没提交成功」，而不是跳到结果页
                _uiState.update { it.copy(phase = CameraPhase.Error) }
                return@launch
            }
            val outcome: CheckInOutcome = repository.submitCheckIn(landmarkId, path)
            if (outcome is CheckInOutcome.Failed && outcome.reason == FailReason.NETWORK) {
                _uiState.update { it.copy(phase = CameraPhase.Error) }
            } else {
                _events.emit(CameraEvent.Finished(OutcomeKeys.of(outcome)))
            }
        }
    }

    /** 提交失败后重试：沿用同一张照片，不重新拍 */
    fun retrySubmit() {
        _uiState.update { it.copy(phase = CameraPhase.Review) }
        submit()
    }

    override fun onCleared() {
        locationJob?.cancel()
        super.onCleared()
    }
}
