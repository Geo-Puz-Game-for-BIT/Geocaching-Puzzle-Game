package com.bitse.geotreasure.core.checkin

import com.bitse.geotreasure.core.model.CheckInReceipt

/**
 * 按 attempt 查询打卡结果的有类型结果（区别于「查询某地标历史成功」）。
 *
 * 只有「服务端明确返回特定 404 CHECKIN_NOT_FOUND」才代表 [NotFound]（明确无记录）；
 * 普通路由 404 / 代理 404 / 405 / 5xx / 超时 / 未配置一律归为 [Unavailable]（查询故障，结果未知）。
 * 未配置 baseUrl 时不得返回 [NotFound]（未配置不能作为「明确未成功」的安全新尝试依据）。
 */
sealed interface CheckInQueryResult {
    /** 本次 attempt 已成功。 */
    data class Success(val receipt: CheckInReceipt) : CheckInQueryResult

    /** 明确无记录（服务端返回 404 且错误码为 CHECKIN_NOT_FOUND）。 */
    data object NotFound : CheckInQueryResult

    /** 已受理未落库（202 PROCESSING），结果未知，稍后可重查。 */
    data object Processing : CheckInQueryResult

    /** 查询故障（405 / 普通404 / 超时 / 5xx / 未配置），结果未知。 */
    data object Unavailable : CheckInQueryResult
}
