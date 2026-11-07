package com.bitse.geotreasure.core.checkin

import com.bitse.geotreasure.core.location.LocationFreshness
import com.bitse.geotreasure.core.location.MAX_CHECK_IN_ACCURACY_METERS
import com.bitse.geotreasure.core.location.distanceMeters
import com.bitse.geotreasure.core.location.isWithinCheckInArea
import com.bitse.geotreasure.core.model.CheckInArea
import com.bitse.geotreasure.core.model.GeoPoint
import com.bitse.geotreasure.core.model.LocationSample
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

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
    LOCATION_STALE("定位已过期，请重新定位"),
    ACCURACY_TOO_LOW("定位精度不足（需 ≤30 米），请到开阔处"),
    OUT_OF_RANGE("尚未进入地标打卡范围"),
}

/**
 * 打卡门控纯逻辑（JVM 可单测），覆盖「开拍 / 重拍 / 提交」共用判定：
 * 只有「定位样本新鲜（≤5s） + 精度 ≤30m + 进入逐地标范围」才放行，否则给出明确可恢复原因。
 * 战争迷雾探索阈值（50m）与此门控无关，勿混用。
 */
object CheckInGate {

    fun evaluate(
        location: LocationSample?,
        landmarkPoint: GeoPoint,
        checkInArea: CheckInArea,
        now: Instant = Clock.System.now(),
    ): CheckInGateResult {
        if (location == null) {
            return CheckInGateResult.Blocked(BlockReason.NO_LOCATION)
        }
        val distance = distanceMeters(location.point, landmarkPoint)
        if (!LocationFreshness.isFresh(location, now)) {
            return CheckInGateResult.Blocked(BlockReason.LOCATION_STALE, distance)
        }
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
