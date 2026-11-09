package com.bitse.geotreasure.feature.map3d

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitse.geotreasure.domain.model.CampusModule
import com.bitse.geotreasure.domain.model.Landmark
import com.bitse.geotreasure.domain.repository.GeoTreasureRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CampusLayer(
    val module: CampusModule,
    val ownedSlots: List<Int>,
    /** 短名：南 / 北 / 东，给顶卡计数用 */
    val shortName: String
) {
    val unlocked: Int get() = ownedSlots.size
    val complete: Boolean get() = unlocked >= module.total
}

data class Map3DUiState(
    val layers: List<CampusLayer>,
    val shards: Int,
    val totalShards: Int,
    /** 当前聚焦的片区 id；null = 全览 */
    val focusedModuleId: String?,
    val nextTarget: Landmark,
    val nextTargetCampus: CampusLayer?,
    /** 用户主动点开看的那一处地标（对应原型 P07 的 partial / campus 两种说明卡） */
    val spotlight: Landmark? = null,
    val spotlightCampus: CampusLayer? = null
) {
    val fogOpened: Int get() = shards
    val completedCampuses: Int get() = layers.count { it.complete }
    val allOpen: Boolean get() = fogOpened >= totalShards
    /** 一片云都没散开 = 原型 p07:fog */
    val fullyFogged: Boolean get() = fogOpened == 0
}

@HiltViewModel
class Map3DViewModel @Inject constructor(
    private val repository: GeoTreasureRepository
) : ViewModel() {

    private val focusedModuleId = MutableStateFlow<String?>(null)
    private val spotlightId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<Map3DUiState> = combine(
        repository.progress,
        focusedModuleId,
        spotlightId
    ) { progress, focus, spot ->
        val layers = repository.modules.map { module ->
            CampusLayer(
                module = module,
                ownedSlots = progress.ownedSlots(module.id),
                shortName = when (module.id) {
                    "MP-A" -> "南"
                    "MP-B" -> "北"
                    else -> "东"
                }
            )
        }
        val focused = layers.firstOrNull { it.module.id == focus }
        val next = focused?.let { layer ->
            layer.module.landmarks.firstOrNull { it.slot !in layer.ownedSlots } ?: layer.module.landmarks.first()
        } ?: repository.nextTargetGlobal()

        val spotLandmark = spot?.let { id -> repository.landmark(id) }
        Map3DUiState(
            layers = layers,
            shards = progress.totalShards,
            totalShards = repository.modules.sumOf { it.total },
            focusedModuleId = focus,
            nextTarget = next,
            nextTargetCampus = layers.firstOrNull { it.module.id == next.moduleId },
            spotlight = spotLandmark,
            spotlightCampus = spotLandmark?.let { lm -> layers.firstOrNull { it.module.id == lm.moduleId } }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = Map3DUiState(
            layers = repository.modules.map { module ->
                CampusLayer(
                    module = module,
                    ownedSlots = repository.progress.value.ownedSlots(module.id),
                    shortName = when (module.id) {
                        "MP-A" -> "南"
                        "MP-B" -> "北"
                        else -> "东"
                    }
                )
            },
            shards = repository.progress.value.totalShards,
            totalShards = repository.modules.sumOf { it.total },
            focusedModuleId = null,
            nextTarget = repository.nextTargetGlobal(),
            nextTargetCampus = null
        )
    )

    fun focus(moduleId: String?) {
        focusedModuleId.value = moduleId
    }

    /** 点水滴看这一处的说明卡；null = 回到「下一个待办」口径 */
    fun spotlight(landmarkId: String?) {
        spotlightId.value = landmarkId
    }
}
