package slimy.physics

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** The acceptance criteria of the PRD, as tests on small hand-built levels. */
class AcceptanceTest {
    private val dt = 1f / 60f
    private val tile = SolidGrid.TILE
    private val restWidth = 2 * SoftBody.RADIUS
    private val restArea = SoftBody.semiDisc(SoftBody.RADIUS, SoftBody.ARC_SEGMENTS, SoftBody.BASE_SEGMENTS).area()

    private fun Slime.run(steps: Int, move: Float = 0f) = repeat(steps) {
        step(SlimeInput(move = move), dt)
        assertTrue(body.isFinite(), "the body blew up")
    }

    private fun assertBackToRestShape(slime: Slime) {
        val width = slime.body.maxX() - slime.body.minX()
        assertTrue(abs(width - restWidth) < restWidth * 0.15f, "width $width of $restWidth")
        assertTrue(abs(slime.body.area() - restArea) < restArea * 0.15f, "area ${slime.body.area()} of $restArea")
    }

    /** A big solid block with a one-tile-wide vertical slot (col 11) from row 5 down to [slotBottom] and a chamber under it. */
    private fun shaftLevel(slotBottom: Int, chamberRows: IntRange?): SolidGrid {
        val grid = TestScenes.solidEverywhere(30, 45)
        grid.carve(1..28, 0..4)
        grid.carve(11..11, 5..slotBottom)
        if (chamberRows != null) grid.carve(1..28, chamberRows)
        return grid
    }

    @Test
    fun aDeadEndOneTileShaftDoesNotMakeTheBodyBlowUpOrTear() {
        val grid = shaftLevel(slotBottom = 12, chamberRows = null)
        val slime = Slime(grid, startX = 11.5f * tile, startBaseY = 5 * tile - 30f)
        var worstStretch = 0f
        repeat(1200) {
            slime.step(SlimeInput(), dt)
            assertTrue(slime.body.isFinite(), "blew up")
            worstStretch = maxOf(worstStretch, slime.body.maxEdgeStretch())
        }
        assertTrue(worstStretch < 3f, "an edge stretched to $worstStretch times its length")
        assertTrue(slime.body.minX() >= 11 * tile - 0.5f && slime.body.maxX() <= 12 * tile + 0.5f || slime.body.minY() < 5 * tile,
            "the slime is neither in the shaft nor on top of it")
    }

    @Test
    fun theSlimeFallsThroughAShaftSixtyPercentOfItsWidthAndRecovers() {
        val grid = shaftLevel(slotBottom = 22, chamberRows = 23..40)
        val slime = Slime(grid, startX = 11.5f * tile, startBaseY = 5 * tile - 30f)
        slime.run(360)
        assertTrue(slime.body.centerY() > 23 * tile, "still above the chamber at y ${slime.body.centerY()}")
        slime.run(300)
        assertBackToRestShape(slime)
    }

    @Test
    fun theSlimePushesThroughALowTunnelAndRecovers() {
        // A floor at row 16 (top 256), a tunnel from col 10 to 15 whose ceiling leaves 8 units: 60% of the slime's height.
        val grid = TestScenes.flatFloor(40, 20, firstRow = 16)
        for (c in 10..15) { grid.add(Solid.tile(c, 14)); grid.add(Solid.stub(c, 15)) }
        for (r in 0..15) { grid.add(Solid.tile(0, r)); grid.add(Solid.tile(39, r)) }
        val slime = Slime(grid, startX = 6 * tile, startBaseY = 16 * tile)
        slime.run(60)
        var pushed = 0
        while (slime.body.centerX() < 22 * tile && pushed < 900) {
            slime.run(1, move = 1f)
            pushed++
        }
        assertTrue(slime.body.centerX() >= 22 * tile, "stuck at x ${slime.body.centerX()} after $pushed steps")
        slime.run(200)
        assertBackToRestShape(slime)
    }

    @Test
    fun aLongFallOntoAFloorNeverLeavesAParticleInsideIt() {
        val grid = TestScenes.flatFloor(40, 50, firstRow = 40)
        val slime = Slime(grid, startX = 320f, startBaseY = 40 * tile - 430f)
        repeat(240) {
            slime.step(SlimeInput(), dt)
            assertParticlesOutside(slime, grid)
        }
    }

    @Test
    fun landingOnTheCornerOfABlockNeverLeavesAParticleInsideIt() {
        val grid = TestScenes.flatFloor(40, 50, firstRow = 40)
        for (c in 20..25) for (r in 37..39) grid.add(Solid.tile(c, r))
        // Centred on the block's left edge, so half the slime lands on it and half beside it.
        val slime = Slime(grid, startX = 20 * tile, startBaseY = 30 * tile - 300f)
        repeat(300) {
            slime.step(SlimeInput(), dt)
            assertParticlesOutside(slime, grid)
        }
    }

    private fun assertParticlesOutside(slime: Slime, grid: SolidGrid) {
        for (i in 0 until slime.body.size) {
            val x = slime.body.x[i]
            val y = slime.body.y[i]
            assertTrue(!grid.isSolidAt(x, y, margin = 0.05f), "particle $i is inside a solid at ($x, $y)")
        }
    }
}
