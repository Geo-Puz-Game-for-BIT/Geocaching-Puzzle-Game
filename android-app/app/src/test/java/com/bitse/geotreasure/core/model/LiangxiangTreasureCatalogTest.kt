package com.bitse.geotreasure.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiangxiangTreasureCatalogTest {
    @Test
    fun `catalog contains four landmarks for each campus zone`() {
        val grouped = LiangxiangTreasureCatalog.landmarks.groupBy { it.campusZone }

        assertEquals(CampusZone.entries.toSet(), grouped.keys)
        grouped.values.forEach { landmarks -> assertEquals(4, landmarks.size) }
        assertEquals(12, LiangxiangTreasureCatalog.landmarks.size)
    }

    @Test
    fun `each section is a complete two by two puzzle`() {
        val puzzle = LiangxiangTreasureCatalog.puzzle

        assertEquals(12, puzzle.fragmentCount)
        assertEquals(3, puzzle.sections.size)
        puzzle.sections.forEach { section ->
            assertEquals(2, section.rows)
            assertEquals(2, section.columns)
            assertEquals(setOf(0, 1, 2, 3), section.fragments.map { it.pieceIndex }.toSet())
        }
    }

    @Test
    fun `fragments map one to one to landmarks`() {
        val landmarks = LiangxiangTreasureCatalog.landmarks
        val fragments = LiangxiangTreasureCatalog.puzzle.sections.flatMap { it.fragments }

        assertEquals(landmarks.map { it.id }.toSet(), fragments.map { it.landmarkId }.toSet())
        assertEquals(landmarks.map { it.fragmentId }.toSet(), fragments.map { it.id }.toSet())
        assertEquals(fragments.size, fragments.map { it.id }.toSet().size)
    }

    @Test
    fun `development coordinates remain visibly unverified`() {
        assertTrue(LiangxiangTreasureCatalog.landmarks.isNotEmpty())
        assertFalse(LiangxiangTreasureCatalog.landmarks.any { it.coordinateVerified })
    }

    @Test
    fun `catalog demonstrates adjustable circle and polygon check in areas`() {
        val areas = LiangxiangTreasureCatalog.landmarks.map { it.checkInArea }

        assertTrue(areas.any { it is CircularCheckInArea })
        assertTrue(areas.any { it is PolygonCheckInArea })
        areas.filterIsInstance<PolygonCheckInArea>().forEach { area ->
            assertTrue(area.vertices.size >= 3)
        }
    }
}
