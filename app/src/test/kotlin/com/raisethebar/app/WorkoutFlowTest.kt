package com.raisethebar.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raisethebar.app.data.AppClock
import com.raisethebar.app.data.Repo
import com.raisethebar.app.data.SetInput
import com.raisethebar.app.data.doNextFirst
import com.raisethebar.app.data.finishSession
import com.raisethebar.app.data.logSet
import com.raisethebar.app.data.planForToday
import com.raisethebar.app.data.sessionView
import com.raisethebar.app.data.startSession
import com.raisethebar.app.data.swapForToday
import com.raisethebar.app.data.todayState
import com.raisethebar.app.db.EstimateEntity
import com.raisethebar.app.ui.LocalRepo
import com.raisethebar.app.ui.theme.RtbTheme
import com.raisethebar.app.ui.workout.SessionScreen
import com.raisethebar.engine.SessionKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Duration

/** Milestone 7: a full Push A can be logged, and killing the app mid-workout loses nothing. */
@RunWith(AndroidJUnit4::class)
class WorkoutFlowTest {
    @get:Rule
    val compose = createComposeRule()

    /** Logs every remaining set the way the logger would: suggested weight, target reps, 2 left (ends any ramp). */
    private fun Repo.logAll(id: Long, stopAfter: Int = Int.MAX_VALUE): Int {
        var count = 0
        while (count < stopAfter) {
            val view = runBlocking { sessionView(id) }!!
            val pe = view.state.ordered.firstOrNull { !view.isComplete(it) } ?: break
            val reps = view.targetReps(pe) ?: pe.repLow ?: 10
            val input =
                if (pe.isTimed) {
                    SetInput(pe.key, null, null, null, seconds = pe.secLow)
                } else {
                    SetInput(pe.key, view.suggestedWeight(pe), reps, 2)
                }
            runBlocking { logSet(id, input) }
            count++
        }
        return count
    }

    @Test
    fun fullPushAIsLoggedSurvivesAKillAndFinishes() {
        val repo = TestRepo.createSetUp()
        val plan = runBlocking { repo.planForToday(SessionKind.FULL) }!!
        assertEquals("Push A", plan.dayName)
        val id = runBlocking { repo.startSession(plan) }
        assertEquals(5, repo.logAll(id, stopAfter = 5))

        // The app is killed: a new process opens the same database and finds the workout exactly as it was.
        val reopened = Repo(repo.db, AppClock(Clock.offset(repo.time.clock, Duration.ofMinutes(20))))
        assertEquals(id, runBlocking { reopened.db.session().open() }?.id)
        val before = runBlocking { reopened.sessionView(id) }!!
        assertEquals(5, before.sets.size)
        assertNotNull(before.state.current)
        assertNotNull(runBlocking { reopened.todayState() }.openSession)

        reopened.logAll(id)
        val view = runBlocking { reopened.sessionView(id) }!!
        assertTrue(view.allComplete)
        assertEquals(12, view.sets.count { !it.isWarmup }) // 6 exercises × (final ramp set + 1 more)
        runBlocking { reopened.finishSession(id) }
        assertNull(runBlocking { reopened.db.session().open() })
        assertEquals(6, runBlocking { reopened.db.estimate().all() }.count { it.e1rmKg != null })
        assertEquals(1, runBlocking { reopened.db.plan().get() }!!.rotationIndex)
        val today = runBlocking { reopened.todayState() }
        assertNotNull(today.doneToday)
        assertEquals("Push A", today.plan!!.dayName) // what was done, not the next workout
    }

