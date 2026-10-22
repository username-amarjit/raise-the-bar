package com.raisethebar.engine

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Section 13.3: skips and the timeout (training Monday to Saturday at 07:30, cut-off 22:00), in India time. */
class SkipsGoldenTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val tuesday = LocalDate.of(2026, 10, 20)
    private val wednesday = tuesday.plusDays(1)
    private val thursday = tuesday.plusDays(2)
    private val cutoff = LocalTime.of(22, 0)

    private fun at(date: LocalDate, h: Int, m: Int): ZonedDateTime = date.atTime(h, m).atZone(zone)

    private fun off(z: ZonedDateTime): OffsetDateTime = z.toOffsetDateTime()

    @Test
    fun `nothing logged and no reason - the day becomes an unexcused skip`() {
        assertEquals(Outcome.SKIP, Skips.outcome(tuesday, DayStatus.Planned, false, null, null, zone))
    }

    @Test
    fun `sick recorded Tuesday at 21_00 excuses Tuesday`() {
        assertTrue(Skips.canRecordReason(tuesday, at(tuesday, 21, 0)))
        assertEquals(Outcome.EXCUSED, Skips.outcome(tuesday, DayStatus.Planned, false, Reason.SICK, off(at(tuesday, 21, 0)), zone))
    }

    @Test
    fun `a reason for Tuesday entered Wednesday at 00_10 is rejected and Tuesday stays a skip`() {
        assertFalse(Skips.canRecordReason(tuesday, at(wednesday, 0, 10)))
        assertEquals(Outcome.SKIP, Skips.outcome(tuesday, DayStatus.Planned, false, Reason.SICK, off(at(wednesday, 0, 10)), zone))
    }

    @Test
    fun `a session started Tuesday 23_30 and finished Wednesday 00_40 counts for Tuesday`() {
        val startedOn = Time.localDateOf(off(at(tuesday, 23, 30)), zone)
        assertEquals(tuesday, startedOn)
        assertEquals(Outcome.DONE, Skips.outcome(startedOn, DayStatus.Planned, true, null, null, zone))
    }

    @Test
    fun `a day off for Thursday declared Wednesday 21_59 is accepted, 22_01 is refused`() {
        assertEquals(Skips.DayOffCheck.OK, Skips.checkDayOff(thursday, at(wednesday, 21, 59), cutoff, DayStatus.Planned, 0))
        assertEquals(Skips.DayOffCheck.TOO_LATE, Skips.checkDayOff(thursday, at(wednesday, 22, 1), cutoff, DayStatus.Planned, 0))
    }

    @Test
    fun `a third declared day off in one month warns, then is saved as an unexcused skip`() {
        val offs =
            listOf(
                Absence(1, AbsenceKind.DAY_OFF, LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 6)),
                Absence(2, AbsenceKind.DAY_OFF, LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 13)),
            )
        val count = Skips.daysOffInMonth(offs, java.time.YearMonth.of(2026, 10))
        assertEquals(Skips.DayOffCheck.THIRD_THIS_MONTH, Skips.checkDayOff(thursday, at(tuesday, 9, 0), cutoff, DayStatus.Planned, count))
        val third = Absence(3, AbsenceKind.DAY_OFF, thursday, thursday, countsAsSkip = true)
        assertEquals(Outcome.SKIP, Skips.outcome(thursday, DayStatus.DayOff(third), false, null, null, zone))
        assertEquals(Outcome.DAY_OFF, Skips.outcome(thursday, DayStatus.DayOff(offs[0]), false, null, null, zone))
    }

    @Test
    fun `the check-in fires at 09_30 for a 07_30 plan, at most 7 days ahead, and not once a session started`() {
        val planned = { d: LocalDate -> Days.isPlanned(d, Fixtures.schedule, emptyList(), Fixtures.program) }
        val now = LocalDateTime.of(tuesday, LocalTime.of(6, 0))
        val all = Skips.checkins(now, Fixtures.schedule, planned, emptySet(), emptyMap(), emptyMap())
        assertEquals(LocalDateTime.of(tuesday, LocalTime.of(9, 30)), all.first().at)
        assertTrue(all.size <= 7)
        assertTrue(all.all { it.date.isBefore(tuesday.plusDays(7)) })
        assertEquals(6, all.size) // Sunday is rest
        val afterStart = Skips.checkins(now, Fixtures.schedule, planned, setOf(tuesday), emptyMap(), emptyMap())
        assertFalse(afterStart.any { it.date == tuesday })
        assertEquals(all, Skips.checkins(now, Fixtures.schedule, planned, emptySet(), emptyMap(), emptyMap()))
        val later =
            Skips.checkins(
                now,
                Fixtures.schedule,
                planned,
                emptySet(),
                mapOf(tuesday to Reason.GOING_LATER),
                mapOf(
                    tuesday to LocalTime.of(18, 0),
                ),
            )
        assertEquals(LocalDateTime.of(tuesday, LocalTime.of(20, 0)), later.first().at)
    }

    @Test
    fun `clock - midnight and month ends in Asia Kolkata`() {
        val oct31 = LocalDate.of(2026, 10, 31)
        assertTrue(Skips.canRecordReason(oct31, at(oct31, 23, 59)))
        assertFalse(Skips.canRecordReason(oct31, oct31.plusDays(1).atStartOfDay(zone)))
        assertEquals(Skips.DayOffCheck.OK, Skips.checkDayOff(LocalDate.of(2026, 11, 1), at(oct31, 21, 0), cutoff, DayStatus.Planned, 0))
        assertEquals(Skips.DayOffCheck.TOO_LATE, Skips.checkDayOff(LocalDate.of(2026, 11, 1), at(oct31, 22, 0), cutoff, DayStatus.Planned, 0))
    }
}

