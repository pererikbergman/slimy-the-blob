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

    /** How hard ring edges, spokes to the core and the area constraint pull back to rest, per pass (0..1). */
    const val EDGE_STIFFNESS = 0.9f
    const val SPOKE_STIFFNESS = 0.12f
    const val AREA_STIFFNESS = 0.6f

    /** Horizontal acceleration on the ground and in the air, and the speed input cannot push past. */
    const val MOVE_ACCEL = 900f
    const val AIR_ACCEL = 500f
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
