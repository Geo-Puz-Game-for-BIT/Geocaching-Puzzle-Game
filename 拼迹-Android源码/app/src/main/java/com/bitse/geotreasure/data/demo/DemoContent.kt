package com.bitse.geotreasure.data.demo

import com.bitse.geotreasure.domain.model.CampusModule
import com.bitse.geotreasure.domain.model.CheckInPhoto
import com.bitse.geotreasure.domain.model.Landmark
import com.bitse.geotreasure.domain.model.LandmarkIds
import com.bitse.geotreasure.domain.model.ModuleIds
import com.bitse.geotreasure.domain.model.ModuleProgress
import com.bitse.geotreasure.domain.model.UserProgress

/**
 * 演示数据 —— 与 design/prototype/index.html 的 MODULES / LM_DIST / MY_PHOTOS 完全对齐。
 *
 * 接 CloudBase 时：模块与地标改由云函数 `getCampusMap` 下发（结构不变），
 * 这里只保留给「无网络时的本地兜底 + 单元测试 + Compose Preview」用。
 */
object DemoContent {

    /** 良乡校区大致中心，仅用于演示定位与距离排序 */
    private const val BASE_LAT = 39.7285
    private const val BASE_LNG = 116.1713

    fun landmark(
        id: String,
        name: String,
        moduleId: String,
        slot: Int,
        distance: String,
        difficulty: String,
        terrain: String,
        latOffset: Double,
        lngOffset: Double
    ) = Landmark(
        id = id,
        name = name,
        moduleId = moduleId,
        slot = slot,
        distanceLabel = distance,
        difficulty = difficulty,
        terrain = terrain,
        latitude = BASE_LAT + latOffset,
        longitude = BASE_LNG + lngOffset,
        taskId = "T-" + id.substringAfter('-'),
        hint = "把地标放在画面上半部分，人站在它前面，两者都完整入镜。"
    )

    val south = CampusModule(
        id = ModuleIds.SOUTH,
        name = "南校区",
        landmarks = listOf(
            landmark(LandmarkIds.SHU_TONG, "疏桐", ModuleIds.SOUTH, 0, "68 m", "★★☆", "林荫", 0.00060, 0.00042),
            landmark(LandmarkIds.BO_YA, "博雅", ModuleIds.SOUTH, 1, "150 m", "★☆☆", "平地", 0.00042, -0.00060),
            landmark(LandmarkIds.LI_JIAO, "理教", ModuleIds.SOUTH, 2, "210 m", "★☆☆", "台阶", -0.00058, 0.00055),
            landmark(LandmarkIds.GONG_YE, "工业生态楼", ModuleIds.SOUTH, 3, "300 m", "★★☆", "平地", -0.00072, -0.00038)
        )
    )

    val north = CampusModule(
        id = ModuleIds.NORTH,
        name = "北校区",
        landmarks = listOf(
            landmark(LandmarkIds.BEI_HU, "北湖", ModuleIds.NORTH, 0, "420 m", "★☆☆", "湖畔", 0.00190, 0.00010),
            landmark(LandmarkIds.ZONG_JIAO, "综教", ModuleIds.NORTH, 1, "90 m", "★☆☆", "平地", 0.00150, 0.00072),
            landmark(LandmarkIds.XUE_FU, "学服", ModuleIds.NORTH, 2, "160 m", "★☆☆", "平地", 0.00120, -0.00055),
            landmark(LandmarkIds.TU_SHU_GUAN, "图书馆", ModuleIds.NORTH, 3, "260 m", "★★☆", "台阶", 0.00210, -0.00020)
        )
    )

    val east = CampusModule(
        id = ModuleIds.EAST,
        name = "东校区",
        landmarks = listOf(
            landmark(LandmarkIds.GAN_TANG, "甘棠", ModuleIds.EAST, 0, "380 m", "★☆☆", "平地", -0.00090, 0.00240),
            landmark(LandmarkIds.QIN_YUAN, "勤园", ModuleIds.EAST, 1, "120 m", "★☆☆", "平地", -0.00110, 0.00275),
            landmark(LandmarkIds.WEN_CUI, "文萃", ModuleIds.EAST, 2, "240 m", "★★☆", "林荫", -0.00145, 0.00210),
            landmark(LandmarkIds.WEN_BO, "文博中心", ModuleIds.EAST, 3, "330 m", "★★☆", "平地", -0.00160, 0.00285)
        )
    )

    val modules: List<CampusModule> = listOf(south, north, east)

    /**
     * 初始进度 = 原型默认态：南 3/4（第 2/3/4 格已收，疏桐待办）、北 1/4、东 0/4。
     * 注意这里存的是「哪几格」，不是「前 N 格」—— 直接决定了 P05 总览的明暗归属。
     */
    val initialProgress = UserProgress(
        modules = listOf(
            ModuleProgress(ModuleIds.SOUTH, listOf(1, 2, 3)),
            ModuleProgress(ModuleIds.NORTH, listOf(0)),
            ModuleProgress(ModuleIds.EAST, emptyList())
        )
    )

    /** P06 的 4 张打卡照；第一张标记为「服务端确认中」 */
    val initialPhotos: List<CheckInPhoto> = listOf(
        CheckInPhoto("ph-1", LandmarkIds.SHU_TONG, "疏桐", ModuleIds.SOUTH, 0, "10-29 09:41", pending = true),
        CheckInPhoto("ph-2", LandmarkIds.BO_YA, "博雅", ModuleIds.SOUTH, 1, "10-26 10:02"),
        CheckInPhoto("ph-3", LandmarkIds.LI_JIAO, "理教", ModuleIds.SOUTH, 2, "10-25 16:20"),
        CheckInPhoto("ph-4", LandmarkIds.GONG_YE, "工业生态楼", ModuleIds.SOUTH, 3, "10-24 15:08")
    )

    fun allLandmarks(): List<Landmark> = modules.flatMap { it.landmarks }
}
