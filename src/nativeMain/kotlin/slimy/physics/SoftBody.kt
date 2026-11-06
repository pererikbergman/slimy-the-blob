package slimy.physics

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A ring of Verlet particles around a core particle. Particles `0 until ringCount` form the closed
 * outline, in order; particle [ringCount] is the core. Three kinds of constraint hold the shape:
 * stiff distance constraints between neighbours on the ring, soft ones from every ring particle to
 * the core (they remember the shape and let it stretch), and an area constraint that keeps the
 * volume, so squashing the slime in one direction pushes it out in another.
 *
 * The constraints are XPBD: each has a compliance, the inverse of a stiffness in real units, so a
 * softer body stays softer however many passes run. The core is [coreMass] times heavier than a ring
 * particle and stands for the slime's insides: when the outline is held, say on the rim of a gap,
 * the core's weight drags the body through it.
 *
 * Velocity is implicit: the difference between a particle's position and its previous position,
 * which is why [addVelocity] edits the previous position.
 */
class SoftBody private constructor(
    /** Number of outline particles; the core comes after them. */
    val ringCount: Int,
    private val restX: FloatArray,
    private val restY: FloatArray,
    coreMass: Float,
) {
    /** Outline particles plus the core. */
    val size: Int = ringCount + 1
    val x = FloatArray(size)
    val y = FloatArray(size)
    val prevX = FloatArray(size)
    val prevY = FloatArray(size)

    private val edgeRest = FloatArray(ringCount)
    private val spokeRest = FloatArray(ringCount)
    private val edgeLambda = FloatArray(ringCount)
    private val spokeLambda = FloatArray(ringCount)
    private var areaLambda = 0f
    private val coreWeight = 1f / coreMass
    private val areaDx = FloatArray(ringCount)
    private val areaDy = FloatArray(ringCount)
    private val areaSign: Float
    private val restArea: Float

    init {
        for (i in 0 until size) {
            x[i] = restX[i]
            y[i] = restY[i]
            prevX[i] = x[i]
            prevY[i] = y[i]
        }
        for (i in 0 until ringCount) {
            val j = (i + 1) % ringCount
            edgeRest[i] = distance(i, j)
            spokeRest[i] = distance(i, ringCount)
        }
        val signed = signedArea()
        areaSign = if (signed < 0f) -1f else 1f
        restArea = signed * areaSign
    }

    /** Moves the whole body, at rest, so its base lies on [baseY] and its middle on [centerX]. */
    fun placeAt(centerX: Float, baseY: Float) {
        val bottom = maxY()
        val middle = (minX() + maxX()) / 2f
        for (i in 0 until size) {
            x[i] += centerX - middle
            y[i] += baseY - bottom
            prevX[i] = x[i]
            prevY[i] = y[i]
        }
    }

    /**
     * Starts a substep of [h] seconds: keeps every particle's velocity (damped), adds gravity, caps
     * the move at [maxStep] and forgets the constraint forces of the substep before.
     */
    fun integrate(gravity: Float, h: Float, damping: Float, maxStep: Float) {
        edgeLambda.fill(0f)
        spokeLambda.fill(0f)
        areaLambda = 0f
        for (i in 0 until size) {
            var dx = (x[i] - prevX[i]) * damping
            var dy = (y[i] - prevY[i]) * damping + gravity * h * h
            val length = sqrt(dx * dx + dy * dy)
            if (length > maxStep) {
                dx *= maxStep / length
                dy *= maxStep / length
            }
            prevX[i] = x[i]
            prevY[i] = y[i]
            x[i] += dx
            y[i] += dy
        }
    }

    /**
     * One pass over every constraint of the substep of [h] seconds that [integrate] began. A
     * compliance is how far a constraint gives per unit of force; 0 is rigid.
     */
    fun relax(h: Float, edgeCompliance: Float, spokeCompliance: Float, areaCompliance: Float) {
        val edgeSoftness = edgeCompliance / (h * h)
        val spokeSoftness = spokeCompliance / (h * h)
        for (i in 0 until ringCount) pull(i, (i + 1) % ringCount, edgeRest[i], edgeSoftness, edgeLambda, i)
        for (i in 0 until ringCount) pull(i, ringCount, spokeRest[i], spokeSoftness, spokeLambda, i)
        keepArea(areaCompliance / (h * h))
    }

    private fun weight(i: Int): Float = if (i == ringCount) coreWeight else 1f

    private fun pull(a: Int, b: Int, rest: Float, softness: Float, lambda: FloatArray, index: Int) {
        val dx = x[a] - x[b]
        val dy = y[a] - y[b]
        val length = sqrt(dx * dx + dy * dy)
        if (length < 1e-6f) return
        val wa = weight(a)
        val wb = weight(b)
        val change = (-(length - rest) - softness * lambda[index]) / (wa + wb + softness)
        lambda[index] += change
        val nx = dx / length
        val ny = dy / length
        x[a] += wa * change * nx
        y[a] += wa * change * ny
        x[b] -= wb * change * nx
        y[b] -= wb * change * ny
    }

    private fun keepArea(softness: Float) {
        val error = area() - restArea
        var gradientSquared = 0f
        for (i in 0 until ringCount) {
            val gx = gradientX(i)
            val gy = gradientY(i)
            gradientSquared += gx * gx + gy * gy
        }
        val change = (-error - softness * areaLambda) / (gradientSquared + softness)
        areaLambda += change
        // Every correction is worked out before any particle moves, so the order does not matter.
        for (i in 0 until ringCount) {
            areaDx[i] = gradientX(i) * change
            areaDy[i] = gradientY(i) * change
        }
        for (i in 0 until ringCount) {
            x[i] += areaDx[i]
            y[i] += areaDy[i]
        }
    }

    private fun gradientX(i: Int): Float = areaSign * 0.5f * (y[(i + 1) % ringCount] - y[(i + ringCount - 1) % ringCount])

    private fun gradientY(i: Int): Float = areaSign * 0.5f * (x[(i + ringCount - 1) % ringCount] - x[(i + 1) % ringCount])

    private fun signedArea(): Float {
        var sum = 0f
        for (i in 0 until ringCount) {
            val j = (i + 1) % ringCount
            sum += x[i] * y[j] - x[j] * y[i]
        }
        return sum / 2f
    }

    /** Whether the point ([px], [py]) is inside the outline. */
    fun contains(px: Float, py: Float): Boolean {
        var inside = false
        var j = ringCount - 1
        for (i in 0 until ringCount) {
            if ((y[i] > py) != (y[j] > py) && px < (x[j] - x[i]) * (py - y[i]) / (y[j] - y[i]) + x[i]) inside = !inside
            j = i
        }
        return inside
    }

    /** Area enclosed by the outline. */
    fun area(): Float = signedArea() * areaSign

    /** Adds ([dx], [dy]) units per second to particle [i], for a substep of [h] seconds. */
    fun addVelocity(i: Int, dx: Float, dy: Float, h: Float) {
        prevX[i] -= dx * h
        prevY[i] -= dy * h
    }

    /** Adds the same velocity to every particle. */
    fun addVelocityAll(dx: Float, dy: Float, h: Float) {
        for (i in 0 until size) addVelocity(i, dx, dy, h)
    }

    /** Sets the vertical velocity of particle [i] to [dy] units per second, for a substep of [h] seconds. */
    fun setVelocityY(i: Int, dy: Float, h: Float) {
        prevY[i] = y[i] - dy * h
    }

    fun velocityX(i: Int, h: Float): Float = (x[i] - prevX[i]) / h

    fun velocityY(i: Int, h: Float): Float = (y[i] - prevY[i]) / h

    /** Mean position of the outline particles. */
    fun centerX(): Float = ringMean(x)

    fun centerY(): Float = ringMean(y)

    /** Mean velocity of the outline particles, in units per second. */
    fun meanVelocityX(h: Float): Float = (ringMean(x) - ringMean(prevX)) / h

    fun meanVelocityY(h: Float): Float = (ringMean(y) - ringMean(prevY)) / h

    private fun ringMean(values: FloatArray): Float {
        var sum = 0f
        for (i in 0 until ringCount) sum += values[i]
        return sum / ringCount
    }

    fun minX(): Float = ringExtreme(x, -1f)

    fun maxX(): Float = ringExtreme(x, 1f)

    fun minY(): Float = ringExtreme(y, -1f)

    fun maxY(): Float = ringExtreme(y, 1f)

    private fun ringExtreme(values: FloatArray, direction: Float): Float {
        var best = values[0]
        for (i in 1 until ringCount) if (values[i] * direction > best * direction) best = values[i]
        return best
    }

    /** The longest ring edge as a multiple of its rest length: 1 at rest, large when the ring is tearing. */
    fun maxEdgeStretch(): Float {
        var worst = 0f
        for (i in 0 until ringCount) {
            val stretch = distance(i, (i + 1) % ringCount) / edgeRest[i]
            if (stretch > worst) worst = stretch
        }
        return worst
    }

    /** Whether every coordinate is a real number, for catching a blown-up simulation. */
    fun isFinite(): Boolean {
        for (i in 0 until size) if (!x[i].isFinite() || !y[i].isFinite()) return false
        return true
    }

    private fun distance(a: Int, b: Int): Float {
        val dx = x[a] - x[b]
        val dy = y[a] - y[b]
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        /**
         * A half-disc of [radius]: [arcSegments] segments over the dome from the left end to the
         * right, then [baseSegments] along the flat base back to the left, with the core where the
         * half-disc's centre of mass is. The base lies on y = 0 and the middle on x = 0; y grows down.
         */
        fun semiDisc(radius: Float, arcSegments: Int, baseSegments: Int, coreMass: Float = Tuning.CORE_MASS): SoftBody {
            val ring = arcSegments + baseSegments
            val xs = FloatArray(ring + 1)
            val ys = FloatArray(ring + 1)
            for (i in 0..arcSegments) {
                val angle = PI.toFloat() + PI.toFloat() * i / arcSegments
                xs[i] = cos(angle) * radius
                ys[i] = sin(angle) * radius
            }
            for (j in 1 until baseSegments) {
                xs[arcSegments + j] = radius - 2f * radius * j / baseSegments
                ys[arcSegments + j] = 0f
            }
            xs[ring] = 0f
            ys[ring] = -4f * radius / (3f * PI.toFloat())
            return SoftBody(ring, xs, ys, coreMass)
        }
    }
}
