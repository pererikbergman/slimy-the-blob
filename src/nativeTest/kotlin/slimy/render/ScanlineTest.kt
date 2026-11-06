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

    private val out = FloatArray(Tuning.ARC_SEGMENTS + Tuning.BASE_SEGMENTS)

    @Test
    fun aRowThroughTheMiddleOfTheDomeCrossesTheOutlineTwiceInOrder() {
        val b = body()
        val count = Scanline.crossings(b, 45f, out)
        assertEquals(2, count)
        assertTrue(out[0] < out[1])
        assertTrue(out[0] < 100f && out[1] > 100f)
    }

    @Test
    fun rowsAboveAndBelowTheBodyCrossNothing() {
        val b = body()
        assertEquals(0, Scanline.crossings(b, 10f, out))
        assertEquals(0, Scanline.crossings(b, 60f, out))
    }

    @Test
    fun theRowsOfAFilledBodyAddUpToItsArea() {
        val b = body()
        var covered = 0f
        var row = floor(b.minY()).toInt()
        while (row < ceil(b.maxY()).toInt()) {
            val count = Scanline.crossings(b, row + 0.5f, out)
            var k = 0
            while (k + 1 < count) {
                covered += out[k + 1] - out[k]
                k += 2
            }
            row++
        }
        assertTrue(abs(covered - b.area()) < b.area() * 0.05f, "rows cover $covered of ${b.area()}")
    }
}
