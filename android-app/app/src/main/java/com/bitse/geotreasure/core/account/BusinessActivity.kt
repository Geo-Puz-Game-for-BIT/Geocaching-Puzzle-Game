package com.bitse.geotreasure.core.account

/**
 * 打卡相关业务活动，供 UI 观察「地图过渡 / 展示动画是否适宜」。
 *
 * 与 A/B 约定：过门事件不 popBackStack、返回地图不重放旧事件；
 * 相机/上传/提交/查询/封存等业务进行中时，地图过渡不强制打断当前页面。
 * 动画结束只代表画面播放结束，不代表真实过门、打卡成功或服务端退出成功。
 */
enum class BusinessActivity {
    /** 空闲。 */
    IDLE,

    /** 相机页面活跃（取景/拍摄）。 */
    CAMERA,

    /** 正在上传照片。 */
    UPLOADING,

    /** 正在提交打卡。 */
    SUBMITTING,

    /** 正在查询恢复结果。 */
    QUERYING,

    /** 提交结果未知，等待恢复。 */
    UNKNOWN,

    /** 等待封存（退出前处理待确认打卡）。 */
    AWAITING_SEAL,
}

/** 是否处于「不宜播放地图过渡动画」的业务进行中状态。 */
fun BusinessActivity.isActive(): Boolean = this != BusinessActivity.IDLE
