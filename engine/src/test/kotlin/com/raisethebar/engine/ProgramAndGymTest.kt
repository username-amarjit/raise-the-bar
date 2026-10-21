package com.raisethebar.engine

import com.raisethebar.config.Rules
import com.raisethebar.engine.Fixtures.with
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Milestone 3: for any date, the right workout, block week, effort target and sets for every slot. */
class ProgramPlannerTest {
    private val program = Fixtures.program

    @Test
    fun `rotation runs Push A to Legs B and only advances on completed sessions`() {
        assertEquals(listOf("Push A", "Pull A", "Legs A", "Push B", "Pull B", "Legs B", "Push A"), (0..6).map { program.rotationDay(it).name })
        assertTrue(SessionKind.advancesRotation(SessionKind.FULL))
        assertTrue(SessionKind.advancesRotation(SessionKind.MINIMUM))
        assertTrue(SessionKind.advancesRotation(SessionKind.HOME))
        assertFalse(SessionKind.advancesRotation(SessionKind.AWAY))
        assertFalse(SessionKind.advancesRotation(SessionKind.PRE_TRIP))
    }

    @Test
    fun `future planned dates get the next workouts, skipping Sundays`() {
        val isPlanned = { d: java.time.LocalDate -> Days.isPlanned(d, Fixtures.schedule, emptyList(), program) }
        val mon = Fixtures.monday
        assertEquals(0, Days.rotationIndexOn(mon, mon, 0, isPlanned, doneToday = false))
        assertEquals(1, Days.rotationIndexOn(mon.plusDays(1), mon, 0, isPlanned, doneToday = false))
        assertEquals(5, Days.rotationIndexOn(mon.plusDays(5), mon, 0, isPlanned, doneToday = false))
        assertEquals(6, Days.rotationIndexOn(mon.plusDays(7), mon, 0, isPlanned, doneToday = false)) // Sunday is rest
        assertEquals(1, Days.rotationIndexOn(mon.plusDays(1), mon, 1, isPlanned, doneToday = true))
        assertEquals(DayStatus.Rest, Days.status(mon.plusDays(6), Fixtures.schedule, emptyList(), program))
    }

    @Test
    fun `calibration week 1 ramps every new exercise with 2 working sets and no warm-up`() {
        val plan = Planner.build(program.day("push_a"), SessionKind.FULL, Fixtures.input())
        assertEquals("Calibration, week 1", plan.phaseLabel)
        assertEquals(6, plan.exercises.size)
        plan.exercises.forEach { e ->
            assertEquals(2, e.sets, e.exerciseId)
            assertTrue(e.ramp, e.exerciseId)
            assertTrue(e.warmups.isEmpty())
        }
        assertEquals(20.0, plan.exercises.first().rampStart)
    }

    @Test
    fun `block week 2 prescribes weights, effort targets, programmed sets and warm-ups on the first lift`() {
        val date = Fixtures.monday.plusWeeks(3) // week 4 = block week 2
        val est = mapOf("barbell_bench_press" to Estimate(63.0, 45.0), "incline_dumbbell_press" to Estimate(30.0, 20.0))
        val plan = Planner.build(program.day("push_a"), SessionKind.FULL, Fixtures.input(date, est))
        assertEquals("Block 1, week 2", plan.phaseLabel)
        val bench = plan.exercises[0]
        assertEquals(47.5 to 7, bench.weight to bench.set1Target)
        assertEquals(3, bench.targetLeft)
        assertEquals(3, bench.sets)
        assertTrue(bench.wentUp)
        assertEquals(listOf(Warmup(22.5, 8), Warmup(32.5, 4), Warmup(40.0, 2)), bench.warmups)
        assertTrue(plan.exercises[1].warmups.isEmpty())
        assertEquals(3, plan.exercises[1].targetLeft) // incline dumbbell press is big
        val lateral = plan.exercises.first { it.exerciseId == "dumbbell_lateral_raise" }
        assertEquals(2, lateral.targetLeft) // small lift in block weeks 1–2
        assertEquals(75, lateral.restS)
    }

