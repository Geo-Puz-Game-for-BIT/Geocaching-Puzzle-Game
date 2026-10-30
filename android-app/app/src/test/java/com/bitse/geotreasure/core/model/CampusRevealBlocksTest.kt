package com.bitse.geotreasure.core.model

import com.bitse.geotreasure.core.location.isWithinCheckInArea
import com.bitse.geotreasure.core.location.localOffsetMeters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusRevealBlocksTest {
    @Test
    fun `each campus has four blocks linked to its puzzle pieces`() {
        val blocks = campusRevealBlocks(LiangxiangTreasureCatalog.landmarks)
        val byZone = blocks.groupBy { it.campusZone }

        assertEquals(12, blocks.size)
        CampusZone.entries.forEach { zone ->
            assertEquals(4, byZone.getValue(zone).size)
            assertEquals(setOf(0, 1, 2, 3), byZone.getValue(zone).map { it.pieceIndex }.toSet())
        }
        assertEquals(LiangxiangTreasureCatalog.landmarks.map { it.id }.toSet(), blocks.map { it.landmarkId }.toSet())
    }

    @Test
    fun `every landmark lies inside its completed reveal block`() {
        val landmarks = LiangxiangTreasureCatalog.landmarks
        val blocks = campusRevealBlocks(landmarks)

        landmarks.forEach { landmark ->
            val block = blocks.single { it.landmarkId == landmark.id }
            assertTrue(block.vertices.size >= 3)
            val area = PolygonCheckInArea(block.vertices.map { localOffsetMeters(landmark.point, it) })
            assertTrue(isWithinCheckInArea(landmark.point, landmark.point, area, accuracyMeters = 0f))
            val containingBlocks = blocks.filter { candidate ->
                if (candidate.campusZone != landmark.campusZone) return@filter false
                val candidateArea = PolygonCheckInArea(
                    candidate.vertices.map { localOffsetMeters(landmark.point, it) },
                )
                isWithinCheckInArea(landmark.point, landmark.point, candidateArea, accuracyMeters = 0f)
            }
            assertEquals(listOf(block), containingBlocks)
        }
    }
}
