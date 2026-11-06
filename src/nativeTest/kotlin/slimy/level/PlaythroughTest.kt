package slimy.level

import slimy.physics.Slime
import slimy.physics.SlimeInput
import slimy.physics.SolidGrid
import slimy.physics.SolidShape
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Plays each zone of the real level with a simple bot on the real physics: hold right, and jump
 * when a wall is just ahead or the ground ends. A zone that this cannot cross is a zone a person
 * would struggle with too, or a bug in the level.
 */
class PlaythroughTest {
    private val dt = 1f / 60f
    private val level = TestLevel.level
    private val tile = 16f

    /** Whether the point is in a solid; a stub only fills the top half of its tile, a slope counts by its tile. */
    private fun solidAt(grid: SolidGrid, x: Float, y: Float): Boolean {
        val solid = grid.at(floor(x / tile).toInt(), floor(y / tile).toInt()) ?: return false
        return solid.shape != SolidShape.BOX || (y >= solid.top && y <= solid.bottom)
    }

    /** Runs from the sign of [zone], 24 units in, until the slime's middle passes [targetCol] or [maxSteps] pass; returns where it got to. */
    private fun play(zone: Char, targetCol: Int, maxSteps: Int = 3000): Slime {
        val sign = checkNotNull(level.zone(zone))
        return playFrom(Slime(level.grid, sign.x + 24f, sign.y), zone, targetCol, maxSteps)
    }

    /** Runs the bot with [slime] where it already is, until it passes [targetCol] or [maxSteps] pass. */
    private fun playFrom(slime: Slime, zone: Char, targetCol: Int, maxSteps: Int = 3000): Slime {
        val input = SlimeInput(move = 1f, jumpHeld = true)
        var step = 0
        while (step < maxSteps && slime.body.centerX() < targetCol * tile) {
            val front = slime.body.maxX()
            val feet = slime.body.maxY()
            val wallAhead = solidAt(level.grid, front + 4f, feet - 6f)
            val groundEnds = !solidAt(level.grid, front + 6f, feet + 6f) && !solidAt(level.grid, front + 6f, feet + 22f)
            input.jumpPressed = slime.isGrounded && (wallAhead || groundEnds)
            level.advance(dt)
            slime.step(input, dt)
            assertTrue(slime.body.isFinite(), "the body blew up at step $step in zone $zone")
            step++
        }
        return slime
    }

    private fun assertReached(slime: Slime, col: Int, zone: Char) {
        assertTrue(
            slime.body.centerX() >= col * tile,
            "zone $zone: stuck at column ${slime.body.centerX() / tile} (y ${slime.body.centerY()}), wanted column $col",
        )
    }

    @Test
    fun zoneAIsWalkable() = assertReached(play('A', 38), 38, 'A')

    @Test
    fun zoneBCanBeCrossedPitsStepsAndAll() = assertReached(play('B', 98), 98, 'B')

    @Test
    fun zoneCTunnelsAndStepsLeadUpToTheDeck() = assertReached(play('C', 149), 149, 'C')

    @Test
    fun aSlimeThatHopsOntoTheDeckShaftSinksThroughIt() {
        val shaftMiddle = 146.5f * tile
        val deckTop = 16f * tile
        // A hop's height above the rim: it lands with nothing pushing it and must go down under its own weight.
        val slime = Slime(level.grid, shaftMiddle, deckTop - 24f)
        var step = 0
        while (step < 900 && slime.body.centerY() < 18f * tile) {
            level.advance(dt)
            slime.step(SlimeInput(), dt)
            step++
        }
        assertTrue(slime.body.centerY() >= 18f * tile, "still on the deck after $step steps, at y ${slime.body.centerY()}")
        assertTrue(slime.body.isFinite())
    }

    @Test
    fun theCorridorUnderTheDeckLeadsOutOfZoneC() {
        val slime = Slime(level.grid, 146.5f * tile, 20f * tile)
        assertReached(playFrom(slime, 'C', 157), 157, 'C')
    }

    @Test
    fun zoneDCanBeRunToTheFinish() = assertReached(play('D', 232), 232, 'D')
}
