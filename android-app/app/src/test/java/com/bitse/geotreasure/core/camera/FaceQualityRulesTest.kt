package com.bitse.geotreasure.core.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceQualityRulesTest {

    @Test
    fun evaluateFaceCount_zeroIsNone() {
        assertEquals(FaceQualityRules.FaceCountVerdict.NONE, FaceQualityRules.evaluateFaceCount(0))
    }

    @Test
    fun evaluateFaceCount_oneIsOk() {
        assertEquals(FaceQualityRules.FaceCountVerdict.OK, FaceQualityRules.evaluateFaceCount(1))
    }

    @Test
    fun evaluateFaceCount_manyIsTooMany() {
        assertEquals(FaceQualityRules.FaceCountVerdict.TOO_MANY, FaceQualityRules.evaluateFaceCount(2))
    }

    @Test
    fun isTooDark_belowThreshold() {
        assertTrue(FaceQualityRules.isTooDark(0.10f))
        assertFalse(FaceQualityRules.isTooDark(0.20f))
    }

    @Test
    fun isTooBlurry_belowThreshold() {
        assertTrue(FaceQualityRules.isTooBlurry(10.0))
        assertFalse(FaceQualityRules.isTooBlurry(100.0))
    }

    @Test
    fun isFaceTooSmall_ratioBoundary() {
        assertTrue(FaceQualityRules.isFaceTooSmall(faceWidth = 90, imageWidth = 1000))
        assertFalse(FaceQualityRules.isFaceTooSmall(faceWidth = 100, imageWidth = 1000))
        assertTrue(FaceQualityRules.isFaceTooSmall(faceWidth = 50, imageWidth = 0))
    }

    @Test
    fun averageLuminance_allWhiteIsOne() {
        assertEquals(1.0f, FaceQualityRules.averageLuminance(IntArray(16) { 255 }), 1e-4f)
    }

    @Test
    fun averageLuminance_allBlackIsZero() {
        assertEquals(0.0f, FaceQualityRules.averageLuminance(IntArray(16) { 0 }), 1e-4f)
    }

    @Test
    fun laplacianVariance_flatImageIsZero() {
        val gray = IntArray(5 * 5) { 128 }
        assertEquals(0.0, FaceQualityRules.laplacianVariance(gray, 5, 5), 1e-9)
    }
}
