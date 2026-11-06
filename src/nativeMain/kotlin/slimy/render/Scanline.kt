package slimy.render

import slimy.physics.SoftBody

/** Finds where a horizontal line crosses a slime's outline, so the outline can be filled row by row. */
object Scanline {
    /**
     * Writes into [out] the x of every place the outline crosses height [y], left to right, and
     * returns how many. Pairs of them (0 and 1, 2 and 3, ...) bound the inside. [out] must hold at
     * least as many entries as the body has outline particles.
     */
    fun crossings(body: SoftBody, y: Float, out: FloatArray): Int {
        val n = body.ringCount
        var count = 0
        for (i in 0 until n) {
            val j = if (i + 1 == n) 0 else i + 1
            val y0 = body.y[i]
            val y1 = body.y[j]
            if ((y0 <= y) != (y1 <= y)) {
                out[count++] = body.x[i] + (y - y0) / (y1 - y0) * (body.x[j] - body.x[i])
            }
        }
        for (i in 1 until count) {
            val value = out[i]
            var k = i - 1
            while (k >= 0 && out[k] > value) {
                out[k + 1] = out[k]
                k--
            }
            out[k + 1] = value
        }
        return count
    }
}
