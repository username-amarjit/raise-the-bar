package com.raisethebar.app.db

import com.raisethebar.app.data.AppClock
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/** One valid row for every table, shared by the database tests. */
object Samples {
    val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    val day: LocalDate = LocalDate.of(2026, 10, 21)
    val at: OffsetDateTime = OffsetDateTime.of(2026, 10, 21, 7, 30, 0, 0, zone.rules.getOffset(Instant.parse("2026-10-21T02:00:00Z")))

    fun clock(instant: String = "2026-10-21T02:00:00Z") = AppClock(Clock.fixed(Instant.parse(instant), zone))

    fun profile() =
        ProfileEntity(
            weightKg = 70.0,
            heightCm = 175.0,
            waistCm = 85.0,
            age = 30,
            dietType = "veg",
            experience = "beginner",
            goal = "lean_bulk",
            proteinTargetG = 120,
            kcalTarget = 2500,
            dayOffCutoff = "22:00",
            makeupSettings = """{"trigger":2,"sessionsPerSkip":2,"cap":6,"blockMin":15}""",
            notificationsOn = true,
        )

    fun schedule() = (0..6).map { ScheduleEntity(weekday = it, isTrainingDay = it < 6, time = "07:30") }

    fun plan() = PlanEntity(template = "ppl6", planStart = day, rotationIndex = 0, previous = null)

    fun equipment() =
        EquipmentEntity(
            id = "dumbbells",
            name = "Dumbbells",
            kind = "dumbbell",
            status = "working",
            statusDate = null,
            brokenSince = null,
            barKg = null,
            platePairsKg = emptyList(),
            dumbbellPairsKg = listOf(2.5, 5.0, 7.5, 10.0),
            stackMin = null,
            stackMax = null,
            stackStep = null,
            missingKg = emptyList(),
        )

    fun exercise() =
        ExerciseEntity(
            id = "barbell_bench_press",
            name = "Barbell bench press",
            aliases = listOf("bench"),
            countsToward = listOf("chest"),
            equipmentIds = listOf("barbell", "plates", "adjustable_bench"),
            bigOrSmall = "big",
            stresses = listOf("barbell_press", "wide_grip_press"),
            cues = listOf("Shoulder blades squeezed and down", "Bar to mid-chest", "Feet planted"),
            instructions = listOf("Lie on the bench."),
            images = listOf("exercises/barbell_bench_press_0.webp"),
            unilateral = false,
            oneSidedVersionId = null,
            perSide = false,
            timed = false,
            bodyweightBased = false,
            stepKg = 2.5,
            source = "free-exercise-db",
            licence = "Unlicense",
        )

    fun programDay() = ProgramDayEntity(id = "push_a", name = "Push A", focus = "Chest focus", position = 0)

    fun slot() =
        SlotEntity(
            id = "push_a_1",
            programDayId = "push_a",
            position = 1,
            sets = 3,
            repLow = 6,
            repHigh = 8,
            secLow = null,
            secHigh = null,
            restS = 150,
            options = listOf("barbell_bench_press", "smith_bench_press"),
        )

    fun estimate() =
        EstimateEntity(
            exerciseId = "barbell_bench_press",
            e1rmKg = 60.0,
            currentWeightKg = 45.0,
            badDays = 0,
            sessions = 1,
            calibrated = true,
            updatedAt = at,
        )

    fun session() =
        SessionEntity(
            localDate = day,
            programDayId = "push_a",
            kind = "full",
            blockWeek = null,
            startedAt = at,
            completedAt = null,
        )

    fun setLog(sessionId: Long) =
        SetLogEntity(
            sessionId = sessionId,
            exerciseId = "barbell_bench_press",
            slotId = "push_a_1",
            setNo = 1,
            weightKg = 45.0,
            reps = 8,
            repsLeft = 3,
            seconds = null,
            pain = null,
            sharp = false,
            isWarmup = false,
            isRamp = false,
            isMakeup = false,
            inInjuryMode = false,
            loggedAt = at,
        )

    fun dayLog() = DayLogEntity(localDate = day, planned = true, reason = null, reasonAt = null, outcome = null)

    fun absence() =
        AbsenceEntity(
            kind = "away",
            start = day.plusDays(1),
            end = day.plusDays(4),
            equipmentThere = "dumbbells",
            minutesPerDay = 30,
            createdAt = at,
            cancelledAt = null,
        )

    fun makeup() = MakeupEntity(triggeredOn = null, skipsCounted = 0, sessionsLeft = 0, paused = false, updatedAt = at)

    fun injury() =
        InjuryEntity(
            area = "shoulder",
            side = "left",
            status = "active",
            phase = 1,
            started = day,
            redFlag = false,
            checkedByDoctor = false,
            preInjury = "{}",
            greenStreak = 0,
        )

    fun painLog(injuryId: Long) = PainLogEntity(injuryId = injuryId, localDate = day, kind = "morning", value = 3, sharp = false)

    fun food() =
        FoodEntity(
            id = "roti",
            name = "Roti or chapati, medium, no ghee",
            unit = "1",
            kcal = 100.0,
            proteinG = 3.0,
            dietType = "veg",
            cooked = false,
            category = "staple",
        )

    fun usualMeal() = UsualMealEntity(name = "After-gym shake", items = "[]", kcal = 225.0, proteinG = 25.0)

    fun foodLog() =
        FoodLogEntity(
            localDate = day,
            meal = "lunch",
            foodId = "roti",
            usualMealId = null,
            qty = 2.0,
            kcal = 200.0,
            proteinG = 6.0,
            canteen = false,
            source = "tap",
        )

    fun bodyLog() = BodyLogEntity(localDate = day, weightKg = 70.2)

    fun meta() = MetaEntity("install_date", "2026-10-21")
}
