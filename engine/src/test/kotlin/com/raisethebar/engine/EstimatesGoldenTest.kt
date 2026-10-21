package com.raisethebar.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Section 13.1 and 13.2 golden tests. */
class EstimatesGoldenTest {
    /** Barbell, upper body: 2.5 kg steps (1.25 kg a side), plenty of plates. */
    private val bench = Loads(LoadKind.BARBELL, (8..80).map { it * 2.5 }, 2.5)

    private fun left(week: Int) = Calendar.targetLeft(Calendar.phase(week), big = true)

    private fun prescribe(est: Estimate, week: Int, loads: Loads = bench) =
        assertNotNull(Estimates.nextWeight(est, repLow = 6, repHigh = 8, left = left(week), sets = 3, loads = loads))

    @Test
    fun `13_1 bench press 3 x 6-8 across seven weeks`() {
        // Week 1: the ramp. 40 x 8 with 3 left, then 45 x 8 with 2 left.
        val ramp = listOf(SetResult(40.0, 8, 3), SetResult(45.0, 8, 2))
        assertTrue(Ramp.after(1, ramp[0], bench) is Ramp.Step.Next)
        assertEquals(Ramp.Step.Done(45.0), Ramp.after(2, ramp[1], bench))
        var est = Estimate(assertNotNull(Ramp.startingEstimate(ramp)), currentWeight = 45.0, sessions = 1)
        assertEquals(60.0, est.e1rm, 1e-9)

        // Week 2: 3 left -> 45 kg x 7. Result 45 x 8, 3 left -> 61.5.
        val w2 = prescribe(est, 2)
        assertEquals(45.0 to 7, w2.weight to w2.set1Target)
        est = assertNotNull(Estimates.afterSession(est, listOf(SetResult(45.0, 8, 3)), w2.weight))
        assertEquals(61.5, est.e1rm, 1e-9)

        // Week 3: 3 left -> 45 kg x 8. Result 45 x 9, 3 left -> 63.0.
        val w3 = prescribe(est, 3)
        assertEquals(45.0 to 8, w3.weight to w3.set1Target)
        est = assertNotNull(Estimates.afterSession(est, listOf(SetResult(45.0, 9, 3)), w3.weight))
        assertEquals(63.0, est.e1rm, 1e-9)

        // Week 4: 3 left -> 47.5 kg x 7, weight up.
        val w4 = prescribe(est, 4)
        assertEquals(47.5 to 7, w4.weight to w4.set1Target)
        assertTrue(w4.wentUp)
        // First-set check: 47.5 x 4, 2 left -> gap -4, so sets 2 and 3 drop to 45 kg.
        val check = Estimates.afterFirstSet(est.e1rm, SetResult(47.5, 4, 2), bench)
        assertEquals(-4, check.gap)
        assertEquals(45.0, check.newWeight)
        // Best sets 47.5 x 4 and 45 x 6, both 2 left (57.0) < 57.96: bad day, estimate stays 63.0, bad days 1.
        est = assertNotNull(Estimates.afterSession(est, listOf(SetResult(47.5, 4, 2), SetResult(45.0, 6, 2)), w4.weight))
        assertEquals(63.0, est.e1rm, 1e-9)
        assertEquals(1, est.badDays)
        assertEquals(47.5, est.currentWeight)

        // Week 5: 2 left -> 47.5 kg x 8. Result 47.5 x 8, 2 left -> 63.33 and bad days 0.
        val w5 = prescribe(est, 5)
        assertEquals(47.5 to 8, w5.weight to w5.set1Target)
        est = assertNotNull(Estimates.afterSession(est, listOf(SetResult(47.5, 8, 2)), w5.weight))
        assertEquals(63.33, est.e1rm, 0.005)
        assertEquals(0, est.badDays)

        // Week 6: 2 left -> 47.5 kg x 8. Result 47.5 x 9, 2 left -> 64.92.
        val w6 = prescribe(est, 6)
        assertEquals(47.5 to 8, w6.weight to w6.set1Target)
        est = assertNotNull(Estimates.afterSession(est, listOf(SetResult(47.5, 9, 2)), w6.weight))
        assertEquals(64.92, est.e1rm, 0.005)

        // Week 7: 1 left -> 50 kg x 8, weight up.
        val w7 = prescribe(est, 7)
        assertEquals(50.0 to 8, w7.weight to w7.set1Target)
        assertTrue(w7.wentUp)
    }

    private fun fromSingleSet(set: SetResult) = Estimate(Estimates.e1rm(set.weight, set.reps, set.left), set.weight)

    @Test
    fun `13_2 45 x 9 with 3 left gives 63 and then 47_5 x 7`() {
        val est = fromSingleSet(SetResult(45.0, 9, 3))
        assertEquals(63.0, est.e1rm, 1e-9)
        val p = assertNotNull(Estimates.nextWeight(est, 6, 8, 3, 3, bench))
        assertEquals(47.5 to 7, p.weight to p.set1Target)
    }

    @Test
    fun `13_2 45 x 4 with 2 left gives 54 and then 40 x 8`() {
        val est = fromSingleSet(SetResult(45.0, 4, 2))
        assertEquals(54.0, est.e1rm, 1e-9)
        val p = assertNotNull(Estimates.nextWeight(est, 6, 8, 3, 3, bench))
        assertEquals(40.0 to 8, p.weight to p.set1Target)
    }

    @Test
    fun `13_2 7_5 x 15 with 2 left stays at 7_5 x 15 because 10 kg is too big a jump`() {
        val dumbbells = Loads(LoadKind.DUMBBELL, listOf(2.5, 5.0, 7.5, 10.0, 12.5), 0.0)
        val est = fromSingleSet(SetResult(7.5, 15, 2))
        assertEquals(11.75, est.e1rm, 1e-9)
        val p = assertNotNull(Estimates.nextWeight(est, 10, 12, 2, 3, dumbbells))
        assertEquals(7.5 to 15, p.weight to p.set1Target)
        assertTrue(p.tooBigJump)
        assertFalse(p.outgrown)
    }
}
