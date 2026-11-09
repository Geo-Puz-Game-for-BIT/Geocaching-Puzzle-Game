package com.bitse.geotreasure.core.location

import com.bitse.geotreasure.core.model.LocationSample
import kotlinx.datetime.Instant

/**
 * 打卡定位新鲜度规则（纯逻辑，JVM 可单测）。
 *
 * 对齐 B 组「GPS / 5 秒新鲜度」口径：正式打卡门控只接受 5 秒内采集的定位样本，
 * 超过该窗口判定为过期（LOCATION_STALE），必须先重新定位，避免用陈旧坐标打卡。
 * 与战争迷雾探索阈值无关，勿混用。
 */
object LocationFreshness {

    /** 打卡允许的定位样本最大年龄（秒）。 */
    const val MAX_CHECK_IN_LOCATION_AGE_SECONDS = 5L

    /** 样本距 [now] 的年龄（秒）；样本缺失返回 [Long.MAX_VALUE] 视为永不过期外的「不可用」。 */
    fun ageSeconds(sample: LocationSample?, now: Instant): Long {
        if (sample == null) return Long.MAX_VALUE
        return (now.toEpochMilliseconds() - sample.capturedAt.toEpochMilliseconds()) / 1000L
    }

    /** 样本是否新鲜：非空且年龄落在 `[0, maxAgeSeconds]` 内（负年龄视为时钟异常 → 不过关）。 */
    fun isFresh(
        sample: LocationSample?,
        now: Instant,
        maxAgeSeconds: Long = MAX_CHECK_IN_LOCATION_AGE_SECONDS,
    ): Boolean {
        if (sample == null) return false
        val age = ageSeconds(sample, now)
        return age in 0..maxAgeSeconds
    }
}