/** Section 13.4: make-up mode. */
class MakeupGoldenTest {
    private val day = LocalDate.of(2026, 10, 29)
    private val settings = MakeupSettings()
    private val start = LocalDate.of(2026, 10, 1)

    private fun skip(state: MakeupState, dates: List<LocalDate>, lastCompleted: LocalDate? = dates.last().minusDays(1), paused: Boolean = false) =
        Makeup.onSkip(state, dates.last(), dates, lastCompleted, start, settings, paused)

    @Test
    fun `1 skip in 7 days - no make-up mode, count 1`() {
        val s = skip(MakeupState(), listOf(day))
        assertEquals(0, s.sessionsLeft)
        assertEquals(1, s.skipsCounted)
    }

    @Test
    fun `2 skips - 4 sessions, 3 skips - 6, 5 skips - 6 and the banner`() {
        assertEquals(4, skip(MakeupState(), listOf(day.minusDays(3), day)).sessionsLeft)
        assertEquals(6, skip(MakeupState(), listOf(day.minusDays(5), day.minusDays(3), day)).sessionsLeft)
        val five = (0..4).map { day.minusDays(it.toLong()) }.sorted()
        assertEquals(6, skip(MakeupState(), five).sessionsLeft)
        assertTrue(Skips.bannerShows(five, day))
        assertTrue(Skips.bannerShows(five.take(4), day)) // the banner starts at 4
        assertFalse(Skips.bannerShows(five.take(3), day))
    }

    @Test
    fun `a new skip while 2 sessions are left that makes 3 in the window resets to 6`() {
        val s = skip(MakeupState(sessionsLeft = 2, skipsCounted = 2), listOf(day.minusDays(6), day.minusDays(2), day))
        assertEquals(6, s.sessionsLeft)
        assertEquals(day, s.triggeredOn)
    }

