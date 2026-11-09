package slimy.level

import klyph.physics.SolidShape
import slimy.physics.Slime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LevelMapTest {
    @Test
    fun aMapIsRowsOfEqualLengthThatFillAndClearReshape() {
        val map = LevelMap(6, 4)
        map.fill(0, 3, 5, 3, '#')
        map.put(2, 2, 'J')
        map.clear(4, 3, 5, 3)
        assertEquals(listOf("......", "......", "..J...", "####.."), map.rows())
    }

    @Test
    fun drawingOutsideTheMapIsAMistakeNotASilentNoOp() {
        val map = LevelMap(3, 3)
        assertFailsWith<IllegalArgumentException> { map.put(3, 0, '#') }
    }
}

class LevelTest {
    private fun tileIndex(level: Level, col: Int, row: Int): Int? = level.tiles.firstOrNull { it.col == col && it.row == row }?.tile

    @Test
    fun eachTileCharacterBecomesTheRightSolid() {
        val level = Level.parse(listOf("#v/\\J"))
        assertEquals(SolidShape.BOX, level.grid.at(0, 0)!!.shape)
        val stub = level.grid.at(1, 0)!!
        assertEquals(8f, stub.bottom - stub.top, 0.001f)
        assertEquals(SolidShape.RISING, level.grid.at(2, 0)!!.shape)
        assertEquals(SolidShape.FALLING, level.grid.at(3, 0)!!.shape)
        assertTrue(level.grid.at(4, 0)!!.tag == Slime.BOUNCE_PAD)
    }

    @Test
    fun theTopOfASolidMassIsGrassAndTheRestIsDirt() {
        val level = Level.parse(listOf("#", "#"))
        assertEquals(Tiles.GRASS, tileIndex(level, 0, 0))
        assertEquals(Tiles.DIRT, tileIndex(level, 0, 1))
    }

    @Test
    fun otherCharactersPickTheirOwnTileArt() {
        val level = Level.parse(listOf("v/\\J"))
        assertEquals(Tiles.STUB, tileIndex(level, 0, 0))
        assertEquals(Tiles.SLOPE_UP, tileIndex(level, 1, 0))
        assertEquals(Tiles.SLOPE_DOWN, tileIndex(level, 2, 0))
        assertEquals(Tiles.PAD, tileIndex(level, 3, 0))
    }

    @Test
    fun startFinishAndZoneSignsStandOnTheBottomOfTheirCell() {
        val level = Level.parse(listOf("S.1F"))
        assertEquals(Spot(8f, 16f), level.start)
        assertEquals(Spot(56f, 16f), level.finish)
        val zone = assertNotNull(level.zone('A'))
        assertEquals(40f, zone.x)
        assertEquals(16f, zone.y)
        assertEquals("The Calibration Chamber", zone.name)
    }

    @Test
    fun aLevelWithoutStartOrFinishSaysSo() {
        val level = Level.parse(listOf("##"))
        assertNull(level.start)
        assertNull(level.finish)
    }

    @Test
    fun theZoneAtAPlaceIsTheLastSignToItsLeft() {
        val level = Level.parse(listOf("1...2...3"))
        assertEquals('A', level.zoneAt(8f)!!.id)
        assertEquals('A', level.zoneAt(71f)!!.id)
        assertEquals('B', level.zoneAt(80f)!!.id)
        assertEquals('C', level.zoneAt(1000f)!!.id)
    }

    @Test
    fun aCrusherSwingsDownToEightUnitsAboveTheFloorAndBackUp() {
        val level = Level.parse(listOf("M.", "..", "..", "##"))
        val crusher = level.crushers.single()
        val floorTop = 3 * 16f
        assertEquals(0f, crusher.solid.top, 0.001f)
        assertEquals(32f, crusher.solid.bottom - crusher.solid.top, 0.001f)
        assertTrue(crusher.solid in level.grid.dynamic)
        level.advance(Crusher.PERIOD / 2f)
        assertEquals(floorTop - 8f, crusher.solid.bottom, 0.01f)
        level.advance(Crusher.PERIOD / 2f)
        assertEquals(0f, crusher.solid.top, 0.01f)
    }

    @Test
    fun aRunOfHangingMarksIsOneCurtainHangingFromTheTopOfItsRow() {
        val level = Level.parse(listOf("####..", "..rrr.", "......"))
        val curtain = level.curtains.single()
        assertEquals(32f, curtain.left)
        assertEquals(80f, curtain.right)
        assertEquals(16f, curtain.top)
    }

    @Test
    fun separateRunsAreSeparateCurtains() {
        val level = Level.parse(listOf("r.rr"))
        assertEquals(2, level.curtains.size)
    }

    @Test
    fun everyDigitIsEitherANamedZoneSignOrAnUnknownTileAndNeverACrash() {
        for (digit in '1'..'9') {
            val result = runCatching { Level.parse(listOf("$digit")) }
            val level = result.getOrNull()
            if (level != null) {
                assertTrue(level.zones.single().name.isNotBlank(), "zone sign $digit has no name")
            } else {
                assertTrue(result.exceptionOrNull() is IllegalArgumentException, "sign $digit failed with ${result.exceptionOrNull()}")
            }
        }
    }

    @Test
    fun zonesAreFoundByTheirNumberAndAnUnknownNumberHasNoZone() {
        val level = Level.parse(listOf("1234"))
        assertEquals('A', level.zoneByNumber(1)!!.id)
        assertEquals('D', level.zoneByNumber(4)!!.id)
        assertNull(level.zoneByNumber(5))
        assertNull(level.zoneByNumber(0))
    }

    @Test
    fun aCrusherLooksPastOtherOpenMapCharactersForItsFloor() {
        val level = Level.parse(listOf("M.", "..", "S.", "##"))
        val crusher = level.crushers.single()
        assertTrue(crusher.solid.isMoving)
        level.advance(Crusher.PERIOD / 2f)
        assertEquals(3 * 16f - Crusher.GAP, crusher.solid.bottom, 0.01f)
    }

    @Test
    fun aCrusherWithNoFloorBelowItIsAMistake() {
        assertFailsWith<IllegalArgumentException> { Level.parse(listOf("M.", "..", "..")) }
    }

    @Test
    fun raggedRowsAndUnknownCharactersAreRejected() {
        assertFailsWith<IllegalArgumentException> { Level.parse(listOf("##", "#")) }
        assertFailsWith<IllegalArgumentException> { Level.parse(listOf("#?")) }
    }
}
