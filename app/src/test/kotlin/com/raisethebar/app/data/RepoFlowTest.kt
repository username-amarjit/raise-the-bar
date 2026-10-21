package com.raisethebar.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raisethebar.app.db.AppDatabase
import com.raisethebar.engine.DayStatus
import com.raisethebar.engine.Goal
import com.raisethebar.engine.MakeupSettings
import com.raisethebar.engine.Outcome
import com.raisethebar.engine.Reason
import com.raisethebar.engine.SeedData
import com.raisethebar.engine.SessionKind
import com.raisethebar.engine.Skips
import com.raisethebar.engine.Weekdays
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Runs the data layer end to end on an in-memory database, the way the screens will: seeding, setup, Today,
 * logging a workout, resuming after a restart, the midnight timeout, make-up mode and a day off.
 */
@RunWith(AndroidJUnit4::class)
class RepoFlowTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val monday = LocalDate.of(2026, 10, 19)
    private val tuesday = monday.plusDays(1)
    private val wednesday = monday.plusDays(2)
    private val thursday = monday.plusDays(3)
    private lateinit var db: AppDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun close() = db.close()

    /** A fresh Repo is like a fresh app start: same database, new clock. */
    private fun repoAt(date: LocalDate, h: Int, m: Int = 0) = Repo(db, AppClock(Clock.fixed(date.atTime(h, m).atZone(zone).toInstant(), zone)))

    private suspend fun setUpOn(date: LocalDate, h: Int = 8): Repo {
        val repo = repoAt(date, h)
        Seeder(repo.db, repo.time).seedIfNeeded()
        val hurting =
            repo.completeSetup(
                SetupInput(
                    weightKg = 70.0,
                    heightCm = 175.0,
                    waistCm = 85.0,
                    age = 30,
                    dietType = "veg",
                    experience = "beginner",
                    goal = Goal.LEAN_BULK,
                    proteinTargetG = 120,
                    kcalTarget = 2500,
                    schedule = Weekdays.defaults(),
                    equipment = SeedData.equipment,
                    dayOffCutoff = LocalTime.of(22, 0),
                    makeup = MakeupSettings(),
                    notificationsOn = false,
                    oldInjuries = listOf(OldInjury("knee", "left", null, hurtsNow = false)),
                ),
            )
        assertTrue(hurting.isEmpty())
        return repo
    }

    @Test
    fun seedingLoadsTheLibraryProgramGymAndFoodOnce() =
        runTest {
            val repo = repoAt(wednesday, 8)
            Seeder(repo.db, repo.time).seedIfNeeded()
            Seeder(repo.db, repo.time).seedIfNeeded()
            assertEquals(SeedData.exercises.size, db.exercise().count())
            assertEquals(SeedData.program.days.size, db.program().days().size)
            assertEquals(SeedData.equipment.size, db.equipment().all().size)
            assertEquals(SeedData.foods.foods.size, db.food().all().size)
            val meals = db.usualMeal().all()
            assertEquals(3, meals.size)
            val thali = meals.first { it.name == "Office thali" }
            assertEquals(770.0, thali.kcal, 1e-9)
            assertEquals(25.0, thali.proteinG, 1e-9)
        }

    @Test
    fun afterSetupTodayShowsCalibrationWeek1PushA() =
        runTest {
            val repo = setUpOn(wednesday)
            assertTrue(repo.isSetupDone())
            assertEquals("watch", db.injury().all().single().status)
            val today = repo.todayState()
            assertEquals(TodayKind.TRAIN, today.kind)
            assertEquals("Calibration, week 1", today.phaseLabel)
            val plan = today.plan!!
            assertEquals("Push A", plan.dayName)
            assertEquals(6, plan.exercises.size)
            assertTrue(plan.exercises.all { it.ramp && it.sets == 2 })
            assertEquals(20.0, plan.exercises.first().rampStart)
            assertEquals(0, today.makeup.sessionsLeft)
            assertFalse(today.banner)
        }

    @Test
    fun aRampedWorkoutLogsResumesAfterRestartAndFinishes() =
        runTest {
            val repo = setUpOn(wednesday)
            val plan = repo.planForToday(SessionKind.FULL)!!
            val id = repo.startSession(plan)
            assertEquals(id, repo.startSession(plan)) // a second start resumes the open session
            val bench = plan.exercises.first()
            assertEquals("barbell_bench_press", bench.exerciseId)

            // Ramp: 20 kg x 8 with 4+ left suggests about 20% heavier; 2 left ends the ramp.
            repo.logSet(id, SetInput(bench.key, 20.0, 8, 4))
            var view = repo.sessionView(id)!!
            assertEquals(22.5, view.suggestedWeight(bench))
            assertEquals(8, view.targetReps(bench))
            repo.logSet(id, SetInput(bench.key, 22.5, 8, 2))
            view = repo.sessionView(id)!!
            assertEquals(22.5, view.state.rampDone[bench.key])
            assertEquals(1, view.workingDone(bench))
            assertEquals(6, view.targetReps(bench))
            repo.logSet(id, SetInput(bench.key, 22.5, 6, 3))

            // Killing the app mid-workout loses nothing: a new app start sees the same session and state.
            val restarted = repoAt(wednesday, 8, 40)
            assertEquals(id, db.session().open()!!.id)
            val resumed = restarted.sessionView(id)!!
            assertTrue(resumed.isComplete(bench))
            assertEquals(plan.exercises[1].key, resumed.state.current)
            assertEquals(3, resumed.setsFor(bench.key).size)
            assertNotNull(resumed.state.restEndsAt)

            restarted.finishSession(id)
            assertNull(db.session().open())
            val est = db.estimate().get("barbell_bench_press")!!
            assertEquals(30.0, est.e1rmKg!!, 1e-9) // best ramp set: 22.5 x (1 + 10/30)
            assertEquals(22.5, est.currentWeightKg!!, 1e-9)
            assertEquals(1, db.plan().get()!!.rotationIndex)
            assertEquals(Outcome.DONE, db.dayLog().get(wednesday)!!.outcome)
            assertNotNull(restarted.todayState().doneToday)
        }

    @Test
    fun missedDaysTimeOutAtMidnightStartMakeupModeAndKeepTheWorkout() =
        runTest {
            setUpOn(monday)
            val wed = repoAt(wednesday, 8)
            assertEquals(listOf(monday, tuesday), wed.evaluateDays())
            assertEquals(Outcome.SKIP, db.dayLog().get(monday)!!.outcome)
            assertEquals(Outcome.SKIP, db.dayLog().get(tuesday)!!.outcome)
            assertEquals(4, wed.makeupState().sessionsLeft)
            assertTrue(wed.evaluateDays().isEmpty()) // already evaluated

            val today = wed.todayState()
            val plan = today.plan!!
            assertEquals("Push A", plan.dayName) // no workout is lost
            assertEquals(2, today.skipsLast7)
            assertEquals(listOf("back", "core"), plan.makeup.map { it.makeupGroup })
            assertTrue(plan.makeup.all { it.isMakeup && it.sets == 2 && it.restS == 60 })

            val screen = wed.skipScreen()
            assertEquals(2, screen.stats.skips.size)
            assertEquals(2, screen.stats.plannedThisMonth)
            assertEquals(0.0, screen.stats.adherence!!, 1e-9)
        }

    @Test
    fun sickBeforeMidnightIsExcusedAndLateReasonsAreRejected() =
        runTest {
            setUpOn(monday)
            assertTrue(repoAt(tuesday, 21).recordReason(tuesday, Reason.SICK))
            assertFalse(repoAt(wednesday, 0, 10).recordReason(tuesday, Reason.SKIPPING))
            val wed = repoAt(wednesday, 8)
            assertEquals(listOf(monday), wed.evaluateDays())
            assertEquals(Outcome.EXCUSED, db.dayLog().get(tuesday)!!.outcome)
            assertEquals(Reason.SICK, db.dayLog().get(tuesday)!!.reason)
            assertEquals(0, wed.makeupState().sessionsLeft)
            assertEquals(1, wed.makeupState().skipsCounted)
        }

    @Test
    fun aDayOffBeforeTheCutoffIsNotASkipAndDoesNotMoveTheRotation() =
        runTest {
            setUpOn(monday)
            assertEquals(Skips.DayOffCheck.TOO_LATE, repoAt(wednesday, 22, 1).declareDayOff(thursday, acceptAsSkip = false))
            assertTrue(db.absence().all().isEmpty())
            assertEquals(Skips.DayOffCheck.OK, repoAt(wednesday, 21, 59).declareDayOff(thursday, acceptAsSkip = false))
            val fri = repoAt(thursday.plusDays(1), 8)
            assertTrue(fri.dayStatus(thursday) is DayStatus.DayOff)
            fri.evaluateDays()
            assertEquals(Outcome.DAY_OFF, db.dayLog().get(thursday)!!.outcome)
            assertEquals(0, db.plan().get()!!.rotationIndex)
        }
}