    @Test
    fun `Pull A block avoids back, biceps and delts and adds leg press and lying leg curl, 2 sets each`() {
        val program = Fixtures.program
        val pullA = Planner.build(program.day("pull_a"), SessionKind.FULL, Fixtures.input(Fixtures.monday.plusWeeks(3)))
        val todayGroups = pullA.groups(Fixtures.exercises)
        assertEquals(setOf(Groups.BACK, Groups.DELTS, Groups.BICEPS), todayGroups)
        val setsLast7 =
            mapOf(
                "chest" to 12,
                "back" to 9,
                "delts" to 10,
                "biceps" to 6,
                "triceps" to 8,
                "quads" to 1,
                "hams_glutes" to 2,
                "calves" to 5,
                "core" to 11,
            )
        val ctx = Fixtures.ctx()
        val block =
            Makeup.buildBlock(todayGroups, setsLast7, program.weeklyTargets, program.makeupCandidates) {
                Selection.available(Fixtures.exercises.getValue(it), ctx)
            }
        assertEquals(listOf(MakeupItem("quads", "leg_press", 2), MakeupItem("hams_glutes", "lying_leg_curl", 2)), block)
        val withBlock = Planner.build(program.day("pull_a"), SessionKind.FULL, Fixtures.input(Fixtures.monday.plusWeeks(3)), block)
        assertEquals(listOf("leg_press", "lying_leg_curl"), withBlock.makeup.map { it.exerciseId })
        assertTrue(withBlock.makeup.all { it.isMakeup && it.restS == 60 && it.sets == 2 })
    }

    @Test
    fun `a group already at 20 hard sets is never picked`() {
        val sets = Groups.ALL.associateWith { 0 } + ("quads" to 20) + ("hams_glutes" to 19)
        val block = Makeup.buildBlock(setOf("back"), sets, Fixtures.program.weeklyTargets, Fixtures.program.makeupCandidates) { true }
        assertFalse(block.any { it.group == "quads" })
        assertTrue(block.filter { it.group == "hams_glutes" }.all { it.sets == 1 })
    }

    @Test
    fun `a minimum session still gets the block, and sessions_left drops only after the block is logged`() {
        val program = Fixtures.program
        val block = listOf(MakeupItem("quads", "leg_press", 2), MakeupItem("hams_glutes", "lying_leg_curl", 2))
        val minimum = Planner.build(program.day("pull_a"), SessionKind.MINIMUM, Fixtures.input(Fixtures.monday.plusWeeks(3)), block)
        assertEquals(3, minimum.exercises.size)
        assertEquals(2, minimum.makeup.size)
        val state = MakeupState(sessionsLeft = 4)
        assertEquals(4, state.sessionsLeft)
        assertEquals(3, Makeup.afterBlockLogged(state).sessionsLeft)
    }

    @Test
    fun `a sick day pauses make-up mode - no block and the count is frozen`() {
        assertTrue(Makeup.isPaused(sickToday = true, injuries = emptyList()))
        val phase1 = InjuryState(1, "knee", Side.LEFT, InjuryStatus.ACTIVE, 1, day)
        assertTrue(Makeup.isPaused(false, listOf(phase1)))
        assertFalse(Makeup.isPaused(false, listOf(phase1.copy(phase = 2))))
        val frozen = skip(MakeupState(sessionsLeft = 2, skipsCounted = 2), listOf(day.minusDays(2), day.minusDays(1), day), paused = true)
        assertEquals(2, frozen.sessionsLeft)
        assertEquals(2, frozen.skipsCounted)
    }

    @Test
    fun `comeback stub - last completed session 8 days ago, then skips - no make-up mode`() {
        val s = skip(MakeupState(), listOf(day.minusDays(1), day), lastCompleted = day.minusDays(8))
        assertEquals(0, s.sessionsLeft)
    }

    @Test
    fun `weekly sets count working, final ramp, make-up, deload and home sets, but not warm-ups or earlier ramp sets`() {
        val sets =
            listOf(
                CountedSet(1, "barbell_bench_press", isWarmup = true, isRamp = false),
                CountedSet(1, "barbell_bench_press", isWarmup = false, isRamp = true),
                CountedSet(1, "barbell_bench_press", isWarmup = false, isRamp = true),
                CountedSet(1, "barbell_bench_press", isWarmup = false, isRamp = false),
                CountedSet(1, "dips", isWarmup = false, isRamp = false),
                CountedSet(2, "push_ups", isWarmup = false, isRamp = false),
            )
        val counts = Makeup.weeklySets(sets, Fixtures.exercises)
        assertEquals(4, counts["chest"]) // final ramp + 1 working bench, dips, push-ups
        assertEquals(2, counts["triceps"]) // dips and push-ups
        assertEquals(0, counts["back"])
    }
}

