package slimy.physics

import klyph.physics.Contacts
import klyph.physics.SoftBody
import klyph.physics.SoftBodyTuning
import klyph.physics.SolidGrid

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
    val body: SoftBody = newBody()
    private val contacts = Contacts(body.size, body.ringCount)

    /** The collision step of a substep, made once rather than on every call. */
    private val collide: () -> Unit = { grid.resolve(body, contacts) }

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
        require(body.tuning.maxStep < grid.tileSize / 2f) { "A substep must stay under half a tile or a particle can tunnel through a corner." }
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

        repeat(SUBSTEPS) { body.substep(h, gravity, collide) }
        isGrounded = contacts.touchedFloor

        if (contacts.touched(BOUNCE_PAD) && body.meanVelocityY(h) > -BOUNCE_SPEED / 2f) {
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
        /** The [klyph.physics.Solid.tag] of a bounce pad, which throws the slime up. */
        const val BOUNCE_PAD = 1

        /** Radius of the resting half-disc: the slime is 2 * RADIUS wide and RADIUS tall. */
        const val RADIUS = 13.3f

        /**
         * How much taller than a half-disc the resting dome is: 1 is a half-circle. The weight of the core
         * flattens it again, so at 1.3 the slime rests about 31 wide and 15 tall.
         */
        const val HEIGHT_SCALE = 1.3f

        /** Ring segments along the dome, and along the flat base. */
        const val ARC_SEGMENTS = 14
        const val BASE_SEGMENTS = 8

        /**
         * How many times heavier than an outline particle the core is. It stands for the slime's
         * insides, and its weight is what pulls the body through a gap narrower than itself. It is also
         * what flattens the dome, so rounder slimes and heavier cores pull against each other: with this
         * shape a slime that lands on a one-tile shaft from a hop sinks through, but one that is placed
         * on it at rest perches on the rim (it did sink from rest when the dome was lower and the core
         * 30; see AcceptanceTest and PlaythroughTest).
         */
        const val CORE_MASS = 34f

        /**
         * How soft the slime is. The compliances came from running AcceptanceTest over a grid of values:
         * spokes much softer and the slime flattens under its own core and does not spring back; much
         * stiffer and it perches on the rim of a shaft narrower than itself instead of falling in. A
         * particle moves at most 4 units a substep, under half a tile, so it cannot tunnel through a corner.
         */
        val TUNING = SoftBodyTuning(
            edgeCompliance = 0.00002f,
            spokeCompliance = 0.0005f,
            areaCompliance = 0.0004f,
            damping = 0.999f,
            maxStep = 4f,
            relaxIterations = 6,
        )

        /** A slime's body at rest, with its base on y = 0 and its middle on x = 0. */
        fun newBody(): SoftBody = SoftBody.semiDisc(RADIUS, ARC_SEGMENTS, BASE_SEGMENTS, CORE_MASS, HEIGHT_SCALE, TUNING)

        /** Physics substeps per 60 Hz step. */
        const val SUBSTEPS = 4

        /** Downward acceleration. */
        const val GRAVITY = 1500f

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
