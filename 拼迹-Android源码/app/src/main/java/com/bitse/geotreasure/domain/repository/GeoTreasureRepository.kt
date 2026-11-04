package com.bitse.geotreasure.domain.repository

import com.bitse.geotreasure.domain.model.CampusModule
import com.bitse.geotreasure.domain.model.CheckInOutcome
import com.bitse.geotreasure.domain.model.CheckInPhoto
import com.bitse.geotreasure.domain.model.Landmark
import com.bitse.geotreasure.domain.model.UserProgress
import kotlinx.coroutines.flow.StateFlow

/**
 * 单一数据出口。页面不持有业务状态，只订阅这里的 Flow —— 这样 P01 进度卡、
 * P05 拼图、P06 统计三处永远同一口径（原型里踩过“总览明暗与片区页相反”的坑）。
 */
interface GeoTreasureRepository {

    val modules: List<CampusModule>

    val progress: StateFlow<UserProgress>

    val photos: StateFlow<List<CheckInPhoto>>

    fun landmark(id: String): Landmark?

    fun module(id: String): CampusModule?

    fun moduleOfLandmark(landmarkId: String): CampusModule?

    /** 该片区下一个待办地标；全部集齐时返回第一个（与原型 lmIdx() 一致） */
    fun nextTarget(moduleId: String): Landmark

    /** 全局下一个待办：优先未集齐的片区 */
    fun nextTargetGlobal(): Landmark

    /** 待确认的打卡条数：没有在途请求时 P06 不出现「还在确认」入口 */
    val pendingCount: StateFlow<Int>

    /**
     * 提交打卡。photoPath 为空表示没有可提交的照片（用于演示「照片不合格」）。
     * 真实实现会：上传到云存储 → 调云函数 checkIn → 服务端判定后回写。
     */
    suspend fun submitCheckIn(landmarkId: String, photoPath: String?): CheckInOutcome

    fun deletePhoto(photoId: String)

    /** 调试/演示用：直接渲染某个结果态，不影响真实进度 */
    fun previewOutcome(landmarkId: String, outcomeKey: String): CheckInOutcome
}
