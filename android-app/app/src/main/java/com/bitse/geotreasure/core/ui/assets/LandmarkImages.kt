package com.bitse.geotreasure.core.ui.assets

import com.bitse.geotreasure.R

/**
 * 地标图资源映射 —— 唯一映射入口。
 *
 * key 使用现有业务 id（`core.model.LiangxiangTreasureCatalog` 的 `lmk_*`），
 * 不使用任何另一套 domain/LandmarkIds 常量，避免再带入未接通的架构。
 *
 * 两档：big（420px，详情 Hero / 相机取景）与 small（200px，缩略图 / 拼图格 / 记录照）。
 * 资源文件名固定 lm_001…lm_012，与设计资产 design/assets/lm/LM-001.png 一一对应；
 * 任何页面取图都必须经过 [big] / [small]，不允许自己在页面里 when(id)。
 *
 * id ↔ 资源对照（按设计稿编号顺序，南→北→东）：
 *  lmk_south_shutong=LM-001、lmk_south_boya=LM-002、lmk_south_lijiao=LM-003、
 *  lmk_south_industrial_ecology=LM-004、lmk_north_lake=LM-005、lmk_north_zongjiao=LM-006、
 *  lmk_north_student_service=LM-007、lmk_north_library=LM-008、lmk_east_gantang=LM-009、
 *  lmk_east_qinyuan=LM-010、lmk_east_wencui=LM-011、lmk_east_wenbo=LM-012
 */
object LandmarkImages {

    private val bigMap: Map<String, Int> = mapOf(
        "lmk_south_shutong" to R.drawable.lm_001,
        "lmk_south_boya" to R.drawable.lm_002,
        "lmk_south_lijiao" to R.drawable.lm_003,
        "lmk_south_industrial_ecology" to R.drawable.lm_004,
        "lmk_north_lake" to R.drawable.lm_005,
        "lmk_north_zongjiao" to R.drawable.lm_006,
        "lmk_north_student_service" to R.drawable.lm_007,
        "lmk_north_library" to R.drawable.lm_008,
        "lmk_east_gantang" to R.drawable.lm_009,
        "lmk_east_qinyuan" to R.drawable.lm_010,
        "lmk_east_wencui" to R.drawable.lm_011,
        "lmk_east_wenbo" to R.drawable.lm_012
    )

    private val smallMap: Map<String, Int> = mapOf(
        "lmk_south_shutong" to R.drawable.lm_001_s,
        "lmk_south_boya" to R.drawable.lm_002_s,
        "lmk_south_lijiao" to R.drawable.lm_003_s,
        "lmk_south_industrial_ecology" to R.drawable.lm_004_s,
        "lmk_north_lake" to R.drawable.lm_005_s,
        "lmk_north_zongjiao" to R.drawable.lm_006_s,
        "lmk_north_student_service" to R.drawable.lm_007_s,
        "lmk_north_library" to R.drawable.lm_008_s,
        "lmk_east_gantang" to R.drawable.lm_009_s,
        "lmk_east_qinyuan" to R.drawable.lm_010_s,
        "lmk_east_wencui" to R.drawable.lm_011_s,
        "lmk_east_wenbo" to R.drawable.lm_012_s
    )

    /** 420px 大档：Hero、相机取景、拍后预览 */
    fun big(landmarkId: String): Int = bigMap[landmarkId] ?: R.drawable.lm_001

    /** 200px 小档：列表缩略图、2×2 拼图格、记录照片 */
    fun small(landmarkId: String): Int = smallMap[landmarkId] ?: R.drawable.lm_001_s

    /** 未解锁时的占位色块不需要图，返回 null 让调用方画遮罩 */
    fun smallOrNull(landmarkId: String): Int? = smallMap[landmarkId]
}
