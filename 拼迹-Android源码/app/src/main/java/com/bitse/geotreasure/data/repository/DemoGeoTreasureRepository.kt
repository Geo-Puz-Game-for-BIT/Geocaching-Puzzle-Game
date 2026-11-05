package com.bitse.geotreasure.data.repository

import com.bitse.geotreasure.data.demo.DemoContent
import com.bitse.geotreasure.data.local.CheckInRecordDao
import com.bitse.geotreasure.data.local.CheckInRecordEntity
import com.bitse.geotreasure.domain.model.CampusModule
import com.bitse.geotreasure.domain.model.CheckInOutcome
import com.bitse.geotreasure.domain.model.CheckInPhoto
import com.bitse.geotreasure.domain.model.FailReason
import com.bitse.geotreasure.domain.model.Landmark
import com.bitse.geotreasure.domain.model.ModuleProgress
import com.bitse.geotreasure.domain.model.UserProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 无后端时的可运行实现：数据在内存里，行为按真实时序（提交 → 1.5s 处理中 → 成功）。
 *
 * 换成 CloudBase 后只需替换本类：
 *  - getCampusMap()           → 拉模块与地标
 *  - uploadFile()             → 云存储存照片，拿到 fileID
 *  - checkIn(landmarkId, ...)  → 云函数判定（距离/精度/照片时间都在服务端）
 *  - watchProgress(userId)    → 实时回写进度（或轮询）
 * 页面与 ViewModel 一行都不用改。
 */
@Singleton
class DemoGeoTreasureRepository @Inject constructor(
    private val dao: CheckInRecordDao
) : com.bitse.geotreasure.domain.repository.GeoTreasureRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val modules: List<CampusModule> = DemoContent.modules

    private val _progress = MutableStateFlow(DemoContent.initialProgress)
    override val progress: StateFlow<UserProgress> = _progress

    private val _photos = MutableStateFlow(DemoContent.initialPhotos)
    override val photos: StateFlow<List<CheckInPhoto>> = _photos

    /**
     * 「在途打卡条数」，对应原型的 S.pend。
     * 原型初始 S.pend = 0 —— 用户没遇到「提交后服务端还没回结果」时 P06 的待确认卡整体不渲染。
     * 提交流程开始置 1，拿到 Success 回 0（原型 p04 success 分支同样清零）。
     * 注意：不能拿 photos.count{pending} 来算 —— MY_PHOTOS[0] 的 pend 标记是常驻的，
     * 真正决定显示与否的是 S.pend 这个在途计数。
     */
    private val _pending = MutableStateFlow(0)
    override val pendingCount: StateFlow<Int> = _pending

    override fun landmark(id: String): Landmark? =
        DemoContent.allLandmarks().firstOrNull { it.id == id }

    override fun module(id: String): CampusModule? = modules.firstOrNull { it.id == id }

    override fun moduleOfLandmark(landmarkId: String): CampusModule? =
        modules.firstOrNull { module -> module.landmarks.any { it.id == landmarkId } }

    override fun nextTarget(moduleId: String): Landmark {
        val module = module(moduleId) ?: modules.first()
        val owned = _progress.value.ownedSlots(module.id)
        return module.landmarks.firstOrNull { it.slot !in owned } ?: module.landmarks.first()
    }

    override fun nextTargetGlobal(): Landmark {
        val module = modules.firstOrNull { _progress.value.ownedCount(it.id) < it.total } ?: modules.first()
        return nextTarget(module.id)
    }

    override suspend fun submitCheckIn(landmarkId: String, photoPath: String?): CheckInOutcome {
        // 真实实现里这一段是「上传 + 云函数判定」，客户端不做半径/精度硬判
        _pending.value = 1 // 原型 data-act="submit" 时先置 S.pend = 1
        delay(1500)
        if (photoPath.isNullOrBlank()) {
            _pending.value = 0
            return CheckInOutcome.Failed(FailReason.PHOTO)
        }
        val landmark = landmark(landmarkId)
            ?: run {
                _pending.value = 0
                return CheckInOutcome.Failed(FailReason.NETWORK)
            }

        grantFragment(landmark)

        val now = LocalDateTime.now()
        val newPhoto = CheckInPhoto(
            id = "ph-" + now.toLocalTime().toSecondOfDay(),
            landmarkId = landmark.id,
            landmarkName = landmark.name,
            moduleId = landmark.moduleId,
            slot = landmark.slot,
            takenAtLabel = now.format(DateTimeFormatter.ofPattern("MM-dd HH:mm")),
            pending = false
        )
        _photos.value = listOf(newPhoto) + _photos.value.filterNot { it.landmarkId == landmarkId }

        runCatching {
            dao.insert(
                CheckInRecordEntity(
                    id = newPhoto.id,
                    landmarkId = landmark.id,
                    photoPath = photoPath,
                    createdAtMillis = System.currentTimeMillis(),
                    status = "CONFIRMED"
                )
            )
        }

        _pending.value = 0 // 原型 p04 success：S.pend = 0
        return CheckInOutcome.Success(landmark.id)
    }

    private fun grantFragment(landmark: Landmark) {
        val current = _progress.value
        val moduleProgress = current.modules.firstOrNull { it.moduleId == landmark.moduleId }
            ?: ModuleProgress(landmark.moduleId, emptyList())
        if (landmark.slot in moduleProgress.ownedSlots) return

        val updated = moduleProgress.copy(ownedSlots = (moduleProgress.ownedSlots + landmark.slot).sorted())
        _progress.value = current.copy(
            modules = current.modules.map { if (it.moduleId == updated.moduleId) updated else it }
        )
    }

    override fun deletePhoto(photoId: String) {
        // 删除照片不撤销碎片 —— 与 P06 的文案口径一致
        _photos.value = _photos.value.filterNot { it.id == photoId }
    }

    override fun previewOutcome(landmarkId: String, outcomeKey: String): CheckInOutcome =
        when (outcomeKey) {
            "success" -> CheckInOutcome.Success(landmarkId)
            "pending" -> CheckInOutcome.Pending
            "confirm" -> CheckInOutcome.PendingConfirm
            "fail-radius" -> CheckInOutcome.Failed(FailReason.RADIUS)
            "fail-accuracy" -> CheckInOutcome.Failed(FailReason.ACCURACY)
            "fail-photo" -> CheckInOutcome.Failed(FailReason.PHOTO)
            else -> CheckInOutcome.Failed(FailReason.NETWORK)
        }
}
