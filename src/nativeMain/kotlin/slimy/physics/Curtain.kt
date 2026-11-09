package slimy.physics

import klyph.physics.Outline
import klyph.physics.SoftBody
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A curtain of hanging strands, like cloth: [ROWS] Verlet particles down each strand,
 * the top ones pinned along a ledge from [left] to [right] at height [top], one strand every [spacing] units.
 * Strands are held to their length and loosely to their neighbours. A slime that moves into the
 * curtain pushes the particles out of its body, so the cloth swings, and settles again after.
 * The curtain does not push back on the slime.
 *
 * A particle is named by its row down the strand and its column across; row 0 is the pinned one.
 */
class Curtain(val left: Float, val right: Float, val top: Float, val spacing: Float) {
    val columns: Int = ((right - left) / spacing).roundToInt() + 1
    val rows: Int = ROWS
    private val count: Int = columns * rows
    private val x = FloatArray(count)
    private val y = FloatArray(count)
    private val prevX = FloatArray(count)
    private val prevY = FloatArray(count)
    private val nearest = FloatArray(2)

    /** The x of the particle in [row] of strand [column]. */
    fun particleX(row: Int, column: Int): Float = x[row * columns + column]

    /** The y of the particle in [row] of strand [column]. */
    fun particleY(row: Int, column: Int): Float = y[row * columns + column]

    init {
        for (r in 0 until rows) for (c in 0 until columns) {
            val i = r * columns + c
            x[i] = left + c * spacing
            y[i] = top + r * SEGMENT
            prevX[i] = x[i]
            prevY[i] = y[i]
        }
    }

    /** Advances the curtain by [dt] seconds; [slime], if there is one, pushes it about. */
    fun step(slime: SoftBody?, dt: Float) {
        val h = dt / SUBSTEPS
        repeat(SUBSTEPS) {
            integrate(h)
            repeat(ITERATIONS) {
                for (r in 0 until rows - 1) for (c in 0 until columns) link(r * columns + c, (r + 1) * columns + c, SEGMENT, 1f, onlyWhenStretched = false)
                for (r in 1 until rows) for (c in 0 until columns - 1) link(r * columns + c, r * columns + c + 1, spacing, SIDE_STIFFNESS, onlyWhenStretched = true)
                if (slime != null) for (i in columns until count) pushOut(slime, i)
            }
        }
    }

    private fun integrate(h: Float) {
        for (i in columns until count) {
            val vx = (x[i] - prevX[i]) * DAMPING
            val vy = (y[i] - prevY[i]) * DAMPING
            prevX[i] = x[i]
            prevY[i] = y[i]
            x[i] += vx
            y[i] += vy + GRAVITY * h * h
        }
    }

    /**
     * Pulls two particles toward [rest] apart by [stiffness] of the error; a particle in row 0 does not
     * move. With [onlyWhenStretched] the link is slack when the particles are closer than [rest], like
     * cloth, which the strands' neighbours would otherwise knot into folds.
     */
    private fun link(a: Int, b: Int, rest: Float, stiffness: Float, onlyWhenStretched: Boolean) {
        val dx = x[b] - x[a]
        val dy = y[b] - y[a]
        val length = sqrt(dx * dx + dy * dy)
        if (length < 1e-6f || (onlyWhenStretched && length < rest)) return
        val wa = if (a < columns) 0f else 1f
        val wb = if (b < columns) 0f else 1f
        val total = wa + wb
        if (total == 0f) return
        val correction = stiffness * (length - rest) / length / total
        x[a] += dx * correction * wa
        y[a] += dy * correction * wa
        x[b] -= dx * correction * wb
        y[b] -= dy * correction * wb
    }

    /** If particle [i] is inside [slime], moves it to the nearest point on the outline, keeping a little of the shove as speed. */
    private fun pushOut(slime: SoftBody, i: Int) {
        val px = x[i]
        val py = y[i]
        if (!Outline.contains(slime, px, py)) return
        val distanceSquared = Outline.nearestPoint(slime, px, py, nearest)
        // Out through the nearest edge, and a hair past it so the particle is not left on the line.
        val dx = nearest[0] - px
        val dy = nearest[1] - py
        val length = sqrt(distanceSquared)
        val nx = if (length < 1e-6f) 0f else dx / length
        val ny = if (length < 1e-6f) -1f else dy / length
        val newX = nearest[0] + nx * 0.5f
        val newY = nearest[1] + ny * 0.5f
        prevX[i] += (newX - px) * 0.7f
        prevY[i] += (newY - py) * 0.7f
        x[i] = newX
        y[i] = newY
    }

    /** Whether every coordinate is a real number, for catching a blown-up simulation. */
    fun isFinite(): Boolean {
        for (i in 0 until count) if (!x[i].isFinite() || !y[i].isFinite()) return false
        return true
    }

    companion object {
        /** Substeps per 60 Hz step. The curtain has its own, so it can change without touching the slime. */
        const val SUBSTEPS = 4

        /** Particles down each strand of a hanging curtain, the first pinned to the ledge, and the distance between neighbours. */
        const val ROWS = 5
        const val SEGMENT = 10f

        /** A curtain falls gently and loses a little speed each substep, so it swings and settles. */
        const val GRAVITY = 900f
        const val DAMPING = 0.985f

        /** How hard the links between neighbouring strands pull, 0 to 1; low, so the curtain drapes like cloth. */
        const val SIDE_STIFFNESS = 0.25f
        const val ITERATIONS = 6
    }
}
