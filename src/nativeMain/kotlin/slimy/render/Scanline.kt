package slimy.render

import slimy.physics.SoftBody

/** Finds the stretches of a horizontal line that lie inside a slime's outline, so the outline can be filled row by row. */
object Scanline {
    /**
     * Writes into [left] and [right] the stretches of height [y] that are inside the outline, left to
     * right, and returns how many. "Inside" means the outline winds around the point at least once, so
     * a body whose outline has folded over itself is still filled solid, where an even-odd rule would
     * leave the folded part hollow. [left] and [right] must hold at least as many entries as the body
     * has outline particles.
     */
    fun spans(body: SoftBody, y: Float, left: FloatArray, right: FloatArray): Int {
        val n = body.ringCount
        var crossings = 0
        for (i in 0 until n) {
            val j = if (i + 1 == n) 0 else i + 1
            val y0 = body.y[i]
            val y1 = body.y[j]
            if ((y0 <= y) != (y1 <= y)) {
                // Two parallel arrays keep the crossing's x in `left` and its direction in `right` until they are paired up below.
                left[crossings] = body.x[i] + (y - y0) / (y1 - y0) * (body.x[j] - body.x[i])
                right[crossings] = if (y1 > y0) 1f else -1f
                crossings++
            }
        }
        for (i in 1 until crossings) {
            val x = left[i]
            val direction = right[i]
            var k = i - 1
            while (k >= 0 && left[k] > x) {
                left[k + 1] = left[k]
                right[k + 1] = right[k]
                k--
            }
            left[k + 1] = x
            right[k + 1] = direction
        }
        // Walk the crossings left to right, keeping the winding number, and write each stretch where it is not zero
        // over the crossings already read: a stretch never ends before its own start, so this is safe in place.
        var winding = 0
        var start = 0f
        var count = 0
        for (i in 0 until crossings) {
            val x = left[i]
            val wasInside = winding != 0
            winding += right[i].toInt()
            val isInside = winding != 0
            if (!wasInside && isInside) {
                start = x
            } else if (wasInside && !isInside) {
                left[count] = start
                right[count] = x
                count++
            }
        }
        return count
    }
}
