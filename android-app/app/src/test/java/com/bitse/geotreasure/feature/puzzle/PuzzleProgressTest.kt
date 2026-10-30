package com.bitse.geotreasure.feature.puzzle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleProgressTest {
    @Test
    fun `new landmark adds its fragment to the three demo fragments`() {
        val initial = ownedFragmentIdsFor(emptySet())
        val completed = ownedFragmentIdsFor(setOf("lmk_north_zongjiao"))

        assertEquals(3, initial.size)
        assertEquals(4, completed.size)
        assertTrue("fragment_north_zongjiao" in completed)
    }

    @Test
    fun `repeated or unknown completion does not add a fragment`() {
        assertEquals(3, ownedFragmentIdsFor(setOf("lmk_north_lake", "unknown")).size)
    }
}
