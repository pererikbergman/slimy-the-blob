package slimy.physics

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContactsTest {
    /** Five particles, of which the first four are the outline and the fifth is the core. */
    private fun contacts() = Contacts(particleCount = 5, outlineCount = 4)

    @Test
    fun aFloorTouchIsSeenThisStepButOnlyRememberedAsLastStepAfterTheNextBegins() {
        val c = contacts()
        c.beginStep()
        c.recordFloor(1, bouncy = false)
        assertTrue(c.touchedFloor)
        assertTrue(c.isOnFloor(1))
        assertFalse(c.wasOnFloor(1), "this step's touch is not last step's")
        c.beginStep()
        assertTrue(c.wasOnFloor(1))
        assertFalse(c.touchedFloor, "the new step starts with nothing touched")
        assertFalse(c.isOnFloor(1))
    }

    @Test
    fun aTouchInAnySubstepOfTheStepCounts() {
        val c = contacts()
        c.beginStep()
        c.recordFloor(0, bouncy = false)
        c.recordFloor(2, bouncy = false)
        assertTrue(c.isOnFloor(0))
        assertTrue(c.isOnFloor(2))
        assertFalse(c.isOnFloor(1))
    }

    @Test
    fun theCoreNeverCountsAsBeingOnTheFloor() {
        val c = contacts()
        c.beginStep()
        c.recordFloor(4, bouncy = false)
        c.beginStep()
        assertFalse(c.wasOnFloor(4))
    }

    @Test
    fun aBouncyTouchIsReportedForTheStepOnly() {
        val c = contacts()
        c.beginStep()
        c.recordFloor(0, bouncy = true)
        assertTrue(c.touchedBouncePad)
        c.beginStep()
        assertFalse(c.touchedBouncePad)
    }

    @Test
    fun clearForgetsEverythingIncludingLastStep() {
        val c = contacts()
        c.beginStep()
        c.recordFloor(0, bouncy = true)
        c.beginStep()
        c.clear()
        assertFalse(c.wasOnFloor(0))
        assertFalse(c.touchedFloor)
        assertFalse(c.touchedBouncePad)
    }
}
