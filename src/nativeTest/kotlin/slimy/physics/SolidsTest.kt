package slimy.physics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SolidsTest {
    private val body = SoftBody.semiDisc(SoftBody.RADIUS, SoftBody.ARC_SEGMENTS, SoftBody.BASE_SEGMENTS)
    private val contacts = Contacts(body.size, body.ringCount)

    /** Puts particle 0 at ([x], [y]) having come from ([fromX], [fromY]), and every other particle far away. */
    private fun particle(x: Float, y: Float, fromX: Float = x, fromY: Float = y) {
        for (i in 0 until body.size) body.setParticle(i, -1000f, -1000f)
        body.setParticle(0, x, y, previousX = fromX, previousY = fromY)
    }

    private fun resolve(grid: SolidGrid) {
        contacts.beginStep()
        grid.resolve(body, contacts)
    }

    /** A 10 x 10 grid with a floor of full tiles on row 5, whose top is y = 80. */
    private fun floorGrid(): SolidGrid = TestScenes.flatFloor(10, 10, firstRow = 5, thickness = 1)

    @Test
    fun aParticleFallingIntoTheFloorLandsOnTopKeepingItsSidewaysSpeed() {
        val grid = floorGrid()
        particle(40f, 84f, fromX = 35f, fromY = 70f)
        resolve(grid)
        assertEquals(80f, body.positionY(0), 0.001f)
        assertEquals(40f, body.positionX(0), 0.001f)
        assertEquals(0f, body.movedY(0), 0.001f)
        assertEquals(5f, body.movedX(0), 0.001f)
        assertTrue(contacts.isOnFloor(0))
        assertTrue(contacts.touchedFloor)
    }

    @Test
    fun aParticleHittingTheSideOfABlockIsPushedSidewaysAndIsNotOnTheFloor() {
        val grid = SolidGrid(10, 10).apply { add(Solid.tile(5, 3)) }
        particle(82f, 56f, fromX = 78f, fromY = 56f)
        resolve(grid)
        assertEquals(80f, body.positionX(0), 0.001f)
        assertEquals(56f, body.positionY(0), 0.001f)
        assertFalse(contacts.isOnFloor(0))
    }

    @Test
    fun aParticleThatStartedInsideLeavesThroughTheNearestFace() {
        val grid = SolidGrid(10, 10).apply { add(Solid.tile(5, 3)) }
        particle(94f, 56f)
        resolve(grid)
        assertEquals(96f, body.positionX(0), 0.001f)
    }

    @Test
    fun aParticleOutsideEverythingIsLeftAlone() {
        val grid = floorGrid()
        particle(40f, 60f, fromX = 41f, fromY = 59f)
        resolve(grid)
        assertEquals(40f, body.positionX(0))
        assertEquals(60f, body.positionY(0))
        assertFalse(contacts.touchedFloor)
    }

    @Test
    fun theSeamBetweenTwoFloorTilesDoesNotPushSideways() {
        val grid = floorGrid()
        particle(95.5f, 82f)
        resolve(grid)
        assertEquals(95.5f, body.positionX(0), 0.001f)
        assertEquals(80f, body.positionY(0), 0.001f)
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
        assertEquals(40f, body.positionX(0), 0.001f)
        assertEquals(40f, body.positionY(0), 0.001f)
        assertTrue(contacts.isOnFloor(0))
    }

    @Test
    fun aFallingSlopeMirrorsIt() {
        val grid = SolidGrid(10, 10).apply { add(Solid.tile(2, 2, SolidShape.FALLING)) }
        particle(36f, 44f)
        resolve(grid)
        assertEquals(40f, body.positionX(0), 0.001f)
        assertEquals(40f, body.positionY(0), 0.001f)
    }

    @Test
    fun aHalfTileStubOnlyFillsTheTopHalfOfItsTile() {
        val grid = SolidGrid(10, 10).apply { add(Solid.stub(4, 4)) }
        particle(70f, 74f)
        resolve(grid)
        // y 64..72 is solid; 74 is below the stub, in the open half of the tile.
        assertEquals(74f, body.positionY(0))
        particle(70f, 70f, fromY = 75f)
        resolve(grid)
        assertEquals(72f, body.positionY(0), 0.001f)
    }

    @Test
    fun aMovingSolidPushesToo() {
        val grid = SolidGrid(10, 10)
        grid.dynamic.add(Solid.moving(left = 20f, top = 20f, right = 52f, bottom = 52f))
        particle(36f, 50f)
        resolve(grid)
        assertEquals(52f, body.positionY(0), 0.001f)
    }

    @Test
    fun aBoxBesideTheTallEdgeOfARisingSlopeCannotBeLeftThroughThatFace() {
        val grid = SolidGrid(10, 10).apply { add(Solid.tile(4, 5, SolidShape.RISING)); add(Solid.tile(5, 5)) }
        // inside the box, half a unit from the face it shares with the slope's full-height edge
        particle(80.5f, 82f)
        resolve(grid)
        assertEquals(80.5f, body.positionX(0), 0.001f)
        assertEquals(80f, body.positionY(0), 0.001f)
    }

    @Test
    fun aBoxBesideTheLowEdgeOfAFallingSlopeCanBeLeftThroughThatFace() {
        val grid = SolidGrid(10, 10).apply { add(Solid.tile(4, 5, SolidShape.FALLING)); add(Solid.tile(5, 5)) }
        // a falling slope meets the box only at its bottom corner, so that face is open air above it
        particle(80.5f, 82f)
        resolve(grid)
        assertEquals(80f, body.positionX(0), 0.001f)
        assertEquals(82f, body.positionY(0), 0.001f)
    }

    @Test
    fun aMovingSolidIsNotATileAndATileIsNotMoving() {
        assertTrue(Solid.moving(0f, 0f, 10f, 10f).isMoving)
        assertFalse(Solid.tile(2, 3).isMoving)
        assertFalse(Solid.stub(2, 3).isMoving)
    }

    @Test
    fun aPointIsInsideASolidOnlyPastItsSurfaceByMoreThanTheMargin() {
        val box = SolidGrid(10, 10).apply { add(Solid.tile(2, 2)) }
        assertTrue(box.isSolidAt(40f, 40f))
        assertFalse(box.isSolidAt(32f, 40f), "a point on the surface is outside")
        assertFalse(box.isSolidAt(10f, 40f))
        assertFalse(box.isSolidAt(33f, 40f, margin = 2f), "inside by 1 is not inside by more than 2")
        assertTrue(box.isSolidAt(36f, 40f, margin = 2f))
    }

    @Test
    fun aStubIsOnlySolidInItsTopHalfAndASlopeOnlyBelowItsSurface() {
        val stub = SolidGrid(10, 10).apply { add(Solid.stub(4, 4)) }
        assertTrue(stub.isSolidAt(70f, 68f))
        assertFalse(stub.isSolidAt(70f, 76f))
        val rising = SolidGrid(10, 10).apply { add(Solid.tile(2, 2, SolidShape.RISING)) }
        assertTrue(rising.isSolidAt(44f, 44f), "below the rising surface")
        assertFalse(rising.isSolidAt(36f, 36f), "above it")
        val falling = SolidGrid(10, 10).apply { add(Solid.tile(2, 2, SolidShape.FALLING)) }
        assertTrue(falling.isSolidAt(36f, 44f), "below the falling surface")
        assertFalse(falling.isSolidAt(44f, 36f), "above it")
    }

    @Test
    fun aMovingSolidIsFoundToo() {
        val grid = SolidGrid(10, 10)
        grid.dynamic.add(Solid.moving(20f, 20f, 52f, 52f))
        assertTrue(grid.isSolidAt(36f, 36f))
        assertFalse(grid.isSolidAt(60f, 36f))
    }
}