/** Section 13.5: day off and away mode. */
class AwayGoldenTest {
    private val program = Fixtures.program

    @Test
    fun `going home Thursday 22 to Sunday 25 October with dumbbells, 30 minutes`() {
        val trip =
            Absence(
                1,
                AbsenceKind.AWAY,
                LocalDate.of(2026, 10, 22),
                LocalDate.of(2026, 10, 25),
                AwayEquipment.DUMBBELLS,
                30,
                createdAt = OffsetDateTime.of(2026, 10, 21, 21, 40, 0, 0, java.time.ZoneOffset.ofHoursMinutes(5, 30)),
            )
        val monday = LocalDate.of(2026, 10, 26)
        val lines = Away.preview(trip, program, preTripDate = null, nextPlanned = monday, nextWorkout = "Push B")
        assertEquals(
            listOf(
                "2026-10-22: Travel, paused",
                "2026-10-23: Dumbbell full body, 30 min",
                "2026-10-24: Rest",
                "2026-10-25: Dumbbell full body, 30 min",
                "2026-10-26: Push B, the next workout in the rotation",
            ),
            lines.map { "${it.date}: ${it.text}" },
        )
        (22..25).forEach { d ->
            val status = Days.status(LocalDate.of(2026, 10, d), Fixtures.schedule, listOf(trip), program)
            assertTrue(status is DayStatus.Away, "Oct $d should be away")
            assertEquals(Outcome.AWAY, Skips.outcome(LocalDate.of(2026, 10, d), status, false, null, null, ZoneId.of("Asia/Kolkata")))
        }
        assertEquals(DayStatus.Planned, Days.status(monday, Fixtures.schedule, listOf(trip), program))
    }

    @Test
    fun `7 days with bodyweight only - 3 sessions on days 2, 4 and 6`() {
        val trip = Absence(1, AbsenceKind.AWAY, Fixtures.monday, Fixtures.monday.plusDays(6), AwayEquipment.BODYWEIGHT, 30)
        val days = Away.plan(trip, program)
        assertEquals(listOf(2, 4, 6), days.filter { it.kind == AwayDayKind.SESSION }.map { it.index })
        assertTrue(days.filter { it.kind == AwayDayKind.SESSION }.all { it.templateId == "away_bodyweight" })
        assertEquals(1, Away.sessionCount(1))
        assertEquals(2, Away.sessionCount(4))
        assertEquals(3, Away.sessionCount(10))
    }

    @Test
    fun `no time - every day paused and no sessions`() {
        val trip = Absence(1, AbsenceKind.AWAY, Fixtures.monday, Fixtures.monday.plusDays(4), AwayEquipment.NONE, null)
        assertTrue(Away.plan(trip, program).all { it.kind == AwayDayKind.PAUSED })
    }

    @Test
    fun `a gym there keeps the normal plan, and skips count as usual`() {
        val trip = Absence(1, AbsenceKind.AWAY, Fixtures.monday, Fixtures.monday.plusDays(4), AwayEquipment.GYM, null)
        assertEquals(DayStatus.Planned, Days.status(Fixtures.monday, Fixtures.schedule, listOf(trip), program))
    }

    @Test
    fun `chest last trained 5 days before a 4-day trip makes the last session full body`() {
        val start = LocalDate.of(2026, 10, 22)
        val trained =
            mapOf(
                "chest" to start.minusDays(5),
                "back" to start.minusDays(2),
                "quads" to start.minusDays(1),
                "hams_glutes" to start.minusDays(3),
            )
        assertTrue(Away.preTripNeeded(start, 4, trained))
        assertFalse(Away.preTripNeeded(start, 2, trained))
        val trip = Absence(1, AbsenceKind.AWAY, start, start.plusDays(3), AwayEquipment.DUMBBELLS, 30)
        val planned = { d: LocalDate -> Days.isPlanned(d, Fixtures.schedule, listOf(trip), program) }
        assertEquals(start.minusDays(1), Away.lastDayBefore(trip, start.minusDays(1), planned) { false })
        assertEquals(null, Away.lastDayBefore(trip, start.minusDays(1), planned) { true })
    }

