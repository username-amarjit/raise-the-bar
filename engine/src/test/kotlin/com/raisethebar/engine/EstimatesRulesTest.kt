package com.raisethebar.engine

import com.raisethebar.config.Rules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** At least one test per estimate, weight and calendar rule in section 5. */
class EstimatesRulesTest {
    private val bench = Loads(LoadKind.BARBELL, (8..80).map { it * 2.5 }, Rules.STEP_BARBELL_UPPER_KG)

    @Test
    fun `EPLEY_DIVISOR and REPS_LEFT_MAX - 4+ is used as 4`() {
        assertEquals(100.0 * (1 + 10 / 30.0), Estimates.e1rm(100.0, 6, 4), 1e-9)
        assertEquals(Estimates.e1rm(100.0, 6, 4), Estimates.e1rm(100.0, 6, 7), 1e-9)
        assertEquals(0, Rules.RIR_BIAS)
    }

    @Test
    fun `BAD_DAY_RATIO - a dip above 92 percent moves halfway, below is a bad day`() {
        val est = Estimate(100.0, 70.0)
        val dip = assertNotNull(Estimates.afterSession(est, listOf(SetResult(70.0, 9, 2)), 70.0)) // 95.67
        assertEquals((100.0 + 70.0 * (1 + 11 / 30.0)) / 2, dip.e1rm, 1e-9)
        assertEquals(0, dip.badDays)
        val bad = assertNotNull(Estimates.afterSession(est, listOf(SetResult(70.0, 6, 2)), 70.0)) // 88.67
        assertEquals(100.0, bad.e1rm, 1e-9)
        assertEquals(1, bad.badDays)
    }

    @Test
    fun `BAD_DAYS_TO_COUNT - two bad days in a row move the estimate halfway down`() {
        val s = Estimates.e1rm(70.0, 6, 2)
        val first = assertNotNull(Estimates.afterSession(Estimate(100.0, 70.0), listOf(SetResult(70.0, 6, 2)), 70.0))
        val second = assertNotNull(Estimates.afterSession(first, listOf(SetResult(70.0, 6, 2)), 70.0))
        assertEquals(2, second.badDays)
        assertEquals((100.0 + s) / 2, second.e1rm, 1e-9)
    }

    @Test
    fun `gains count in full and currentWeight is the prescribed weight`() {
        val est = assertNotNull(Estimates.afterSession(Estimate(60.0, 45.0), listOf(SetResult(42.5, 12, 2)), 45.0))
        assertEquals(42.5 * (1 + 14 / 30.0), est.e1rm, 1e-9)
        assertEquals(45.0, est.currentWeight)
        assertEquals(1, est.sessions)
    }

    @Test
    fun `FIRST_SET_GAP - 2 or more easier moves the rest up a step, 1 off changes nothing`() {
        val up = Estimates.afterFirstSet(60.0, SetResult(45.0, 10, 2), bench) // 12 vs predicted 10
        assertEquals(2, up.gap)
        assertEquals(47.5, up.newWeight)
        val same = Estimates.afterFirstSet(60.0, SetResult(45.0, 9, 2), bench)
        assertEquals(1, same.gap)
        assertNull(same.newWeight)
    }

    @Test
    fun `REP_DROP_PER_SET - sets 2 and 3 target one rep fewer each`() {
        val p = assertNotNull(Estimates.nextWeight(Estimate(60.0, 45.0), 6, 8, 3, 3, bench))
        assertEquals(listOf(7, 6, 5), (1..3).map(p::target))
    }

    @Test
    fun `FIRST_TARGET_ABOVE_LOW - a first prescription aims at the bottom of the range plus 1`() {
        val p = assertNotNull(Estimates.nextWeight(Estimate(60.0, null), 6, 8, 3, 3, bench))
        assertEquals(45.0, p.weight) // 47.5 predicts 5; 45 predicts 7 = 6 + 1
        assertTrue(Estimates.predictedReps(60.0, p.weight, 3) >= 6 + Rules.FIRST_TARGET_ABOVE_LOW)
    }

