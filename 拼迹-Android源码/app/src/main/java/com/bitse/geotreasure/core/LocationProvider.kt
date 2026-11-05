package com.bitse.geotreasure.core

import kotlinx.coroutines.flow.Flow

/**
 * 定位接缝。
 *
 * 为什么只暴露「到目标的距离」而不是原始坐标：
 * 设计稿明确「界面不预设打卡半径、也不写死精度门槛，是否通过由服务端在提交后判定」，
 * 所以前端只需要一个用来显示「距目标约 X m」的数，不需要、也不应该拿着坐标自己算通过与否。
 *
 * 正式实现：接高德定位 SDK（AMapLocationClient），把 ContinuousLocation 转成下面的 Flow。
 * 演示实现：DemoLocationProvider（固定 12m，只为对上设计稿文案）。
 */
interface LocationProvider {

    /**
     * 目标位置的相对距离（米）。null 表示暂时拿不到（未授权 / 正在定位）。
     * 每次订阅都会重新开始定位，取消订阅即停止。
     */
    fun distanceTo(latitude: Double, longitude: Double): Flow<Float?>
}
