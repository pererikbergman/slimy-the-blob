package slimy.physics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SolidsTest {
    private val body = SoftBody.semiDisc(Tuning.RADIUS, Tuning.ARC_SEGMENTS, Tuning.BASE_SEGMENTS)
    private val contacts = Contacts(body.size, body.ringCount)

    /** Puts particle 0 at ([x], [y]) having come from ([fromX], [fromY]), and every other particle far away. */
    private fun particle(x: Float, y: Float, fromX: Float = x, fromY: Float = y) {
        for (i in 0 until body.size) {
            body.x[i] = -1000f
            body.y[i] = -1000f
            body.prevX[i] = -1000f
            body.prevY[i] = -1000f
        }
        body.x[0] = x
        body.y[0] = y
        body.prevX[0] = fromX
        body.prevY[0] = fromY
    }

    private fun resolve(grid: SolidGrid) {
        contacts.beginStep()
        grid.resolve(body, contacts)
    }

    /** A 10 x 10 grid with a floor of full tiles on row 5, whose top is y = 80. */
    private fun floorGrid(): SolidGrid = SolidGrid(10, 10).apply { for (c in 0 until 10) add(Solid.tile(c, 5)) }

    @Test
    fun aParticleFallingIntoTheFloorLandsOnTopKeepingItsSidewaysSpeed() {
        val grid = floorGrid()
        particle(40f, 84f, fromX = 35f, fromY = 70f)
        resolve(grid)
        assertEquals(80f, body.y[0], 0.001f)
        assertEquals(40f, body.x[0], 0.001f)
        assertEquals(0f, body.y[0] - body.prevY[0], 0.001f)
        assertEquals(5f, body.x[0] - body.prevX[0], 0.001f)
        assertTrue(contacts.isOnFloor(0))
        assertTrue(contacts.touchedFloor)
    }

    @Test
    fun aParticleHittingTheSideOfABlockIsPushedSidewaysAndIsNotOnTheFloor() {
        val grid = SolidGrid(10, 10).apply { add(Solid.tile(5, 3)) }
        particle(82f, 56f, fromX = 78f, fromY = 56f)
        resolve(grid)
        assertEquals(80f, body.x[0], 0.001f)
        assertEquals(56f, body.y[0], 0.001f)
        assertFalse(contacts.isOnFloor(0))
    }

    @Test
    fun aParticleThatStartedInsideLeavesThroughTheNearestFace() {
        val grid = SolidGrid(10, 10).apply { add(Solid.tile(5, 3)) }
        particle(94f, 56f)
        resolve(grid)
        assertEquals(96f, body.x[0], 0.001f)
    }

    @Test
    fun aParticleOutsideEverythingIsLeftAlone() {
        val grid = floorGrid()
        particle(40f, 60f, fromX = 41f, fromY = 59f)
        resolve(grid)
        assertEquals(40f, body.x[0])
        assertEquals(60f, body.y[0])
        assertFalse(contacts.touchedFloor)
    }

    @Test
    fun theSeamBetweenTwoFloorTilesDoesNotPushSideways() {
        val grid = floorGrid()
        particle(95.5f, 82f)
        resolve(grid)
        assertEquals(95.5f, body.x[0], 0.001f)
        assertEquals(80f, body.y[0], 0.001f)
    }

    @Test
    fun aBouncyTileReportsABounceWhenLandedOn() {
        val grid = SolidGrid(10, 10).apply { add(Solid.tile(4, 5, isBouncy = true)) }
        particle(72f, 82f, fromY = 70f)
        resolve(grid)
        assertTrue(contacts.touchedBouncePad)
        assertTrue(contacts.touchedFloor)
    }

    @Test
    fun aRisingSlopePushesAlongItsSurfaceNormal() {
        val grid = SolidGrid(10, 10).apply { add(Solid.tile(2, 2, SolidShape.RISING)) }
        particle(44f, 44f)
        resolve(grid)
        assertEquals(40f, body.x[0], 0.001f)
        assertEquals(40f, body.y[0], 0.001f)
        assertTrue(contacts.isOnFloor(0))
    }

    @Test
    fun aFallingSlopeMirrorsIt() {
        val grid = SolidGrid(10, 10).apply { add(Solid.tile(2, 2, SolidShape.FALLING)) }
        particle(36f, 44f)
        resolve(grid)
        assertEquals(40f, body.x[0], 0.001f)
        assertEquals(40f, body.y[0], 0.001f)
    }

    @Test
    fun aHalfTileStubOnlyFillsTheTopHalfOfItsTile() {
        val grid = SolidGrid(10, 10).apply { add(Solid.stub(4, 4)) }
        particle(70f, 74f)
        resolve(grid)
        // y 64..72 is solid; 74 is below the stub, in the open half of the tile.
        assertEquals(74f, body.y[0])
        particle(70f, 70f, fromY = 75f)
        resolve(grid)
        assertEquals(72f, body.y[0], 0.001f)
    }

    @Test
    fun aMovingSolidPushesToo() {
        val grid = SolidGrid(10, 10)
        grid.dynamic.add(Solid(SolidShape.BOX, left = 20f, top = 20f, right = 52f, bottom = 52f))
        particle(36f, 50f)
        resolve(grid)
        assertEquals(52f, body.y[0], 0.001f)
    }
}