    @Test
    fun `undo before the trip restores the plan exactly, undo on day 2 cancels the remaining days`() {
        val now = OffsetDateTime.now()
        val trip = Absence(1, AbsenceKind.AWAY, Fixtures.monday.plusDays(3), Fixtures.monday.plusDays(6), AwayEquipment.DUMBBELLS, 30)
        val before = Away.undo(trip, now, Fixtures.monday)
        assertFalse(before.isActive)
        (0..7).forEach { i ->
            val d = Fixtures.monday.plusDays(i.toLong())
            assertEquals(Days.status(d, Fixtures.schedule, emptyList(), program), Days.status(d, Fixtures.schedule, listOf(before), program))
        }
        val during = Away.undo(trip, now, trip.start.plusDays(1))
        assertEquals(trip.start.plusDays(1), during.end)
        assertEquals(DayStatus.Planned, Days.status(trip.start.plusDays(2), Fixtures.schedule, listOf(during), program))
    }
}

/** Section 13.6: injury mode. */
class InjuryGoldenTest {
    private val rules = Fixtures.rules
    private val program = Fixtures.program
    private val today = Fixtures.monday.plusWeeks(3)
    private val leftShoulder = InjuryState(1, "shoulder", Side.LEFT, InjuryStatus.ACTIVE, 1, today, today, usualMorningPain = 4)

    @Test
    fun `left shoulder, pain 4, no red flags - pressing overhead, dips and wide-grip bench are swapped, legs and rows stay`() {
        val answers =
            Triage.Answers(
                "shoulder",
                Side.LEFT,
                feel = Triage.FEEL_SHARP,
                onset = Triage.ONSET_DURING_SET,
                easesWithWarmUp = false,
                painNow = 4,
            )
        assertEquals(Triage.Result.INJURY, Triage.classify(answers))
        val ctx = Fixtures.ctx(today, injuries = listOf(leftShoulder))

        fun pick(slot: String) = Selection.pick(program.slot(slot)!!, program.days.first { d -> d.slots.any { it.id == slot } }.dayType, ctx, program)
        assertEquals("dumbbell_bench_press", pick("push_a_1").exerciseId) // barbell and Smith bench are wide-grip
        assertEquals("neutral_grip_dumbbell_press", pick("push_b_1").exerciseId) // every overhead press is out
        assertEquals(Pick.Source.ALTERNATIVE, pick("push_b_1").source)
        assertEquals("close_grip_bench_press", pick("push_b_4").exerciseId) // dips and assisted dips are out
        listOf("legs_a_1", "legs_a_2", "legs_b_1", "pull_a_2", "pull_a_3", "pull_b_1").forEach { slot ->
            assertEquals(Pick.Source.FIRST_CHOICE, pick(slot).source, slot)
        }
        val lateral = Fixtures.exercises.getValue("dumbbell_lateral_raise")
        assertTrue(Injuries.ifPainless(lateral, leftShoulder, rules))
        assertTrue(Injuries.usesArea(lateral, leftShoulder, rules))
        assertEquals(Pick.Source.FIRST_CHOICE, pick("push_a_5").source)
    }

    @Test
    fun `any red flag shows the stop screen and locks the area until I've been checked`() {
        val answers =
            Triage.Answers(
                "shoulder",
                Side.LEFT,
                feel = Triage.FEEL_SHARP,
                onset = Triage.ONSET_DURING_SET,
                easesWithWarmUp = false,
                painNow = 5,
                redFlags = setOf("pop"),
            )
        assertEquals(Triage.Result.STOP, Triage.classify(answers))
        assertEquals(Triage.Result.EMERGENCY, Triage.classify(answers.copy(emergency = true)))
        val locked = leftShoulder.copy(redFlag = true, phase = 3)
        assertTrue(locked.locked)
        val rows = Fixtures.exercises.getValue("seated_cable_row")
        assertTrue(Injuries.removes(rows, locked, rules)) // uses the shoulder
        assertFalse(Injuries.removes(rows, locked.copy(checkedByDoctor = true), rules))
        assertFalse(Injuries.removes(Fixtures.exercises.getValue("leg_press"), locked, rules))
    }

