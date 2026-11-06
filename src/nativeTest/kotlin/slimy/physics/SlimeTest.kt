package slimy.physics

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlimeTest {
    private val dt = 1f / 60f
    private val floorTop = 15 * SolidGrid.TILE

    /** 40 x 20 tiles with a two-tile floor whose top is y = 240; [bouncy] columns get a bounce pad on top. */
    private fun grid(bouncy: IntRange = IntRange.EMPTY): SolidGrid = SolidGrid(40, 20).apply {
        for (c in 0 until 40) {
            add(Solid.tile(c, 15, isBouncy = c in bouncy))
            add(Solid.tile(c, 16))
        }
    }

    private fun settled(grid: SolidGrid = grid()): Slime {
        val slime = Slime(grid, startX = 320f, startBaseY = floorTop - 40f)
        repeat(120) { slime.step(SlimeInput(), dt) }
        return slime
    }

    @Test
    fun aDroppedSlimeSettlesOnTheFloorWithoutSinkingIn() {
        val slime = settled()
        assertTrue(slime.isGrounded)
        assertTrue(slime.body.isFinite())
        assertTrue(slime.body.maxY() <= floorTop + 0.5f, "base at ${slime.body.maxY()}")
        assertTrue(slime.body.maxY() >= floorTop - 1f, "hovering at ${slime.body.maxY()}")
    }

    @Test
    fun aJumpPressedThisStepIsRisingByTheEndOfThatStep() {
        val slime = settled()
        val before = slime.body.centerY()
        val input = SlimeInput(jumpPressed = true, jumpHeld = true)
        slime.step(input, dt)
        assertTrue(slime.hasJumped)
        assertTrue(slime.body.centerY() < before - 2f, "moved ${slime.body.centerY() - before}")
        assertFalse(slime.isGrounded)
    }

    @Test
    fun aHeldJumpLiftsTheBaseAtLeastTwoTiles() {
        val slime = settled()
        var highest = slime.body.maxY()
        slime.step(SlimeInput(jumpPressed = true, jumpHeld = true), dt)
        repeat(60) {
            slime.step(SlimeInput(jumpHeld = true), dt)
            if (slime.body.maxY() < highest) highest = slime.body.maxY()
        }
        assertTrue(floorTop - highest >= 2 * SolidGrid.TILE, "lifted ${floorTop - highest}")
    }

    @Test
    fun aTappedJumpIsShorterThanAHeldOne() {
        val held = settled()
        val tapped = settled()
        var heldTop = held.body.maxY()
        var tappedTop = tapped.body.maxY()
        held.step(SlimeInput(jumpPressed = true, jumpHeld = true), dt)
        tapped.step(SlimeInput(jumpPressed = true, jumpHeld = false), dt)
        repeat(60) {
            held.step(SlimeInput(jumpHeld = true), dt)
            tapped.step(SlimeInput(), dt)
            heldTop = minOf(heldTop, held.body.maxY())
            tappedTop = minOf(tappedTop, tapped.body.maxY())
        }
        assertTrue(tappedTop > heldTop + 8f, "held $heldTop tapped $tappedTop")
    }

    @Test
    fun aJumpPressedJustBeforeLandingStillHappens() {
        val slime = Slime(grid(), startX = 320f, startBaseY = floorTop - 4f)
        slime.step(SlimeInput(jumpPressed = true), dt)
        var jumped = false
        repeat(10) {
            slime.step(SlimeInput(), dt)
            if (slime.hasJumped) jumped = true
        }
        assertTrue(jumped)
    }

    @Test
    fun holdingRightMovesTheSlimeRightAndLettingGoStopsIt() {
        val slime = settled()
        val start = slime.body.centerX()
        repeat(60) { slime.step(SlimeInput(move = 1f), dt) }
        assertTrue(slime.body.centerX() > start + 50f, "moved ${slime.body.centerX() - start}")
        assertEquals(1, slime.facing)
        repeat(90) { slime.step(SlimeInput(), dt) }
        assertTrue(abs(slime.body.meanVelocityX(dt / Slime.SUBSTEPS)) < 5f)
        assertTrue(slime.body.isFinite())
    }

    @Test
    fun theSlimeNeverRunsFasterThanTheTopSpeed() {
        val slime = settled()
        repeat(180) { slime.step(SlimeInput(move = 1f), dt) }
        val speed = slime.body.meanVelocityX(dt / Slime.SUBSTEPS)
        assertTrue(speed <= Slime.MAX_SPEED * 1.15f, "speed $speed")
    }

    @Test
    fun aBouncePadThrowsTheSlimeHigherThanAJump() {
        val padTop = floorTop
        val slime = Slime(grid(bouncy = 18..22), startX = 320f, startBaseY = padTop - 100f)
        var bounced = false
        var highest = Float.MAX_VALUE
        repeat(240) {
            slime.step(SlimeInput(), dt)
            if (slime.hasBounced) bounced = true
            if (bounced) highest = minOf(highest, slime.body.maxY())
        }
        assertTrue(bounced)
        assertTrue(padTop - highest > 100f, "rose ${padTop - highest}")
    }
}
