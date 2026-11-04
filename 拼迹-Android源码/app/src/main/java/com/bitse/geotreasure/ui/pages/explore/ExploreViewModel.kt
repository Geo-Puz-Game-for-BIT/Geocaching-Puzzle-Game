package com.bitse.geotreasure.ui.pages.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitse.geotreasure.core.NetworkMonitor
import com.bitse.geotreasure.domain.model.CampusModule
import com.bitse.geotreasure.domain.model.Landmark
import com.bitse.geotreasure.domain.model.LocationState
import com.bitse.geotreasure.domain.model.ModuleIds
import com.bitse.geotreasure.domain.model.UserProgress
import com.bitse.geotreasure.domain.repository.GeoTreasureRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExploreUiState(
    val modules: List<CampusModule>,
    val selectedModuleId: String,
    val module: CampusModule,
    val ownedSlots: List<Int>,
    /** 当前片区的下一个待办地标 = 任务卡与「？」水滴指向的对象 */
    val target: Landmark,
    val online: Boolean,
    val location: LocationState,
    /** 全局已收碎片数：底部 Tab 角标用它，和 P05 / P06 同一个来源 */
    val shards: Int = 0,
    val totalShards: Int = 12,
    /** 原型 p01:loading —— 地图与任务卡分块占位，不白屏 */
    val loading: Boolean = false,
    /** 原型 p01:error —— 连地标数据都没拿到，保留底图配色 + 重新加载 */
    val loadFailed: Boolean = false
) {
    fun owns(slot: Int): Boolean = slot in ownedSlots

    val distanceText: String
        get() = if (location == LocationState.PERMISSION_DENIED || location == LocationState.LOCATING || loadFailed) {
            "距离待获取"
        } else {
            "约 " + target.distanceLabel
        }

    /** 原型：loading / error / denied 三种情况都不画水滴（拿不到位置就没有可信标记） */
    val showPins: Boolean
        get() = !loading && !loadFailed && location != LocationState.PERMISSION_DENIED
}

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repository: GeoTreasureRepository,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    private val selectedModuleId = MutableStateFlow(ModuleIds.SOUTH)
    private val location = MutableStateFlow(LocationState.LOCATING)

    /**
     * 原型 p01 的两种「没数据」态：
     *  - loading  = 首次拉取中（骨架屏 ≤3s，不白屏）
     *  - failed   = 一条都没拉到（保留底图配色 + 重新加载）
     * 演示数据在内存里，所以只有 loading 有一段可感知的过渡；接 CloudBase 后
     * 由 getCampusMap() 的时序与异常分别驱动这两位。
     */
    private data class MapLoad(val loading: Boolean, val failed: Boolean)
    private val mapLoad = MutableStateFlow(MapLoad(loading = true, failed = false))

    init {
        viewModelScope.launch {
            kotlinx.coroutines.delay(420)
            mapLoad.value = MapLoad(loading = false, failed = false)
        }
    }

    val uiState: StateFlow<ExploreUiState> = combine(
        repository.progress,
        selectedModuleId,
        location,
        networkMonitor.isOnline,
        mapLoad
    ) { progress: UserProgress, moduleId: String, loc: LocationState, online: Boolean, load: MapLoad ->
        val module = repository.module(moduleId) ?: repository.modules.first()
        val ownedSlots = progress.ownedSlots(module.id)
        val target = module.landmarks.firstOrNull { it.slot !in ownedSlots } ?: module.landmarks.first()
        ExploreUiState(
            modules = repository.modules,
            selectedModuleId = module.id,
            module = module,
            ownedSlots = ownedSlots,
            target = target,
            online = online,
            location = loc,
            shards = progress.totalShards,
            totalShards = repository.modules.sumOf { it.total },
            loading = load.loading,
            loadFailed = load.failed
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExploreUiState(
            modules = repository.modules,
            selectedModuleId = ModuleIds.SOUTH,
            module = repository.modules.first(),
            ownedSlots = repository.progress.value.ownedSlots(ModuleIds.SOUTH),
            target = repository.nextTarget(ModuleIds.SOUTH),
            online = true,
            location = LocationState.LOCATING,
            shards = repository.progress.value.totalShards,
            totalShards = repository.modules.sumOf { it.total },
            loading = true
        )
    )

    fun selectModule(moduleId: String) {
        selectedModuleId.value = moduleId
    }

    fun onLocationPermissionResult(granted: Boolean) {
        location.value = if (granted) LocationState.OK else LocationState.PERMISSION_DENIED
    }

    fun onLocating() {
        location.value = LocationState.LOCATING
    }

    fun onWeakSignal() {
        location.value = LocationState.WEAK_SIGNAL
    }

    /** 原型 p01:error 的「重新加载」：重新拉一次地标数据，失败则留在 error 态 */
    fun retryLoad() {
        mapLoad.value = MapLoad(loading = true, failed = false)
        viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            // demo 的数据在内存里，只有数据源为空才算「没加载出来」；
            // 接 CloudBase 后这里换成 getCampusMap() 的 catch 分支
            val failed = repository.modules.isEmpty()
            mapLoad.value = MapLoad(loading = false, failed = failed)
        }
    }
}