    @Test
    fun `phase 3 at 60 percent - green goes to 70, amber holds, red goes back to phase 2`() {
        val p3 = Injuries.enter(leftShoulder, 3, today)
        assertEquals(0.6, p3.loadFraction!!, 1e-9)
        assertEquals(0.7, Injuries.afterSession(p3, PainLight.GREEN, today).loadFraction!!, 1e-9)
        assertEquals(0.6, Injuries.afterSession(p3, PainLight.AMBER, today).loadFraction!!, 1e-9)
        val red = Injuries.afterSession(p3, PainLight.RED, today)
        assertEquals(2, red.phase)
        assertEquals(0.4, red.loadFraction!!, 1e-9)
    }

    @Test
    fun `phase 1 moves on after a day with a green morning, and phase 2 after 2 green sessions`() {
        assertEquals(1, Injuries.afterMorning(leftShoulder, 2, false, today).phase) // same day
        val p2 = Injuries.afterMorning(leftShoulder, 2, false, today.plusDays(1))
        assertEquals(2, p2.phase)
        assertEquals(0.4, p2.loadFraction!!, 1e-9)
        val one = Injuries.afterSession(p2, PainLight.GREEN, today.plusDays(1))
        assertEquals(0.5, one.loadFraction!!, 1e-9)
        val two = Injuries.afterSession(one, PainLight.GREEN, today.plusDays(2))
        assertEquals(3, two.phase)
        assertEquals(0.6, two.loadFraction!!, 1e-9)
    }

    @Test
    fun `morning pain worse than the usual morning steps back one phase`() {
        val p3 = Injuries.enter(leftShoulder, 3, today).copy(usualMorningPain = 2)
        assertEquals(2, Injuries.afterMorning(p3, 4, false, today.plusDays(1)).phase)
        assertEquals(3, Injuries.afterMorning(p3, 2, false, today.plusDays(1)).phase)
    }

    @Test
    fun `phase 4 - two green sessions at the pre-injury weight resolve the injury`() {
        val p4 = Injuries.enter(leftShoulder, 4, today)
        assertEquals(1.0, Injuries.loadFraction(p4)!!, 1e-9)
        val once = Injuries.afterSession(p4, PainLight.GREEN, today)
        assertEquals(InjuryStatus.ACTIVE, once.status)
        assertEquals(InjuryStatus.RESOLVED, Injuries.afterSession(once, PainLight.GREEN, today).status)
    }

    @Test
    fun `phases 2 to 4 bring the original exercise back at the pre-injury weight times the phase percentage`() {
        val p2 = Injuries.enter(leftShoulder, 2, today)
        val pre = mapOf(1L to mapOf("seated_dumbbell_shoulder_press" to PreInjury(30.0, 20.0)))
        val plan = Planner.build(program.day("push_a"), SessionKind.FULL, Fixtures.input(today, injuries = listOf(p2), preInjury = pre))
        val press = plan.exercises.first { it.slotId == "push_a_3" }
        assertEquals("seated_dumbbell_shoulder_press", press.exerciseId)
        assertTrue(press.injuryLoad)
        assertEquals(7.5, press.weight) // 40% of 20 kg = 8, rounded down to the 7.5 kg pair
        assertEquals(12 to 20, press.repLow to press.repHigh)
        assertTrue(press.painRequired)
    }

    @Test
    fun `a soreness pattern starts no injury mode and shows the warm-up tip`() {
        val answers =
            Triage.Answers(
                "muscle",
                Side.BOTH,
                muscle = "quads",
                feel = Triage.FEEL_DULL,
                onset = Triage.ONSET_AFTER_TRAINING,
                easesWithWarmUp = true,
                painNow = 3,
            )
        assertEquals(Triage.Result.SORENESS, Triage.classify(answers))
        assertTrue(rules.warmUpTip.isNotBlank())
    }

