package slimy.render

import slimy.physics.SoftBody
import slimy.physics.Tuning
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScanlineTest {
    private fun body(): SoftBody = SoftBody.semiDisc(Tuning.RADIUS, Tuning.ARC_SEGMENTS, Tuning.BASE_SEGMENTS).also { it.placeAt(100f, 50f) }

    private val left = FloatArray(Tuning.ARC_SEGMENTS + Tuning.BASE_SEGMENTS)
    private val right = FloatArray(Tuning.ARC_SEGMENTS + Tuning.BASE_SEGMENTS)

    private fun filledArea(b: SoftBody): Float {
        var covered = 0f
        for (row in floor(b.minY()).toInt() until ceil(b.maxY()).toInt()) {
            val count = Scanline.spans(b, row + 0.5f, left, right)
            for (k in 0 until count) covered += right[k] - left[k]
        }
        return covered
    }

    @Test
    fun aRowThroughTheMiddleOfTheDomeIsOneStretchAroundTheCentre() {
        val b = body()
        assertEquals(1, Scanline.spans(b, 40f, left, right))
        assertTrue(left[0] < 100f && right[0] > 100f)
    }

    @Test
    fun rowsAboveAndBelowTheBodyHaveNoStretch() {
        val b = body()
        assertEquals(0, Scanline.spans(b, 10f, left, right))
        assertEquals(0, Scanline.spans(b, 60f, left, right))
    }

    @Test
    fun theRowsOfAFilledBodyAddUpToItsArea() {
        val b = body()
        assertTrue(abs(filledArea(b) - b.area()) < b.area() * 0.05f, "rows cover ${filledArea(b)} of ${b.area()}")
    }

    @Test
    fun anOutlineFoldedOverItselfIsStillFilledSolid() {
        val b = body()
        val before = filledArea(b)
        // Swap two stretches of the outline's particles, which twists it into a bow-tie across the dome.
        for (i in 0 until 4) {
            val a = 2 + i
            val c = 9 - i
            val x = b.x[a]; val y = b.y[a]
            b.x[a] = b.x[c]; b.y[a] = b.y[c]
            b.x[c] = x; b.y[c] = y
        }
        // An even-odd fill would drop the doubled part; the winding fill covers at least what the folded body encloses.
        assertTrue(filledArea(b) > before * 0.6f, "only ${filledArea(b)} of $before is filled after the fold")
    }
}
