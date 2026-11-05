package com.bitse.geotreasure.ui.navigation

import com.bitse.geotreasure.domain.model.CheckInOutcome
import com.bitse.geotreasure.domain.model.FailReason

/**
 * 结果页的路由键。
 *
 * P04 的每一种结局都要能从路由直接定位（result/LM-003/fail-radius），
 * 这样「哪一种失败」是数据而不是靠页面内计数推断出来的 —— 与原型 p04 的状态名一一对应。
 */
object OutcomeKeys {
    const val SUCCESS = "success"
    const val PENDING = "pending"
    const val CONFIRM = "confirm"
    const val FAIL_RADIUS = "fail-radius"
    const val FAIL_ACCURACY = "fail-accuracy"
    const val FAIL_PHOTO = "fail-photo"
    const val FAIL_NETWORK = "fail-network"

    fun of(outcome: CheckInOutcome): String = when (outcome) {
        is CheckInOutcome.Success -> SUCCESS
        is CheckInOutcome.Pending -> PENDING
        is CheckInOutcome.PendingConfirm -> CONFIRM
        is CheckInOutcome.Failed -> when (outcome.reason) {
            FailReason.RADIUS -> FAIL_RADIUS
            FailReason.ACCURACY -> FAIL_ACCURACY
            FailReason.PHOTO -> FAIL_PHOTO
            FailReason.NETWORK -> FAIL_NETWORK
        }
    }
}
