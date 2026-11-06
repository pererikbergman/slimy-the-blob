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
 * fixed step, cut into [Tuning.SUBSTEPS] substeps of integrate, relax and collide.
 */
class Slime(private val grid: SolidGrid, startX: Float, startBaseY: Float) {
    val body: SoftBody = SoftBody.semiDisc(Tuning.RADIUS, Tuning.ARC_SEGMENTS, Tuning.BASE_SEGMENTS)
    private val contacts = Contacts(body.size)

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

    private var coyote = 0f
    private var jumpBuffer = 0f

    /** Whether the slime is still rising from a jump, the only rise a released key cuts short. */
    private var isJumpRising = false

    init {
        reset(startX, startBaseY)
    }

    /** Puts the slime back at rest with its base on [baseY] and its middle on [x]. */
    fun reset(x: Float, baseY: Float) {
        body.placeAt(x, baseY)
        contacts.beginStep()
        contacts.beginSubstep()
        isGrounded = false
        hasJumped = false
        hasBounced = false
        coyote = 0f
        jumpBuffer = 0f
        isJumpRising = false
    }

    /** Advances the slime by [dt] seconds under [input]. */
    fun step(input: SlimeInput, dt: Float) {
        val h = dt / Tuning.SUBSTEPS
        hasJumped = false
        hasBounced = false

        coyote = if (isGrounded) Tuning.COYOTE_SECONDS else coyote - dt
        jumpBuffer = if (input.jumpPressed) Tuning.JUMP_BUFFER_SECONDS else jumpBuffer - dt
        if (jumpBuffer > 0f && coyote > 0f) jump(h)

        if (input.move != 0f) {
            facing = if (input.move > 0f) 1 else -1
            val speed = body.meanVelocityX(h)
            val canSpeedUp = if (input.move > 0f) speed < Tuning.MAX_SPEED else speed > -Tuning.MAX_SPEED
            if (canSpeedUp) {
                val acceleration = if (isGrounded) Tuning.MOVE_ACCEL else Tuning.AIR_ACCEL
                body.addVelocityAll(input.move * acceleration * dt, 0f, h)
            }
        } else if (isGrounded) {
            val keep = 1f - Tuning.GROUND_FRICTION
            for (i in 0 until body.size) body.prevX[i] = body.x[i] - (body.x[i] - body.prevX[i]) * keep
        }

        if (body.meanVelocityY(h) >= 0f) isJumpRising = false
        val gravity = if (isJumpRising && !input.jumpHeld) Tuning.GRAVITY * Tuning.SHORT_HOP_GRAVITY else Tuning.GRAVITY

        contacts.beginStep()
        repeat(Tuning.SUBSTEPS) {
            contacts.beginSubstep()
            body.integrate(gravity, h, Tuning.DAMPING, Tuning.MAX_STEP)
            repeat(Tuning.RELAX_ITERATIONS) {
                body.relax(h, Tuning.EDGE_COMPLIANCE, Tuning.SPOKE_COMPLIANCE, Tuning.AREA_COMPLIANCE)
                grid.resolve(body, contacts)
            }
        }
        isGrounded = contacts.anyFloor

        if (contacts.anyBounce && body.meanVelocityY(h) > -Tuning.BOUNCE_SPEED / 2f) {
            for (i in 0 until body.size) body.setVelocityY(i, -Tuning.BOUNCE_SPEED, h)
            hasBounced = true
            isGrounded = false
            isJumpRising = false
        }
    }

    /** Every particle leaves upward at once; the ones on the floor get extra lift. */
    private fun jump(h: Float) {
        for (i in 0 until body.size) {
            val lift = if (i < body.ringCount && contacts.floor[i]) Tuning.JUMP_BASE_LIFT else 0f
            body.setVelocityY(i, -(Tuning.JUMP_SPEED + lift), h)
        }
        isGrounded = false
        coyote = 0f
        jumpBuffer = 0f
        hasJumped = true
        isJumpRising = true
    }
}
