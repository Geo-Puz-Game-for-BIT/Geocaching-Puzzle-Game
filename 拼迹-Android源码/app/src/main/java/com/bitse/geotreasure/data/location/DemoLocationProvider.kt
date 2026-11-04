package com.bitse.geotreasure.data.location

import com.bitse.geotreasure.core.LocationProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 演示用定位：固定返回 12m，与设计稿 P03「距目标约 12 m · 定位正常」一致。
 *
 * 接高德时换成 AMapLocationProvider（同一个接口），页面代码不需要改。
 * 注意：这里返回的是「演示值」，不是真实定位 —— 真机上不会因为这个值就允许打卡，
 * 是否通过由服务端在提交后判定。
 */
class DemoLocationProvider : LocationProvider {

    override fun distanceTo(latitude: Double, longitude: Double): Flow<Float?> = flow {
        emit(null)          // 先给「定位中」，界面显示占位而不是 0 m
        delay(700)
        emit(DEMO_DISTANCE_METERS)
    }

    private companion object {
        const val DEMO_DISTANCE_METERS = 12f
    }
}
