package com.bitse.geotreasure.core.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoCompressorTest {

    @Test
    fun calcInSampleSize_smallImage_keepsFullResolution() {
        assertEquals(1, PhotoCompressor.calcInSampleSize(width = 1000, height = 800, maxDimension = 2048))
    }

    @Test
    fun calcInSampleSize_typicalCameraImage_returnsMinimalSampling() {
        // 4000/2 = 2000 < 2048，不再降采样，保清晰优先
        assertEquals(1, PhotoCompressor.calcInSampleSize(width = 4000, height = 3000, maxDimension = 2048))
    }

    @Test
    fun calcInSampleSize_largeImage_halvesSampling() {
        // 5120/2 = 2560 >= 2048，继续降；5120/4 = 1280 < 2048，停止
        assertEquals(2, PhotoCompressor.calcInSampleSize(width = 5120, height = 3840, maxDimension = 2048))
    }

    @Test
    fun calcInSampleSize_veryLargeImage_stepsDownByPowersOfTwo() {
        val sample = PhotoCompressor.calcInSampleSize(width = 16384, height = 12288, maxDimension = 2048)
        // 结果最长边必须仍 >= maxDimension/2，且 sample 为 2 的幂
        assertTrue(sample in setOf(4, 8))
        assertTrue(16384 / sample >= 2048 / 2)
    }

    @Test
    fun qualitySequence_descendsAndStaysInValidRange() {
        val qualities = PhotoCompressor.qualitySequence()
        assertTrue(qualities.isNotEmpty())
        assertTrue(qualities.zipWithNext().all { (current, next) -> current > next })
        assertTrue(qualities.all { it in 1..100 })
    }
}
