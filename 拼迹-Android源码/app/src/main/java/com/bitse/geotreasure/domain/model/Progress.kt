package com.bitse.geotreasure.domain.model

/** 一片区的收集进度：哪几格已到手（不是“前 N 格”）。 */
data class ModuleProgress(
    val moduleId: String,
    val ownedSlots: List<Int>
) {
    fun owns(slot: Int): Boolean = slot in ownedSlots
}

/** 打卡照片（P06 记录页 / P04 碎片归位共用） */
data class CheckInPhoto(
    val id: String,
    val landmarkId: String,
    val landmarkName: String,
    val moduleId: String,
    val slot: Int,
    val takenAtLabel: String,
    /** true = 服务端还没回结果，界面上显示「确认中」而不是「私密」 */
    val pending: Boolean = false
)

/** 全局进度。界面只读它，写入只经 Repository，保证四处口径一致。 */
data class UserProgress(
    val modules: List<ModuleProgress>
) {
    fun ownedCount(moduleId: String): Int =
        modules.firstOrNull { it.moduleId == moduleId }?.ownedSlots?.size ?: 0

    fun owns(moduleId: String, slot: Int): Boolean =
        modules.firstOrNull { it.moduleId == moduleId }?.owns(slot) ?: false

    fun ownedSlots(moduleId: String): List<Int> =
        modules.firstOrNull { it.moduleId == moduleId }?.ownedSlots ?: emptyList()

    val totalShards: Int get() = modules.sumOf { it.ownedSlots.size }

    val completedModules: Int get() = modules.count { it.ownedSlots.size >= 4 }

    val isModuleComplete: (String) -> Boolean = { id -> ownedCount(id) >= 4 }
}

/** 提交打卡的三种结局，与 P04 的状态一一对应 */
sealed interface CheckInOutcome {
    data class Success(val landmarkId: String) : CheckInOutcome
    data object Pending : CheckInOutcome
    data object PendingConfirm : CheckInOutcome
    data class Failed(val reason: FailReason) : CheckInOutcome
}

enum class FailReason { RADIUS, ACCURACY, PHOTO, NETWORK }

/** 定位状态：P01 / P03 的横幅与距离提示都由它驱动 */
enum class LocationState { OK, PERMISSION_DENIED, WEAK_SIGNAL, LOCATING }
