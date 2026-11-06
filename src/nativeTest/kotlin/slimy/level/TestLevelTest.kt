package slimy.level

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestLevelTest {
    private val level = TestLevel.level

    @Test
    fun theLevelIs240TilesWideAnd24Tall() {
        assertEquals(240, level.cols)
        assertEquals(24, level.rows)
        assertTrue(level.tiles.size > 1000, "only ${level.tiles.size} tiles")
    }

    @Test
    fun theFourZonesRunLeftToRight() {
        assertEquals(listOf('A', 'B', 'C', 'D'), level.zones.map { it.id })
        assertEquals(level.zones.sortedBy { it.x }, level.zones)
    }

    @Test
    fun thereIsAStartInZoneAAndAFinishInZoneD() {
        val start = assertNotNull(level.start)
        val finish = assertNotNull(level.finish)
        assertEquals('A', level.zoneAt(start.x)!!.id)
        assertEquals('D', level.zoneAt(finish.x)!!.id)
    }

    @Test
    fun theStartStandsOnSolidGroundInOpenAir() {
        val start = assertNotNull(level.start)
        val col = (start.x / 16f).toInt()
        val row = (start.y / 16f).toInt()
        assertNull(level.grid.at(col, row - 1))
        assertNotNull(level.grid.at(col, row))
    }

    @Test
    fun theShaftThroughTheDeckIsOneTileWide() {
        assertNotNull(level.grid.at(145, 16))
        assertNull(level.grid.at(146, 16))
        assertNull(level.grid.at(146, 17))
        assertNotNull(level.grid.at(147, 16))
    }

    @Test
    fun theLowestTunnelLeavesEightUnitsOfClearance() {
        val stub = assertNotNull(level.grid.at(133, 19))
        val floor = assertNotNull(level.grid.at(133, 20))
        assertEquals(8f, floor.top - stub.bottom, 0.001f)
    }

    @Test
    fun aCurtainOfRedSlimeHangsUnderTheLedgeAfterThePads() {
        val curtain = level.curtains.single()
        val ledge = assertNotNull(level.grid.at((curtain.left / 16f).toInt(), 14))
        assertEquals(curtain.top, ledge.bottom)
        assertTrue(curtain.left > 193 * 16f)
    }

    @Test
    fun theKineticGauntletHasPadsAndACrusher() {
        val pads = level.tiles.count { it.tile == Tiles.PAD }
        assertEquals(2, pads)
        assertEquals(1, level.crushers.size)
        assertTrue(level.tiles.count { it.tile == Tiles.SLOPE_UP } >= 6)
        assertTrue(level.tiles.count { it.tile == Tiles.SLOPE_DOWN } >= 6)
    }
}
