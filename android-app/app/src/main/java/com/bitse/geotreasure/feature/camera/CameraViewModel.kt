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
 * 「到点才开拍」的门槛（演示值，采纳 UI 评审 UI-03 后从「纯提醒」改为「到点才可用快门」）。
 *
 * 注意分工：
 *  - 这个值只决定**快门能不能按**，是体验门槛；接后端后应由服务端配置下发，不写死在客户端；
 *  - 是否打卡**通过**仍由服务端在提交后判定 —— 与范围冻结口径一致，前端不写死判定半径/精度。
 */
const val ARRIVAL_METERS = 60f

data class CameraUiState(
    val landmark: Landmark?,
    val module: CampusModule?,
    val permission: CameraPermission = CameraPermission.Unknown,
    val phase: CameraPhase = CameraPhase.Live,
    /** 距目标多少米；null = 还没定到位 */
    val distanceMeters: Float? = null,
    val online: Boolean = true,
    /** 拍完落在 cache/checkin 下的绝对路径；重拍期间保留 —— 取消重拍时原照还在 */
    val photoPath: String? = null
) {
    /** UI-03「到点才开拍」：定位就绪且在门槛内才允许按快门 */
    val arrived: Boolean get() = (distanceMeters ?: Float.MAX_VALUE) < ARRIVAL_METERS

    val distanceText: String
        get() {
            val meters = distanceMeters ?: return "正在定位…"
            return "距目标约 ${meters.toInt()} m"
        }

    val locationText: String get() = if (arrived) "定位正常" else "定位中"
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

    /**
     * 重拍：回到取景，但**保留已拍的那张**（UI-06「取消重拍保留原照」）。
     * 用户在取景态按返回 = 取消重拍 → cancelRetake() 回到预览，原照还在。
     * 只有真正按下快门拍出新照片，原照才会被替换。
     */
    fun retake() {
        _uiState.update { it.copy(phase = CameraPhase.Live) }
    }

    /** 取消重拍：原照保留，回到拍后预览继续提交 */
    fun cancelRetake() {
        if (_uiState.value.photoPath != null) {
            _uiState.update { it.copy(phase = CameraPhase.Review) }
        }
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
