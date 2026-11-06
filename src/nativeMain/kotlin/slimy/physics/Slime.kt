package slimy.physics

/** What the player asks of the slime this step. [move] is -1 for left to 1 for right. */
class SlimeInput(
    var move: Float = 0f,
    /** Jump went down this step. */
    var jumpPressed: Boolean = false,
    /** Jump is still down, which keeps a rising jump going. */
    var jumpHeld: Boolean = false,
)

/**
 * The player's slime: a [SoftBody] moved through [grid] by input. [step] advances it by one
 * fixed step, cut into [SUBSTEPS] substeps of integrate, relax and collide.
 */
class Slime(private val grid: SolidGrid, startX: Float, startBaseY: Float) {
    val body: SoftBody = SoftBody.semiDisc()
    private val contacts = Contacts(body.size, body.ringCount)

    /** Whether any particle rested on something facing up during the last step. */
    var isGrounded = false
        private set

    /** Set by the last step: whether it started a jump, and whether a bounce pad threw the slime. */
    var hasJumped = false
        private set
    var hasBounced = false
        private set

    /** -1 after pushing left, 1 after pushing right. */
    var facing = 1
        private set

    /** Mean horizontal velocity of the body, in units per second, as of the last step. */
    val velocityX: Float get() = body.meanVelocityX(substepSeconds)

    private var substepSeconds = 1f / 60f / SUBSTEPS

    private var coyote = 0f
    private var jumpBuffer = 0f

    /** Whether the slime is still rising from a jump, the only rise a released key cuts short. */
    private var isJumpRising = false

    init {
        require(MAX_STEP < SolidGrid.TILE / 2f) { "A substep must stay under half a tile or a particle can tunnel through a corner." }
        reset(startX, startBaseY)
    }

    /** Puts the slime back at rest with its base on [baseY] and its middle on [x]. */
    fun reset(x: Float, baseY: Float) {
        body.placeAt(x, baseY)
        contacts.clear()
        isGrounded = false
        hasJumped = false
        hasBounced = false
        coyote = 0f
        jumpBuffer = 0f
        isJumpRising = false
    }

    /** Advances the slime by [dt] seconds under [input]. */
    fun step(input: SlimeInput, dt: Float) {
        val h = dt / SUBSTEPS
        substepSeconds = h
        hasJumped = false
        hasBounced = false
        contacts.beginStep()

        coyote = if (isGrounded) COYOTE_SECONDS else coyote - dt
        jumpBuffer = if (input.jumpPressed) JUMP_BUFFER_SECONDS else jumpBuffer - dt
        if (jumpBuffer > 0f && coyote > 0f) jump(h)

        if (input.move != 0f) {
            facing = if (input.move > 0f) 1 else -1
            val speed = body.meanVelocityX(h)
            val canSpeedUp = if (input.move > 0f) speed < MAX_SPEED else speed > -MAX_SPEED
            if (canSpeedUp) {
                val acceleration = if (isGrounded) MOVE_ACCEL else AIR_ACCEL
                body.addVelocityAll(input.move * acceleration * dt, 0f, h)
            }
        } else if (isGrounded) {
            val keep = 1f - GROUND_FRICTION
            body.scaleVelocityX(keep)
        }

        if (body.meanVelocityY(h) >= 0f) isJumpRising = false
        val gravity = if (isJumpRising && !input.jumpHeld) GRAVITY * SHORT_HOP_GRAVITY else GRAVITY

        repeat(SUBSTEPS) {
            body.integrate(gravity, h, DAMPING, MAX_STEP)
            repeat(RELAX_ITERATIONS) {
                body.relax(h, EDGE_COMPLIANCE, SPOKE_COMPLIANCE, AREA_COMPLIANCE)
                grid.resolve(body, contacts)
            }
        }
        isGrounded = contacts.touchedFloor

        if (contacts.touchedBouncePad && body.meanVelocityY(h) > -BOUNCE_SPEED / 2f) {
            for (i in 0 until body.size) body.setVelocityY(i, -BOUNCE_SPEED, h)
            hasBounced = true
            isGrounded = false
            isJumpRising = false
        }
    }

    /** Every particle leaves upward at once; the ones on the floor get extra lift. */
    private fun jump(h: Float) {
        for (i in 0 until body.size) {
            val lift = if (contacts.wasOnFloor(i)) JUMP_BASE_LIFT else 0f
            body.setVelocityY(i, -(JUMP_SPEED + lift), h)
        }
        isGrounded = false
        coyote = 0f
        jumpBuffer = 0f
        hasJumped = true
        isJumpRising = true
    }

    companion object {
        /** Physics substeps per 60 Hz step, and constraint passes per substep. */
        const val SUBSTEPS = 4
        const val RELAX_ITERATIONS = 6

        /** Largest distance a particle may move in one substep; under half a tile so it cannot tunnel through a corner. */
        const val MAX_STEP = 4f

        /** Downward acceleration. */
        const val GRAVITY = 1500f

        /** Share of velocity kept each substep. */
        const val DAMPING = 0.999f

        /**
         * How far each kind of constraint gives per unit of force (compliance, 0 is rigid): the ring
         * edges, the spokes to the core and the area. Soft spokes let the slime stretch and squash,
         * a soft area lets it sag and be squeezed, stiff edges keep the outline from tearing. These came
         * from running AcceptanceTest over a grid of values: spokes much softer and the slime flattens
         * under its own core and does not spring back; much stiffer and it perches on the rim of a shaft
         * narrower than itself instead of falling in.
         */
        const val EDGE_COMPLIANCE = 0.00002f
        const val SPOKE_COMPLIANCE = 0.0005f
        const val AREA_COMPLIANCE = 0.0004f

        /**
         * Horizontal acceleration on the ground and in the air, and the speed input cannot push past.
         * The ground value is also what shoves the slime through a gap narrower than itself: below about
         * 1000 it stalls at the mouth of an 8-unit tunnel, from 1400 up it gets through (see AcceptanceTest).
         */
        const val MOVE_ACCEL = 1800f
        const val AIR_ACCEL = 1000f
        const val MAX_SPEED = 170f

        /** Share of horizontal speed lost per step on the ground with no input. */
        const val GROUND_FRICTION = 0.12f

        /** Upward speed of a jump, and the extra speed given to particles touching the floor. */
        const val JUMP_SPEED = 420f
        const val JUMP_BASE_LIFT = 140f

        /** Gravity multiplier while rising with the jump key released, for short hops. */
        const val SHORT_HOP_GRAVITY = 2.2f

        /** Upward speed a bounce pad gives. */
        const val BOUNCE_SPEED = 680f

        /** Grace after leaving the ground in which a jump still works, and before landing in which an early press still counts. */
        const val COYOTE_SECONDS = 0.1f
        const val JUMP_BUFFER_SECONDS = 0.1f
    }
}
