package slimy.physics

/**
 * Every number that decides how the slime feels. Distances are world units (a tile is 16), times
 * are seconds, speeds are units per second. The simulation is a fixed 60 Hz step cut into
 * [SUBSTEPS] smaller steps, so a particle's per-substep movement is speed / (60 * SUBSTEPS).
 */
object Tuning {
    /** Side of one level tile. */
    const val TILE = 16f

    /** Radius of the resting half-disc: the slime is 2 * RADIUS wide and RADIUS tall. */
    const val RADIUS = 13.3f

    /** Ring segments along the dome, and along the flat base. */
    const val ARC_SEGMENTS = 14
    const val BASE_SEGMENTS = 8

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
    const val AREA_COMPLIANCE = 0.001f

    /**
     * How many times heavier than an outline particle the core is. It stands for the slime's
     * insides, and its weight is what pulls the body through a gap narrower than itself. At 16 or
     * less a slime resting on a one-tile shaft perches on the rim for good; from 20 it sinks in.
     */
    const val CORE_MASS = 30f

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