    @Test
    fun firstSetCheckDropsTheRemainingSetsAndTheEstimateHolds() {
        val repo = TestRepo.createSetUp()
        runBlocking {
            val plan = repo.db.plan().get()!!
            repo.db.plan().upsert(plan.copy(planStart = plan.planStart.minusWeeks(3))) // week 4 = block week 2
            repo.db.estimate().upsert(EstimateEntity("barbell_bench_press", 63.0, 45.0, 0, 3, true, repo.time.now()))
        }
        val plan = runBlocking { repo.planForToday(SessionKind.FULL) }!!
        val bench = plan.exercises.first()
        assertEquals(47.5 to 7, bench.weight to bench.set1Target)
        assertTrue(bench.wentUp)
        val id = runBlocking { repo.startSession(plan) }
        runBlocking { repo.logSet(id, SetInput(bench.key, 47.5, 4, 2)) }
        val view = runBlocking { repo.sessionView(id) }!!
        assertEquals("Harder than predicted, so sets 2 and 3 drop to 45 kg.", view.state.messages[bench.key])
        assertEquals(45.0, view.suggestedWeight(bench))
        runBlocking { repo.logSet(id, SetInput(bench.key, 45.0, 6, 2)) }
        runBlocking { repo.logSet(id, SetInput(bench.key, 45.0, 5, 2)) }
        runBlocking { repo.finishSession(id) }
        val est = runBlocking { repo.db.estimate().get("barbell_bench_press") }!!
        assertEquals(63.0, est.e1rmKg!!, 1e-9) // 57.0 < 57.96: a bad day, so the estimate holds
        assertEquals(1, est.badDays)
        assertEquals(47.5, est.currentWeightKg!!, 1e-9) // the prescribed weight, not the dropped one
    }

    @Test
    fun gymClosedStartsTheHomeVersionWhichCountsAsDone() {
        val repo = TestRepo.createSetUp()
        val home = runBlocking { repo.planForToday(SessionKind.HOME) }!!
        assertEquals(listOf("push_ups", "pike_push_ups", "chair_dips", "plank"), home.exercises.map { it.exerciseId })
        val id = runBlocking { repo.startSession(home) }
        repo.logAll(id)
        runBlocking { repo.finishSession(id) }
        assertEquals(1, runBlocking { repo.db.plan().get() }!!.rotationIndex)
        assertTrue(runBlocking { repo.db.estimate().all() }.none { it.e1rmKg != null })
    }

    @Test
    fun machineBusyDoesTheNextExerciseFirstOrSwapsToABackupForToday() {
        val repo = TestRepo.createSetUp()
        val plan = runBlocking { repo.planForToday(SessionKind.FULL) }!!
        val bench = plan.exercises[0]
        val second = plan.exercises[1]
        assertEquals("barbell_bench_press", bench.exerciseId)
        val id = runBlocking { repo.startSession(plan) }

        runBlocking { repo.doNextFirst(id, bench.key) }
        val reordered = runBlocking { repo.sessionView(id) }!!.state
        assertEquals(second.key, reordered.current)
        assertEquals(listOf(second.key, bench.key), reordered.order.take(2))

        runBlocking { repo.swapForToday(id, bench.key) }
        val swapped = runBlocking { repo.sessionView(id) }!!.state
        val backup = swapped.exercise(bench.key)!!
        assertNotEquals("barbell_bench_press", backup.exerciseId)
        assertTrue(backup.exerciseId in runBlocking { repo.program() }.slot("push_a_1")!!.options)
        assertEquals("Swapped for today", backup.note)
        assertTrue("barbell_bench_press" in swapped.skipToday)
        // Only for this workout: a fresh plan still starts with the barbell bench press.
        assertEquals("barbell_bench_press", runBlocking { repo.planForToday(SessionKind.FULL) }!!.exercises[0].exerciseId)
    }

    @Test
    fun theLoggerSavesARampSetFromTheScreen() {
        val repo = TestRepo.createSetUp()
        val id = runBlocking { repo.startSession(repo.planForToday(SessionKind.FULL)!!) }
        compose.setContent { RtbTheme { CompositionLocalProvider(LocalRepo provides repo) { SessionScreen(id, {}, {}, {}) } } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Find your weight, set 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Do 8 reps, then say how many were left").performScrollTo()
        compose.onNodeWithContentDescription("Reps left 4+").performScrollTo().performClick()
        compose.onNodeWithText("Log set").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Find your weight, set 2").fetchSemanticsNodes().isNotEmpty() }
        val sets = runBlocking { repo.db.setLog().forSession(id) }
        assertEquals(1, sets.size)
        assertEquals(20.0 to 4, sets[0].weightKg to sets[0].repsLeft)
        assertTrue(sets[0].isRamp)
        compose.onNodeWithText("22.5").performScrollTo() // the suggested next ramp weight
    }
}
