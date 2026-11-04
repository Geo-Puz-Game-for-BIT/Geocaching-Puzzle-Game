package com.bitse.geotreasure.ui.assets

import com.bitse.geotreasure.R
import com.bitse.geotreasure.domain.model.LandmarkIds

/**
 * 地标图资源映射 —— 唯一映射入口。
 *
 * 两档：big（420px，详情 Hero / 相机取景）与 small（200px，缩略图 / 拼图格 / 记录照）。
 * 资源文件名固定 lm_001…lm_012，与设计资产 design/assets/lm/LM-001.png 一一对应；
 * 任何页面取图都必须经过 [big] / [small]，不允许自己在页面里 when(id) —— 否则又会出
 * 「点开理教显示疏桐」那类错位。
 */
object LandmarkImages {

    private val bigMap: Map<String, Int> = mapOf(
        LandmarkIds.SHU_TONG to R.drawable.lm_001,
        LandmarkIds.BO_YA to R.drawable.lm_002,
        LandmarkIds.LI_JIAO to R.drawable.lm_003,
        LandmarkIds.GONG_YE to R.drawable.lm_004,
        LandmarkIds.BEI_HU to R.drawable.lm_005,
        LandmarkIds.ZONG_JIAO to R.drawable.lm_006,
        LandmarkIds.XUE_FU to R.drawable.lm_007,
        LandmarkIds.TU_SHU_GUAN to R.drawable.lm_008,
        LandmarkIds.GAN_TANG to R.drawable.lm_009,
        LandmarkIds.QIN_YUAN to R.drawable.lm_010,
        LandmarkIds.WEN_CUI to R.drawable.lm_011,
        LandmarkIds.WEN_BO to R.drawable.lm_012
    )

    private val smallMap: Map<String, Int> = mapOf(
        LandmarkIds.SHU_TONG to R.drawable.lm_001_s,
        LandmarkIds.BO_YA to R.drawable.lm_002_s,
        LandmarkIds.LI_JIAO to R.drawable.lm_003_s,
        LandmarkIds.GONG_YE to R.drawable.lm_004_s,
        LandmarkIds.BEI_HU to R.drawable.lm_005_s,
        LandmarkIds.ZONG_JIAO to R.drawable.lm_006_s,
        LandmarkIds.XUE_FU to R.drawable.lm_007_s,
        LandmarkIds.TU_SHU_GUAN to R.drawable.lm_008_s,
        LandmarkIds.GAN_TANG to R.drawable.lm_009_s,
        LandmarkIds.QIN_YUAN to R.drawable.lm_010_s,
        LandmarkIds.WEN_CUI to R.drawable.lm_011_s,
        LandmarkIds.WEN_BO to R.drawable.lm_012_s
    )

    /** 420px 大档：Hero、相机取景、拍后预览 */
    fun big(landmarkId: String): Int = bigMap[landmarkId] ?: R.drawable.lm_001

    /** 200px 小档：列表缩略图、2×2 拼图格、记录照片 */
    fun small(landmarkId: String): Int = smallMap[landmarkId] ?: R.drawable.lm_001_s

    /** 未解锁时的占位色块不需要图，返回 null 让调用方画遮罩 */
    fun smallOrNull(landmarkId: String): Int? = smallMap[landmarkId]
}