    @Test
    fun `deload week halves the sets, takes 90 percent rounded down and stops with 4 left`() {
        val date = Fixtures.monday.plusWeeks(8) // week 9
        val plan = Planner.build(program.day("push_a"), SessionKind.FULL, Fixtures.input(date, mapOf("barbell_bench_press" to Estimate(70.0, 50.0))))
        assertEquals(SessionKind.DELOAD, plan.kind)
        val bench = plan.exercises[0]
        assertEquals(45.0, bench.weight)
        assertEquals(2, bench.sets)
        assertEquals(Rules.DELOAD_LEFT, bench.targetLeft)
        assertEquals(1, Calendar.deloadSets(2))
    }

    @Test
    fun `minimum session is the first three exercises, 2 sets each`() {
        val plan = Planner.build(program.day("legs_a"), SessionKind.MINIMUM, Fixtures.input(Fixtures.monday.plusWeeks(3)))
        assertEquals(listOf("barbell_back_squat", "leg_press", "leg_extension"), plan.exercises.map { it.exerciseId })
        assertTrue(plan.exercises.all { it.sets == 2 })
    }

    @Test
    fun `home version for each day type has no weights`() {
        val push = Planner.build(program.homeDay(DayType.PUSH)!!, SessionKind.HOME, Fixtures.input())
        assertEquals(listOf("push_ups", "pike_push_ups", "chair_dips", "plank"), push.exercises.map { it.exerciseId })
        assertEquals(2, push.exercises[0].targetLeft)
        assertNull(push.exercises[0].repLow)
        assertEquals(45 to 45, push.exercises[3].secLow to push.exercises[3].secHigh)
        assertTrue(push.exercises.all { it.weight == null })
        assertEquals(4, program.homeDay(DayType.PULL)!!.slots.size)
        assertEquals(4, program.homeDay(DayType.LEGS)!!.slots.size)
    }

    @Test
    fun `timed holds target the bottom of the range`() {
        val plan = Planner.build(program.day("legs_a"), SessionKind.FULL, Fixtures.input(Fixtures.monday.plusWeeks(3)))
        val plank = plan.exercises.last()
        assertEquals("plank", plank.exerciseId)
        assertEquals(30 to 60, plank.secLow to plank.secHigh)
        assertEquals(2, plank.sets)
    }

    @Test
    fun `a broken machine swaps in the backup with a note`() {
        val gym = Fixtures.gym.with("chest_supported_row_machine", EquipmentStatus.BROKEN)
        val plan = Planner.build(program.day("pull_b"), SessionKind.FULL, Fixtures.input(Fixtures.monday.plusWeeks(3), gym = gym))
        val slot3 = plan.exercises.first { it.slotId == "pull_b_3" }
        assertEquals("chest_supported_dumbbell_row", slot3.exerciseId)
        assertEquals("Chest-supported row machine broken: backup 1", slot3.note)
    }
}

/** Milestone 5: available weights, steps and exercise selection. */
class GymSelectionTest {
    private val gym = Fixtures.gym

    @Test
    fun `barbell weights are the bar plus any combination of plate pairs, minus missing plates`() {
        val bar = gym.getValue("barbell")
        val all = Gym.barbellWeights(bar, gym.getValue("plates"))
        assertTrue(listOf(20.0, 22.5, 25.0, 47.5, 100.0).all { it in all })
        val noSmall = Gym.barbellWeights(bar, gym.getValue("plates").copy(missingKg = listOf(1.25)))
        assertFalse(22.5 in noSmall)
        assertTrue(25.0 in noSmall)
    }

    @Test
    fun `steps - 2_5 kg upper body, 5 kg lower body, next dumbbell pair, next stack plate, null at the ends`() {
        val bench = Gym.loadsFor(Fixtures.exercises.getValue("barbell_bench_press"), gym, 70.0)
        assertEquals(47.5, bench.stepUp(45.0))
        assertEquals(42.5, bench.stepDown(45.0))
        val squat = Gym.loadsFor(Fixtures.exercises.getValue("barbell_back_squat"), gym, 70.0)
        assertEquals(65.0, squat.stepUp(60.0))
        assertEquals(55.0, squat.stepDown(60.0))
        val db =
            Gym.loadsFor(
                Fixtures.exercises.getValue("dumbbell_curl"),
                gym + ("dumbbells" to gym.getValue("dumbbells").copy(missingKg = listOf(12.5))),
                70.0,
            )
        assertEquals(15.0, db.stepUp(10.0))
        assertNull(db.stepUp(30.0))
        assertNull(db.stepDown(2.5))
        val press = Gym.loadsFor(Fixtures.exercises.getValue("leg_press"), gym, 70.0)
        assertEquals(45.0, press.stepUp(40.0))
        assertNull(press.stepDown(40.0))
        assertNull(press.stepUp(300.0))
    }

