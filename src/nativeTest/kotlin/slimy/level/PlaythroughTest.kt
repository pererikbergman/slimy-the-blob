package slimy.level

import slimy.GameSession
import slimy.physics.Slime
import kotlin.test.Test
import kotlin.test.assertFalse
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

    /** Runs from the sign of [zone] until the slime's middle passes [targetCol] or [maxSteps] pass; returns where it got to. */
    private fun play(zone: Char, targetCol: Int, maxSteps: Int = 3000): Slime {
        val session = GameSession(level)
        session.restartInZone(zone)
        return playFrom(session, zone, targetCol, maxSteps)
    }

    /** Runs the bot on [session] where its slime already is, until it passes [targetCol] or [maxSteps] pass. */
    private fun playFrom(session: GameSession, zone: Char, targetCol: Int, maxSteps: Int = 3000): Slime {
        val slime = session.slime
        session.setMove(1f)
        session.setJumpHeld(true)
        var step = 0
        while (step < maxSteps && slime.body.centerX() < targetCol * tile) {
            val front = slime.body.maxX()
            val feet = slime.body.maxY()
            val wallAhead = level.grid.isSolidAt(front + 4f, feet - 6f)
            val groundEnds = !level.grid.isSolidAt(front + 6f, feet + 6f) && !level.grid.isSolidAt(front + 6f, feet + 22f)
            if (slime.isGrounded && (wallAhead || groundEnds)) session.pressJump()
            assertFalse(session.step(dt), "zone $zone: the slime fell out of the level at column ${slime.body.centerX() / tile}")
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
        val session = GameSession(level)
        session.slime.reset(shaftMiddle, deckTop - 24f)
        var step = 0
        while (step < 900 && session.slime.body.centerY() < 18f * tile) {
            session.step(dt)
            step++
        }
        assertTrue(session.slime.body.centerY() >= 18f * tile, "still on the deck after $step steps, at y ${session.slime.body.centerY()}")
        assertTrue(session.slime.body.isFinite())
    }

    @Test
    fun theCorridorUnderTheDeckLeadsOutOfZoneC() {
        val session = GameSession(level)
        session.slime.reset(146.5f * tile, 20f * tile)
        assertReached(playFrom(session, 'C', 157), 157, 'C')
    }

    @Test
    fun zoneDCanBeRunToTheFinish() = assertReached(play('D', 232), 232, 'D')
}
