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
import com.raisethebar.app.data.buildPlan
import com.raisethebar.app.data.finishSession
import com.raisethebar.app.data.logSet
import com.raisethebar.app.data.markChecked
import com.raisethebar.app.data.morningCheck
import com.raisethebar.app.data.planForToday
import com.raisethebar.app.data.previewInjury
import com.raisethebar.app.data.sessionView
import com.raisethebar.app.data.startInjury
import com.raisethebar.app.data.startSession
import com.raisethebar.app.data.toModel
import com.raisethebar.app.data.withState
import com.raisethebar.app.db.EstimateEntity
import com.raisethebar.app.ui.LocalRepo
import com.raisethebar.app.ui.injury.InjuryScreen
import com.raisethebar.app.ui.theme.RtbTheme
import com.raisethebar.engine.Injuries
import com.raisethebar.engine.InjuryStatus
import com.raisethebar.engine.SessionKind
import com.raisethebar.engine.Side
import com.raisethebar.engine.Triage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Duration

/** Milestone 11 (section 13.6) on the real data layer. */
@RunWith(AndroidJUnit4::class)
class InjuryFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val leftShoulder =
        Triage.Answers(
            "shoulder",
            Side.LEFT,
            feel = Triage.FEEL_SHARP,
            onset = Triage.ONSET_DURING_SET,
            easesWithWarmUp = false,
            painNow = 4,
        )

    private fun Repo.plan(dayId: String) = runBlocking { buildPlan(program().day(dayId), SessionKind.FULL, time.today(), withMakeup = false) }

    private fun Repo.later(days: Long) = Repo(db, AppClock(Clock.offset(time.clock, Duration.ofDays(days))))

    @Test
    fun leftShoulderPain4SwapsPressingAndDipsWhileLegsAndRowsStay() {
        val repo = TestRepo.createSetUp()
        val preview = runBlocking { repo.previewInjury(leftShoulder) }
        assertEquals("Left shoulder", preview.name)
        assertTrue(preview.takenOut.containsAll(listOf("Barbell bench press", "Barbell overhead press", "Dips")))
        assertTrue(preview.swaps >= 4)
        val legsBefore = repo.plan("legs_a").exercises.map { it.exerciseId }
        val rowsBefore = repo.plan("pull_a").exercises.first { it.slotId == "pull_a_2" }.exerciseId
        runBlocking { repo.startInjury(leftShoulder) }
        val bench = repo.plan("push_a").exercises.first()
        assertEquals("dumbbell_bench_press", bench.exerciseId)
        assertEquals("Left shoulder: backup 2", bench.note)
        assertFalse(repo.plan("push_b").exercises.any { it.exerciseId in setOf("barbell_overhead_press", "dips", "incline_barbell_bench_press") })
        assertEquals(legsBefore, repo.plan("legs_a").exercises.map { it.exerciseId })
        assertEquals(rowsBefore, repo.plan("pull_a").exercises.first { it.slotId == "pull_a_2" }.exerciseId)
    }

    @Test
    fun aRedFlagLocksTheAreaUntilIveBeenChecked() {
        val repo = TestRepo.createSetUp()
        val id = runBlocking { repo.startInjury(leftShoulder.copy(redFlags = setOf("pop"))) }
        assertTrue(runBlocking { repo.injuries() }.single().locked)
        assertFalse(repo.plan("pull_a").exercises.any { it.exerciseId == "seated_cable_row" })
        runBlocking { repo.markChecked(id) }
        assertTrue(repo.plan("pull_a").exercises.any { it.exerciseId == "seated_cable_row" })
    }

    @Test
    fun morningPainWorseThanTheUsualMorningStepsBackAPhase() {
        val repo = TestRepo.createSetUp()
        val id = runBlocking { repo.startInjury(leftShoulder.copy(painNow = 2)) }
        val tomorrow = repo.later(1)
        runBlocking { tomorrow.morningCheck(mapOf(id to (1 to false))) }
        assertEquals(2, runBlocking { repo.db.injury().get(id) }!!.phase) // green after a day: phase 2
        runBlocking {
            val row = repo.db.injury().get(id)!!
            repo.db.injury().update(row.withState(Injuries.enter(row.toModel(), 3, tomorrow.time.today())))
        }
        runBlocking { repo.later(2).morningCheck(mapOf(id to (4 to false))) }
        assertEquals(2, runBlocking { repo.db.injury().get(id) }!!.phase)
    }

    @Test
    fun injuryModeSetsLeaveEstimatesAloneAndPhase4RestoresThePreInjuryValues() {
        val repo = TestRepo.createSetUp()
        runBlocking { repo.db.estimate().upsert(EstimateEntity("seated_dumbbell_shoulder_press", 30.0, 20.0, 0, 4, true, repo.time.now())) }
        val id = runBlocking { repo.startInjury(leftShoulder) }
        runBlocking {
            val row = repo.db.injury().get(id)!!
            val p4 = Injuries.enter(row.toModel(), 4, repo.time.today()).copy(greenStreak = 1)
            repo.db.injury().update(row.withState(p4))
            repo.db.estimate().upsert(EstimateEntity("seated_dumbbell_shoulder_press", 25.0, 17.5, 0, 5, true, repo.time.now()))
        }
        val plan = runBlocking { repo.planForToday(SessionKind.FULL) }!!
        val press = plan.exercises.first { it.slotId == "push_a_3" }
        assertEquals("seated_dumbbell_shoulder_press", press.exerciseId)
        assertTrue(press.injuryLoad && press.painRequired)
        assertEquals(20.0, press.weight!!, 1e-9) // phase 4: the pre-injury weight
        val session = runBlocking { repo.startSession(plan) }
        repeat(press.sets) { runBlocking { repo.logSet(session, SetInput(press.key, 20.0, 10, 2, pain = 1)) } }
        assertTrue(runBlocking { repo.sessionView(session) }!!.setsFor(press.key).all { it.inInjuryMode })
        runBlocking { repo.finishSession(session) }
        val row = runBlocking { repo.db.injury().get(id) }!!
        assertEquals(InjuryStatus.RESOLVED, row.status)
        val est = runBlocking { repo.db.estimate().get("seated_dumbbell_shoulder_press") }!!
        assertEquals(30.0 to 20.0, est.e1rmKg to est.currentWeightKg) // normal estimates continue from the saved values
    }

    @Test
    fun triageScreenAppliesShoulderModePhase1() {
        val repo = TestRepo.createSetUp()
        var done = false
        compose.setContent { RtbTheme { CompositionLocalProvider(LocalRepo provides repo) { InjuryScreen(-1, onDone = { done = true }) } } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Something hurts · Step 1 of 3").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithContentDescription("Pain 4, amber: hold").performScrollTo().performClick()
        compose.onNodeWithText("See what changes").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Left shoulder mode, phase 1.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Apply to my plan").performScrollTo().performClick()
        compose.waitUntil(5_000) { done }
        val injury = runBlocking { repo.injuries() }.single()
        assertEquals("shoulder" to 1, injury.area to injury.phase)
    }
}
