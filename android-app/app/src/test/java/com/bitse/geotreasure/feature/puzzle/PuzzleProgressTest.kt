package com.bitse.geotreasure.feature.puzzle

import com.bitse.geotreasure.core.model.LiangxiangTreasureCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleProgressTest {
    @Test
    fun `未打卡不点亮任何碎片，含曾经的演示碎片也不预置`() {
        val initial = ownedFragmentIdsFor(emptySet())

        assertTrue("未打卡应无碎片点亮", initial.isEmpty())
        // 真实模式移除演示预置：北湖/图书馆/文萃三个历史演示碎片不得默认点亮
        assertFalse("fragment_north_lake" in initial)
        assertFalse("fragment_north_library" in initial)
        assertFalse("fragment_east_wencui" in initial)
    }

    @Test
    fun `打卡成功点亮对应碎片`() {
        val completed = ownedFragmentIdsFor(setOf("lmk_north_zongjiao"))

        assertEquals(setOf("fragment_north_zongjiao"), completed)
    }

    @Test
    fun `未知或未完成地标不产生碎片（失败或 UNKNOWN 不提前点亮）`() {
        // 只有已确认完成的地标才映射碎片；unknown 与未完成地标一律忽略
        val completed = ownedFragmentIdsFor(setOf("lmk_north_lake", "unknown"))

        assertEquals(setOf("fragment_north_lake"), completed)
    }

    @Test
    fun `重复完成不增加碎片（幂等）`() {
        // completedLandmarkIds 为集合，重复地标天然去重；映射后仍为单一碎片
        val completed = ownedFragmentIdsFor(setOf("lmk_north_lake", "lmk_north_lake"))

        assertEquals(setOf("fragment_north_lake"), completed)
    }

    @Test
    fun `全部地标完成后十二块碎片全部点亮`() {
        val allLandmarkIds = LiangxiangTreasureCatalog.landmarks.map { it.id }.toSet()
        val allFragmentIds = LiangxiangTreasureCatalog.landmarks.map { it.fragmentId }.toSet()

        val completed = ownedFragmentIdsFor(allLandmarkIds)

        assertEquals(allFragmentIds.size, completed.size)
        assertEquals(allFragmentIds, completed)
    }
}