    @Test
    fun `one-sided injuries switch the healthy side to the one-leg version, and a strained muscle removes its main lifts`() {
        val leftKnee = InjuryState(2, "knee", Side.LEFT, InjuryStatus.ACTIVE, 1, today, today)
        val pick = Selection.pick(program.slot("legs_a_3")!!, DayType.LEGS, Fixtures.ctx(today, injuries = listOf(leftKnee)), program)
        assertEquals("single_leg_extension", pick.exerciseId)
        assertEquals(Side.RIGHT, pick.healthySide)
        val chest = InjuryState(3, Injuries.MUSCLE, Side.LEFT, InjuryStatus.ACTIVE, 1, today, today, muscle = "chest")
        assertTrue(Injuries.clashes(Fixtures.exercises.getValue("cable_fly"), chest, rules))
        assertFalse(Injuries.clashes(Fixtures.exercises.getValue("lat_pulldown"), chest, rules))
    }
}

/** Section 13.7: food and body. */
class FoodGoldenTest {
    private val foods = SeedData.foods.foods.associateBy { it.id }

    @Test
    fun `70 kg lean bulk - 120 g protein (112 to 140) and 2500 kcal, recomp 2200`() {
        assertEquals(120, Nutrition.proteinTarget(70.0))
        assertEquals(112..140, Nutrition.proteinRange(70.0))
        assertEquals(2500, Nutrition.kcalGuess(70.0, Goal.LEAN_BULK))
        assertEquals(2200, Nutrition.kcalGuess(70.0, Goal.RECOMP))
    }

    @Test
    fun `175 cm with waist 85 suggests lean bulk, waist 90 suggests recomp`() {
        assertEquals(Goal.LEAN_BULK, Nutrition.suggestGoal(85.0, 175.0))
        assertEquals(Goal.RECOMP, Nutrition.suggestGoal(90.0, 175.0))
    }

    @Test
    fun `office thali is 770 kcal and 25 g protein, 860 kcal with Canteen on`() {
        val thali =
            listOf("roti" to 2.0, "dal_tadka" to 1.0, "rice" to 1.0, "dry_veg_sabzi" to 1.0, "curd" to 1.0).map { (id, q) ->
                foods.getValue(id) to
                    q
            }
        assertEquals(Totals(770.0, 25.0), Nutrition.meal(thali, canteen = false))
        assertEquals(Totals(860.0, 25.0), Nutrition.meal(thali, canteen = true))
    }

    @Test
    fun `weigh-ins of 70_2, 70_6 and 70_4 average 70_4, and 2 weigh-ins show no average`() {
        val today = LocalDate.of(2026, 10, 21)
        val three = mapOf(today to 70.2, today.minusDays(2) to 70.6, today.minusDays(5) to 70.4)
        assertEquals(70.4, Nutrition.weightAverage(three, today)!!, 1e-9)
        assertEquals(null, Nutrition.weightAverage(three - today, today))
        val old = mapOf(today.minusDays(7) to 70.0, today.minusDays(9) to 70.0, today.minusDays(12) to 70.0)
        assertEquals(0.4, Nutrition.weightChange(three + old, today)!!, 1e-9)
    }

    @Test
    fun `veg diet hides egg and chicken items`() {
        val visible = foods.values.filter { Diet.allows(Diet.VEG, it.dietType) }.map { it.id }
        assertFalse("whole_egg" in visible || "egg_whites" in visible || "chicken_breast" in visible || "chicken_curry" in visible)
        assertTrue("paneer" in visible)
        assertTrue(foods.values.filter { Diet.allows(Diet.EGG, it.dietType) }.any { it.id == "whole_egg" })
        assertFalse(foods.values.filter { Diet.allows(Diet.EGG, it.dietType) }.any { it.id == "chicken_curry" })
    }

    @Test
    fun `targets are recalculated when body weight moves 2 kg`() {
        assertFalse(Nutrition.shouldRecalculate(71.9, 70.0))
        assertTrue(Nutrition.shouldRecalculate(72.0, 70.0))
        assertTrue(Nutrition.shouldRecalculate(68.0, 70.0))
    }
}
