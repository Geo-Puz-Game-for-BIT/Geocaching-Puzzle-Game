package com.bitse.geotreasure.feature.camera

import com.bitse.geotreasure.data.remote.CheckInApiException
import org.junit.Assert.assertEquals
import org.junit.Test

class CheckInFailureMessageTest {

    @Test
    fun outOfRange_mapsToFriendlyMessage() {
        val message = checkInFailureMessage(CheckInApiException("OUT_OF_RANGE", false, "Current location is outside"))
        assertEquals("已离开打卡范围，请回到地标附近重试", message)
    }

    @Test
    fun accuracyTooLow_mapsToFriendlyMessage() {
        val message = checkInFailureMessage(CheckInApiException("LOCATION_ACCURACY_TOO_LOW", false, "bad accuracy"))
        assertEquals("定位精度不足，请到开阔处重试", message)
    }

    @Test
    fun challengeExpired_mapsToFriendlyMessage() {
        val message = checkInFailureMessage(CheckInApiException("CHALLENGE_EXPIRED", true, "expired"))
        assertEquals("挑战码已过期，请重新提交", message)
    }

    @Test
    fun notConfigured_usesServerMessage() {
        val message = checkInFailureMessage(CheckInApiException("NOT_CONFIGURED", false, "服务端地址未配置"))
        assertEquals("服务端地址未配置", message)
    }

    @Test
    fun genericError_usesMessage() {
        val message = checkInFailureMessage(CheckInApiException("INTERNAL_ERROR", true, "boom"))
        assertEquals("boom", message)
    }

    @Test
    fun plainThrowable_usesMessage() {
        val message = checkInFailureMessage(IllegalStateException("定位失败"))
        assertEquals("定位失败", message)
    }
}
