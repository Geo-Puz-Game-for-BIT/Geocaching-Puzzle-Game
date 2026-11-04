package com.bitse.geotreasure.domain.model

/**
 * 一个地标。id 形如 LM-001，与设计资源 design/assets/lm/LM-001.png 一一对应。
 * 地图 / 详情 / 拼图 / 记录四处都只认 id，避免“点开 A 显示 B”。
 */
data class Landmark(
    val id: String,
    val name: String,
    val moduleId: String,
    /** 在片区内的格子序号 0..3，决定 2×2 拼图里的位置 */
    val slot: Int,
    val distanceLabel: String,
    val difficulty: String,
    val terrain: String,
    val latitude: Double,
    val longitude: Double,
    /** 拍照任务编号，如 T-003 */
    val taskId: String,
    val hint: String
)

/** 一个 2×2 片区（南/北/东校区） */
data class CampusModule(
    val id: String,
    val name: String,
    val total: Int = 4,
    val landmarks: List<Landmark>
)

/** 地标 key 常量：给资源和演示数据用，避免字符串拼错 */
object LandmarkIds {
    const val SHU_TONG = "LM-001"
    const val BO_YA = "LM-002"
    const val LI_JIAO = "LM-003"
    const val GONG_YE = "LM-004"
    const val BEI_HU = "LM-005"
    const val ZONG_JIAO = "LM-006"
    const val XUE_FU = "LM-007"
    const val TU_SHU_GUAN = "LM-008"
    const val GAN_TANG = "LM-009"
    const val QIN_YUAN = "LM-010"
    const val WEN_CUI = "LM-011"
    const val WEN_BO = "LM-012"
}

object ModuleIds {
    const val SOUTH = "MP-A"
    const val NORTH = "MP-B"
    const val EAST = "MP-C"
}
