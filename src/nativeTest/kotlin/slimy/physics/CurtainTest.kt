package slimy.physics

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class CurtainTest {
    private val dt = 1f / 60f
    private val top = 100f

    /** Three tiles wide, so four strands, hanging from y = 100. */
    private fun curtain() = Curtain(left = 0f, right = 3 * Tuning.TILE, top = top)

    private fun body(centerX: Float, baseY: Float) = SoftBody.semiDisc(Tuning.RADIUS, Tuning.ARC_SEGMENTS, Tuning.BASE_SEGMENTS).also { it.placeAt(centerX, baseY) }

    private fun Curtain.restX(c: Int) = left + c * Tuning.TILE

    private fun Curtain.farthestFromRest(): Float {
        var worst = 0f
        for (r in 0 until rows) for (c in 0 until columns) worst = maxOf(worst, abs(x[r * columns + c] - restX(c)))
        return worst
    }

    @Test
    fun aCurtainHangsStraightDownFromItsAnchorsAndStaysThere() {
        val curtain = curtain()
        repeat(300) { curtain.step(null, dt) }
        val length = (curtain.rows - 1) * Tuning.CURTAIN_SEGMENT
        for (c in 0 until curtain.columns) {
            assertTrue(abs(curtain.y[c] - top) < 0.001f, "anchor $c moved")
            val tip = curtain.y[(curtain.rows - 1) * curtain.columns + c]
            assertTrue(abs(tip - (top + length)) < 3f, "strand $c ends at $tip, not ${top + length}")
        }
        assertTrue(curtain.farthestFromRest() < 0.5f)
        assertTrue(curtain.isFinite())
    }

    @Test
    fun aSlimeInTheWayPushesTheCurtainOutOfItsBody() {
        val curtain = curtain()
        repeat(60) { curtain.step(null, dt) }
        val tipRow = curtain.rows - 1
        // Centred on the second strand with its dome up through the strand's lower half.
        val slime = body(centerX = Tuning.TILE, baseY = top + tipRow * Tuning.CURTAIN_SEGMENT + 2f)
        repeat(30) { curtain.step(slime, dt) }
        for (i in curtain.columns until curtain.count) assertTrue(!Outline.contains(slime, curtain.x[i], curtain.y[i]), "particle $i is inside the slime")
        assertTrue(curtain.farthestFromRest() > 3f, "nothing moved, farthest ${curtain.farthestFromRest()}")
        assertTrue(curtain.isFinite())
    }

    @Test
    fun aSlimeWalkingThroughSwingsTheCurtainAndItSettlesBackAfter() {
        val curtain = curtain()
        val baseY = top + (curtain.rows - 1) * Tuning.CURTAIN_SEGMENT + 2f
        var swung = 0f
        for (step in 0 until 40) {
            curtain.step(body(centerX = -20f + step * 3f, baseY = baseY), dt)
            swung = maxOf(swung, curtain.farthestFromRest())
            assertTrue(curtain.isFinite(), "blew up at step $step")
        }
        assertTrue(swung > 3f, "swung only $swung")
        repeat(600) { curtain.step(null, dt) }
        assertTrue(curtain.farthestFromRest() < 1f, "still ${curtain.farthestFromRest()} from rest")
    }
}
