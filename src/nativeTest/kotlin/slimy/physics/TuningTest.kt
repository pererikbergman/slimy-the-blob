package slimy.physics

import kotlin.test.Test
import kotlin.test.assertTrue

class TuningTest {
    @Test
    fun aSubstepCannotCrossHalfATile() {
        assertTrue(Tuning.MAX_STEP < Tuning.TILE / 2)
    }
}