    @Test
    fun `ramp - 4+ left jumps about 20 percent, 3 left offers That's my weight, 2 left stops`() {
        val jump = Ramp.after(1, SetResult(40.0, 8, 4), bench)
        assertEquals(Ramp.Step.Next(47.5, canConfirm = false), jump) // 48 rounded down to 47.5
        val three = Ramp.after(1, SetResult(40.0, 8, 3), bench)
        assertTrue(three is Ramp.Step.Next && three.canConfirm && three.suggested >= 42.5)
        assertEquals(Ramp.Step.Done(40.0), Ramp.after(1, SetResult(40.0, 8, 2), bench))
        assertEquals(Ramp.Step.Done(60.0), Ramp.after(Rules.RAMP_MAX_SETS, SetResult(60.0, 8, 4), bench))
        assertEquals(20.0, Ramp.start(bench))
    }

    @Test
    fun `deload - half the sets rounded up, 90 percent rounded down, stop with 4 left`() {
        val p = assertNotNull(Estimates.deload(Estimate(63.0, 47.5), 47.5, 3, bench))
        assertEquals(42.5, p.weight) // 42.75 rounded down
        assertEquals(2, p.sets)
        assertEquals(Rules.DELOAD_LEFT, p.targetLeft)
    }

    @Test
    fun `holds - add 5 s to both ends once every set reaches the top, up to 90 s`() {
        assertEquals(30, Holds.target(30, 60, 0))
        assertEquals(0, Holds.nextOffset(30, 60, 0, listOf(60, 55)))
        assertEquals(5, Holds.nextOffset(30, 60, 0, listOf(60, 62)))
        assertEquals(35 to 65, Holds.range(30, 60, 5))
        assertEquals(30, Holds.nextOffset(30, 60, 30, listOf(90, 90)))
    }

    @Test
    fun `outgrown - the heaviest weight and still above the range`() {
        val dumbbells = Loads(LoadKind.DUMBBELL, listOf(5.0, 7.5, 10.0), 0.0)
        val p = assertNotNull(Estimates.nextWeight(Estimate(20.0, 10.0), 10, 12, 2, 3, dumbbells))
        assertEquals(10.0, p.weight)
        assertTrue(p.outgrown)
        assertFalse(p.tooBigJump)
    }

    @Test
    fun `calendar - weeks 1 and 2 calibrate, then 6 block weeks and a deload every 7 weeks`() {
        val start = java.time.LocalDate.of(2026, 10, 19)
        assertEquals(1, Calendar.planWeek(start, start))
        assertEquals(1, Calendar.planWeek(start, start.plusDays(6)))
        assertEquals(2, Calendar.planWeek(start, start.plusDays(7)))
        assertEquals(Phase.Calibration(1), Calendar.phase(1))
        assertEquals(Phase.Block(1, 1), Calendar.phase(3))
        assertEquals(Phase.Block(1, 6), Calendar.phase(8))
        listOf(9, 16, 23).forEach { assertEquals(Phase.Deload((it - 2) / 7), Calendar.phase(it)) }
        assertEquals(Phase.Block(2, 1), Calendar.phase(10))
        assertEquals("Calibration, week 1", Calendar.phase(1).label)
        assertEquals("Block 1, week 3", Calendar.phase(5).label)
        assertEquals("Deload week", Calendar.phase(9).label)
    }

    @Test
    fun `effort ramp - big and small lifts per week`() {
        fun left(w: Int) = Calendar.targetLeft(Calendar.phase(w), true) to Calendar.targetLeft(Calendar.phase(w), false)
        assertEquals(3 to 3, left(2))
        assertEquals(3 to 2, left(3))
        assertEquals(3 to 2, left(4))
        assertEquals(2 to 1, left(5))
        assertEquals(2 to 1, left(6))
        assertEquals(1 to 1, left(7))
        assertEquals(1 to 0, left(8))
        assertEquals(4 to 4, left(9))
        assertEquals(2, Calendar.workingSets(Calendar.phase(1), 3))
        assertEquals(2, Calendar.workingSets(Calendar.phase(2), 3))
        assertEquals(4, Calendar.workingSets(Calendar.phase(3), 4))
        assertEquals(2, Calendar.workingSets(Calendar.phase(9), 3))
        assertEquals(2, Calendar.workingSets(Calendar.phase(9), 4))
    }
}
