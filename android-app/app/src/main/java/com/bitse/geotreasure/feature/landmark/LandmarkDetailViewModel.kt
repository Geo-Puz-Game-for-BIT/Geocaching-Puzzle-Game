package com.bitse.geotreasure.ui.pages.landmark

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitse.geotreasure.core.NetworkMonitor
import com.bitse.geotreasure.domain.model.CampusModule
import com.bitse.geotreasure.domain.model.Landmark
import com.bitse.geotreasure.domain.repository.GeoTreasureRepository
import com.bitse.geotreasure.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * P02 地标详情。
 *
 * landmarkId 只从导航参数来 —— 页面自己不带任何「当前是第几个地标」的推断，
 * 所以「点理教却显示疏桐」这类错位在结构上不会发生。
 */
data class LandmarkDetailUiState(
    val landmarkId: String,
    val landmark: Landmark?,
    val module: CampusModule?,
    val owned: Boolean,
    /** 该片区已收块数，用于「第 N/4 块」文案 */
    val moduleOwnedCount: Int,
    val online: Boolean,
    /** 首次进入时的短骨架屏，避免白屏闪一下 */
    val loading: Boolean
) {
    val taskNo: String get() = landmark?.taskId ?: ""
    val rewardIndex: Int get() = (moduleOwnedCount + 1).coerceAtMost(4)
    val notFound: Boolean get() = !loading && landmark == null
}

@HiltViewModel
class LandmarkDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GeoTreasureRepository,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    private val landmarkId: String = savedStateHandle[Routes.ARG_LANDMARK_ID] ?: ""
    private val loading = MutableStateFlow(true)

    init {
        // 演示数据是内存的，这里只是让骨架屏有个可感知的过渡；
        // 接 CloudBase 后换成真实的详情请求（含失败回退到 error 态）。
        viewModelScope.launch {
            kotlinx.coroutines.delay(260)
            loading.value = false
        }
    }

    val uiState: StateFlow<LandmarkDetailUiState> = combine(
        repository.progress,
        networkMonitor.isOnline,
        loading
    ) { progress, online, isLoading ->
        val landmark = repository.landmark(landmarkId)
        val module = landmark?.let { repository.moduleOfLandmark(it.id) }
        LandmarkDetailUiState(
            landmarkId = landmarkId,
            landmark = landmark,
            module = module,
            owned = landmark != null && progress.owns(landmark.moduleId, landmark.slot),
            moduleOwnedCount = module?.let { progress.ownedCount(it.id) } ?: 0,
            online = online,
            loading = isLoading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LandmarkDetailUiState(
            landmarkId = landmarkId,
            landmark = repository.landmark(landmarkId),
            module = repository.landmark(landmarkId)?.let { repository.moduleOfLandmark(it.id) },
            owned = false,
            moduleOwnedCount = 0,
            online = true,
            loading = true
        )
    )

    fun retryLoad() {
        loading.value = true
        viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            loading.value = false
        }
    }
}