    @Test
    fun `bodyweight exercises use effective load - body weight plus added minus assistance`() {
        val pull = Gym.loadsFor(Fixtures.exercises.getValue("pull_ups"), gym, 70.0)
        assertEquals(70.0, pull.lightest)
        assertEquals(5.0, pull.toDisplay(75.0))
        val assisted = Gym.loadsFor(Fixtures.exercises.getValue("assisted_pull_up"), gym, 70.0)
        assertEquals(30.0, assisted.toDisplay(40.0))
        assertEquals(40.0, assisted.fromDisplay(30.0))
    }

    @Test
    fun `leg press broken uses the hack squat, or walking lunges without one, and fixed restores it`() {
        val program = Fixtures.program
        val slot = program.slot("legs_a_2")!!

        fun pickWith(g: Map<String, Equipment>) = Selection.pick(slot, DayType.LEGS, Fixtures.ctx(gym = g), program)
        val broken = gym.with("leg_press", EquipmentStatus.BROKEN)
        assertEquals("hack_squat", pickWith(broken).exerciseId)
        assertEquals("Leg press broken: backup 1", pickWith(broken).note)
        assertEquals("walking_lunges", pickWith(broken.with("hack_squat", EquipmentStatus.GONE)).exerciseId)
        assertEquals("leg_press", pickWith(broken.with("leg_press", EquipmentStatus.WORKING)).exerciseId)
    }

    @Test
    fun `busy lasts for today only`() {
        val program = Fixtures.program
        val busy = gym.with("lat_pulldown", EquipmentStatus.BUSY, Fixtures.monday)
        val slot = program.slot("pull_a_1")!!
        assertEquals("pull_ups", Selection.pick(slot, DayType.PULL, Fixtures.ctx(Fixtures.monday, busy), program).exerciseId)
        assertEquals("lat_pulldown", Selection.pick(slot, DayType.PULL, Fixtures.ctx(Fixtures.monday.plusDays(1), busy), program).exerciseId)
    }

    @Test
    fun `a promoted backup becomes the first choice, and today-only swaps use the next backup`() {
        val program = Fixtures.program.withPromotions(mapOf("pull_a_1" to "pull_ups"))
        val slot = program.slot("pull_a_1")!!
        assertEquals(listOf("pull_ups", "lat_pulldown", "assisted_pull_up"), slot.ranked)
        assertEquals("pull_ups", Selection.pick(slot, DayType.PULL, Fixtures.ctx(), program).exerciseId)
        val swapped = Selection.pick(slot, DayType.PULL, Fixtures.ctx(skipToday = setOf("pull_ups")), program)
        assertEquals("lat_pulldown", swapped.exerciseId)
    }

    @Test
    fun `nothing available falls back to the day's home version`() {
        val program = Fixtures.program
        val none = gym.with("cable_station", EquipmentStatus.BROKEN).with("pec_deck", EquipmentStatus.GONE).with("dumbbells", EquipmentStatus.GONE)
        val pick = Selection.pick(program.slot("push_a_4")!!, DayType.PUSH, Fixtures.ctx(gym = none), program)
        assertEquals(Pick.Source.HOME, pick.source)
        assertEquals("push_ups", pick.exerciseId)
    }

    @Test
    fun `report it message, fixed-yet prompt every 3 days, and next-exercise-first only with different equipment`() {
        assertEquals(
            "Hi, the leg press in the office gym has been out of order since Monday 19 October. Could someone take a look? Thanks.",
            Gym.reportMessage("Leg press", Fixtures.monday),
        )
        val broken = gym.with("leg_press", EquipmentStatus.BROKEN).getValue("leg_press")
        assertFalse(Gym.shouldAskFixed(broken, null, Fixtures.monday.plusDays(2)))
        assertTrue(Gym.shouldAskFixed(broken, null, Fixtures.monday.plusDays(3)))
        assertFalse(Gym.shouldAskFixed(broken, Fixtures.monday.plusDays(3), Fixtures.monday.plusDays(5)))
        val ex = Fixtures.exercises
        assertTrue(Gym.canDoNextFirst(ex.getValue("leg_press"), ex.getValue("leg_extension")))
        assertFalse(Gym.canDoNextFirst(ex.getValue("leg_press"), ex.getValue("leg_press_calf_raise")))
    }
}
