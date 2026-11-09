package slimy

import slimy.level.Level
import slimy.level.Zone
import slimy.physics.Curtain
import slimy.physics.Slime
import slimy.physics.SlimeInput

/**
 * One play of a [Level]: the slime, the level's curtains, and the rules that tie them together.
 * It knows nothing of the window, the keyboard or the engine, so a test drives exactly the code the
 * game runs. The game reads its input into [setMove], [setJumpHeld] and [pressJump], calls [step]
 * once per fixed step, and draws what [slime] and [curtains] then hold.
 *
 * A step does these in this order, which matters: the level's moving parts move, the slime moves
 * against them, the curtains are pushed by where the slime now is, and a slime that has fallen out
 * of the level is put back at the start of its zone.
 */
class GameSession(val level: Level) {
    private val start = checkNotNull(level.start) { "The level has no start" }

    val slime = Slime(level.grid, start.x, start.y)
    val curtains: List<Curtain> = level.curtains.map { Curtain(it.left, it.right, it.top, Level.TILE) }

    private val input = SlimeInput()
    private var jumpWaiting = false

    /** The zone the slime is in, or `null` if the level has none. */
    val zone: Zone? get() = level.zoneAt(slime.body.centerX())

    /** Whether the slime has got past the level's finish. */
    val isFinished: Boolean
        get() {
            val finish = level.finish ?: return false
            return slime.body.centerX() > finish.x
        }

    /** Sets the direction pushed, -1 for left to 1 for right. */
    fun setMove(move: Float) {
        input.move = move
    }

    /** Sets whether jump is held down, which keeps a rising jump going. */
    fun setJumpHeld(held: Boolean) {
        input.jumpHeld = held
    }

    /** Presses jump. The press waits for the next [step], and counts once, however many frames pass first. */
    fun pressJump() {
        jumpWaiting = true
    }

    /** Puts the slime at rest beside the sign of zone [id], or at the level's start when there is no such zone. */
    fun restartInZone(id: Char?) {
        val target = id?.let { level.zone(it) }
        if (target != null) slime.reset(target.x + RESTART_OFFSET, target.y) else slime.reset(start.x, start.y)
    }

    /** Puts the slime back at the start of the zone it is in. */
    fun restartCurrentZone() = restartInZone(zone?.id)

    /** Advances everything by [dt] seconds; returns `true` if the slime fell out of the level and was put back. */
    fun step(dt: Float): Boolean {
        input.jumpPressed = jumpWaiting
        jumpWaiting = false
        level.advance(dt)
        slime.step(input, dt)
        for (curtain in curtains) curtain.step(slime.body, dt)
        if (slime.body.centerY() > level.height + FALL_MARGIN) {
            restartCurrentZone()
            return true
        }
        return false
    }

    companion object {
        /** How far in front of a zone's sign the slime is put when a zone is restarted, so it does not stand inside the sign. */
        const val RESTART_OFFSET = 24f

        /** How far below the level's bottom the slime may fall before it is put back. */
        const val FALL_MARGIN = 4 * Level.TILE
    }
}
