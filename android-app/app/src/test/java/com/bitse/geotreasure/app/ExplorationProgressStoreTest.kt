package com.bitse.geotreasure.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ExplorationProgressStoreTest {
    @Test
    fun `first completion adds one landmark`() {
        val result = mergeCompletedLandmark(emptySet(), "lmk_north_zongjiao")
        assertEquals(setOf("lmk_north_zongjiao"), result)
    }

    @Test
    fun `repeated completion does not duplicate`() {
        val once = mergeCompletedLandmark(emptySet(), "lmk_north_zongjiao")
        val twice = mergeCompletedLandmark(once, "lmk_north_zongjiao")
        assertEquals(setOf("lmk_north_zongjiao"), twice)
    }

    @Test
    fun `distinct landmarks accumulate`() {
        val result = mergeCompletedLandmark(
            mergeCompletedLandmark(emptySet(), "lmk_north_zongjiao"),
            "lmk_east_library",
        )
        assertEquals(setOf("lmk_north_zongjiao", "lmk_east_library"), result)
    }
}
