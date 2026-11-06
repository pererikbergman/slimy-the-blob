package slimy.physics

/**
 * What a slime has been resting on: which outline particles touched a surface facing up, and
 * whether it was a bounce pad. It keeps this step's touches and the previous step's apart, because
 * a jump asks what the body was standing on before it left the ground.
 *
 * Call [beginStep] once at the start of every step, then let [SolidGrid.resolve] record touches as
 * many times as it likes; there is no per-substep bookkeeping. [clear] forgets everything, for a
 * body that has been put somewhere new.
 */
class Contacts(particleCount: Int, private val outlineCount: Int) {
    private var current = BooleanArray(particleCount)
    private var previous = BooleanArray(particleCount)

    /** Whether any particle touched a surface facing up so far this step. */
    var touchedFloor = false
        private set

    /** Whether any particle touched a bounce pad so far this step. */
    var touchedBouncePad = false
        private set

    /** Starts a step: what was touched during the one before is remembered, and this step starts with nothing touched. */
    fun beginStep() {
        val swap = previous
        previous = current
        current = swap
        current.fill(false)
        touchedFloor = false
        touchedBouncePad = false
    }

    /** Forgets this step and the last one. */
    fun clear() {
        current.fill(false)
        previous.fill(false)
        touchedFloor = false
        touchedBouncePad = false
    }

    /** Whether particle [i] touched a surface facing up so far this step. */
    fun isOnFloor(i: Int): Boolean = current[i]

    /** Whether outline particle [i] rested on a surface facing up at any time in the previous step. The core never counts. */
    fun wasOnFloor(i: Int): Boolean = i < outlineCount && previous[i]

    /** Records that particle [i] was pushed by a surface facing up, [bouncy] if it was a bounce pad. [SolidGrid] calls this. */
    fun recordFloor(i: Int, bouncy: Boolean) {
        current[i] = true
        touchedFloor = true
        if (bouncy) touchedBouncePad = true
    }
}
