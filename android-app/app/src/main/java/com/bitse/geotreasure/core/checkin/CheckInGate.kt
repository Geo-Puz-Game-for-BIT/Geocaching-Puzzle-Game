package com.bitse.geotreasure.core.checkin

import com.bitse.geotreasure.core.location.MAX_CHECK_IN_ACCURACY_METERS
import com.bitse.geotreasure.core.location.distanceMeters
import com.bitse.geotreasure.core.location.isWithinCheckInArea
import com.bitse.geotreasure.core.model.CheckInArea
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.LocationSample

/** 打卡入口门控判定结果。 */
sealed interface CheckInGateResult {
    data class Ready(val distanceMeters: Double) : CheckInGateResult

    data class Blocked(
        val reason: BlockReason,
        val distanceMeters: Double? = null,
    ) : CheckInGateResult
}

enum class BlockReason(val message: String) {
    NO_LOCATION("等待定位…"),
    ACCURACY_TOO_LOW("定位精度不足（需 ≤30 米），请到开阔处"),
    OUT_OF_RANGE("尚未进入地标打卡范围"),
}

/**
 * 打卡入口门控纯逻辑（可 JVM 单测）：
 * 只有「进入打卡范围 + 定位精度 ≤30m」才放行，否则给出明确原因。
 * 战争迷雾探索阈值（50m）与此门控无关，勿混用。
 */
object CheckInGate {

    fun evaluate(
        location: LocationSample?,
        landmarkPoint: GeoPoint,
        checkInArea: CheckInArea,
    ): CheckInGateResult {
        if (location == null) {
            return CheckInGateResult.Blocked(BlockReason.NO_LOCATION)
        }
        val distance = distanceMeters(location.point, landmarkPoint)
        if (location.accuracyMeters > MAX_CHECK_IN_ACCURACY_METERS) {
            return CheckInGateResult.Blocked(BlockReason.ACCURACY_TOO_LOW, distance)
        }
        val inArea = isWithinCheckInArea(location.point, landmarkPoint, checkInArea, location.accuracyMeters)
        return if (inArea) {
            CheckInGateResult.Ready(distance)
        } else {
            CheckInGateResult.Blocked(BlockReason.OUT_OF_RANGE, distance)
        }
    }
}
