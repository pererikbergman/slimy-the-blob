package slimy

import slimy.level.Level
import slimy.level.TestLevel
import slimy.physics.Outline
import slimy.physics.SolidGrid
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameSessionTest {
    private val dt = 1f / 60f

    private fun session() = GameSession(TestLevel.level)

    private fun GameSession.settle() = repeat(120) { step(dt) }

    @Test
    fun aJumpPressedBeforeAStepHappensInThatStepAndOnlyThatOne() {
        val s = session().also { it.settle() }
        s.pressJump()
        s.step(dt)
        assertTrue(s.slime.hasJumped)
        s.step(dt)
        assertFalse(s.slime.hasJumped, "one press made a second jump")
    }

    @Test
    fun aPressWaitsForTheNextStepEvenIfTimePassesFirst() {
        val s = session().also { it.settle() }
        s.pressJump()
        // nothing happens until the game steps; the press is still there when it does
        assertFalse(s.slime.hasJumped)
        s.step(dt)
        assertTrue(s.slime.hasJumped)
    }

    @Test
    fun restartingInAZonePutsTheSlimeBesideItsSignAndAtTheStartWhenThereIsNoSuchZone() {
        val s = session()
        val zoneC = assertNotNull(s.level.zone('C'))
        s.restartInZone('C')
        assertEquals(zoneC.x + GameSession.RESTART_OFFSET, s.slime.body.centerX(), 1f)
        s.restartInZone(null)
        assertEquals(assertNotNull(s.level.start).x, s.slime.body.centerX(), 1f)
        s.restartInZone('Z')
        assertEquals(assertNotNull(s.level.start).x, s.slime.body.centerX(), 1f)
    }

    @Test
    fun aSlimeThatFallsOutOfTheLevelIsPutBackAtTheStartOfItsZone() {
        val s = session()
        val zoneB = assertNotNull(s.level.zone('B'))
        s.slime.reset(zoneB.x + 40f, s.level.height + GameSession.FALL_MARGIN + 80f)
        assertTrue(s.step(dt), "the fall was not noticed")
        assertEquals('B', s.zone?.id)
        assertEquals(zoneB.x + GameSession.RESTART_OFFSET, s.slime.body.centerX(), 2f)
        assertTrue(s.slime.body.centerY() < s.level.height, "still below the level")
        assertFalse(s.step(dt), "a slime on solid ground was reported as fallen")
    }

    @Test
    fun theSessionStepsTheCurtainsSoASlimeStandingInOneMovesIt() {
        // a ceiling, a curtain hanging under it, and a floor close enough that the slime stands among the strands
        val level = Level.parse(listOf("######", "rrrr..", ".....S", "######"))
        val s = GameSession(level)
        val curtain = s.curtains.single()
        s.slime.reset(2 * SolidGrid.TILE, 3 * SolidGrid.TILE)
        var farthest = 0f
        repeat(30) {
            s.step(dt)
            for (i in 0 until curtain.count) {
                val restX = curtain.left + (i % curtain.columns) * SolidGrid.TILE
                farthest = maxOf(farthest, abs(curtain.x[i] - restX))
            }
        }
        assertTrue(farthest > 3f, "the curtain was not pushed: farthest from rest $farthest")
        for (i in curtain.columns until curtain.count) {
            assertFalse(Outline.contains(s.slime.body, curtain.x[i], curtain.y[i]), "curtain particle $i ended up inside the slime")
        }
    }

    @Test
    fun theLevelIsFinishedOnlyOnceTheSlimeIsPastTheFinishSign() {
        val s = session()
        assertFalse(s.isFinished)
        val finish = assertNotNull(s.level.finish)
        s.slime.reset(finish.x + 40f, finish.y)
        assertTrue(s.isFinished)
    }
}
