package slimy.physics

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoftBodyTest {
    private val h = 1f / (60f * Tuning.SUBSTEPS)

    private fun body(): SoftBody = SoftBody.semiDisc(Tuning.RADIUS, Tuning.ARC_SEGMENTS, Tuning.BASE_SEGMENTS)

    @Test
    fun theRestShapeIsADomeAsWideAsTwiceItsRadiusAndHeightScaleTimesItsRadiusTall() {
        val b = body()
        assertEquals(Tuning.ARC_SEGMENTS + Tuning.BASE_SEGMENTS, b.ringCount)
        assertEquals(2 * Tuning.RADIUS, b.maxX() - b.minX(), 0.01f)
        assertEquals(Tuning.RADIUS * Tuning.HEIGHT_SCALE, b.maxY() - b.minY(), 0.01f)
    }

    @Test
    fun placingPutsTheBaseOnTheGivenLineAndTheBodyAtRest() {
        val b = body()
        b.placeAt(centerX = 100f, baseY = 50f)
        assertEquals(50f, b.maxY(), 0.01f)
        assertEquals(100f, (b.minX() + b.maxX()) / 2f, 0.01f)
        assertEquals(0f, b.velocityY(0, h))
    }

    @Test
    fun gravityPullsEveryParticleDownAndAStepNeverExceedsTheCap() {
        val b = body()
        b.placeAt(0f, 0f)
        val before = b.y[3]
        b.addVelocityAll(0f, 100_000f, h)
        b.integrate(Tuning.GRAVITY, h, Tuning.DAMPING, Tuning.MAX_STEP)
        val moved = b.y[3] - before
        assertTrue(moved > 0f, "moved $moved")
        assertTrue(moved <= Tuning.MAX_STEP + 0.001f, "moved $moved")
    }

    @Test
    fun relaxingASquashedBodyRestoresMostOfItsArea() {
        val b = body()
        b.placeAt(0f, 0f)
        val rest = b.area()
        for (i in 0 until b.size) b.y[i] = b.y[i] * 0.5f
        for (i in 0 until b.size) b.prevY[i] = b.y[i]
        assertTrue(b.area() < rest * 0.6f)
        repeat(60) { b.relax(h, Tuning.EDGE_COMPLIANCE, Tuning.SPOKE_COMPLIANCE, Tuning.AREA_COMPLIANCE) }
        assertTrue(b.isFinite())
        assertTrue(b.area() > rest * 0.85f, "area ${b.area()} of $rest")
    }

    @Test
    fun anUndisturbedBodyIsAlreadyAtRest() {
        val b = body()
        b.placeAt(0f, 0f)
        val rest = b.area()
        repeat(20) { b.relax(h, Tuning.EDGE_COMPLIANCE, Tuning.SPOKE_COMPLIANCE, Tuning.AREA_COMPLIANCE) }
        assertTrue(abs(b.area() - rest) < rest * 0.01f)
        assertEquals(1f, b.maxEdgeStretch(), 0.01f)
    }
}
