package com.raisethebar.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raisethebar.app.data.AppClock
import com.raisethebar.app.data.AwayDraft
import com.raisethebar.app.data.Repo
import com.raisethebar.app.data.SetInput
import com.raisethebar.app.data.TodayKind
import com.raisethebar.app.data.activeChanges
import com.raisethebar.app.data.evaluateDays
import com.raisethebar.app.data.finishSession
import com.raisethebar.app.data.logSet
import com.raisethebar.app.data.planForToday
import com.raisethebar.app.data.previewAway
import com.raisethebar.app.data.saveAway
import com.raisethebar.app.data.startSession
import com.raisethebar.app.data.todayState
import com.raisethebar.app.data.undoChange
import com.raisethebar.app.ui.LocalRepo
import com.raisethebar.app.ui.plan.PlanChangesScreen
import com.raisethebar.app.ui.theme.RtbTheme
import com.raisethebar.engine.AbsenceKind
import com.raisethebar.engine.AwayEquipment
import com.raisethebar.engine.DayStatus
import com.raisethebar.engine.InputRejected
import com.raisethebar.engine.Outcome
import com.raisethebar.engine.SessionKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import kotlin.test.assertFailsWith

/** Milestone 10 (section 13.5) on the real data layer. */
@RunWith(AndroidJUnit4::class)
class PlanFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val monday = LocalDate.of(2026, 10, 19)
    private val thursday = monday.plusDays(3)
    private val sunday = monday.plusDays(6)

    private fun Repo.at(date: LocalDate, h: Int, m: Int = 0) =
        Repo(db, AppClock(Clock.fixed(date.atTime(h, m).atZone(TestRepo.zone).toInstant(), TestRepo.zone)))

    /** Completes today's workout on [date], optionally logging one bench set (chest). */
    private fun Repo.train(date: LocalDate, benchSet: Boolean = false) {
        val r = at(date, 8)
        runBlocking {
            val plan = r.planForToday(SessionKind.FULL)!!
            val id = r.startSession(plan)
            if (benchSet) r.logSet(id, SetInput(plan.exercises.first().key, 20.0, 8, 2))
            r.finishSession(id)
        }
    }

    @Test
    fun goingHomeThursdayToSundayWithDumbbells() {
        val base = TestRepo.createSetUp(monday)
        (0L..2L).forEach { base.train(monday.plusDays(it), benchSet = true) }
        val wednesdayEvening = base.at(monday.plusDays(2), 21, 40)
        val draft = AwayDraft(thursday, sunday, AwayEquipment.DUMBBELLS, 30)
        val preview = runBlocking { wednesdayEvening.previewAway(draft) }
        assertNull(preview.preTripDate)
        assertFalse(preview.countsSkips)
        assertEquals(
            listOf(
                "2026-10-22: Travel, paused",
                "2026-10-23: Dumbbell full body, 30 min",
                "2026-10-24: Rest",
                "2026-10-25: Dumbbell full body, 30 min",
                "2026-10-26: Push B, the next workout in the rotation",
            ),
            preview.lines.map { "${it.date}: ${it.text}" },
        )
        runBlocking { wednesdayEvening.saveAway(draft) }
        val friday = base.at(thursday.plusDays(1), 8)
        assertEquals(TodayKind.AWAY_SESSION, runBlocking { friday.todayState() }.kind)
        val nextMonday = base.at(monday.plusDays(7), 8)
        assertEquals(listOf<LocalDate>(), runBlocking { nextMonday.evaluateDays() })
        (22..25).forEach { d -> assertEquals(Outcome.AWAY, runBlocking { base.db.dayLog().get(LocalDate.of(2026, 10, d)) }?.outcome) }
        assertEquals(0, runBlocking { nextMonday.makeupState() }.sessionsLeft)
        assertEquals("Push B", runBlocking { nextMonday.todayState() }.plan!!.dayName)
    }

    @Test
    fun undoBeforeTheTripRestoresThePlanAndUndoDuringItResumesTomorrow() {
        val base = TestRepo.createSetUp(monday)
        val wednesday = base.at(monday.plusDays(2), 9)
        runBlocking { wednesday.saveAway(AwayDraft(thursday, sunday, AwayEquipment.NONE)) }
        val id = runBlocking { wednesday.activeChanges() }.single().id
        assertTrue(runBlocking { wednesday.dayStatus(thursday) } is DayStatus.Away)
        runBlocking { wednesday.undoChange(id) }
        assertEquals(DayStatus.Planned, runBlocking { wednesday.dayStatus(thursday) })
        assertTrue(runBlocking { wednesday.activeChanges() }.isEmpty())

        runBlocking { wednesday.saveAway(AwayDraft(thursday, sunday, AwayEquipment.NONE)) }
        val friday = base.at(thursday.plusDays(1), 9)
        val trip = runBlocking { friday.activeChanges() }.single { it.kind == AbsenceKind.AWAY }
        runBlocking { friday.undoChange(trip.id) }
        assertTrue(runBlocking { friday.dayStatus(thursday.plusDays(1)) } is DayStatus.Away) // today stays away
        assertEquals(DayStatus.Planned, runBlocking { friday.dayStatus(thursday.plusDays(2)) }) // Saturday trains again
    }

    @Test
    fun chestUntrainedForTooLongMakesTheLastSessionBeforeTheTripFullBody() {
        val base = TestRepo.createSetUp(monday)
        base.train(monday, benchSet = true) // chest last trained Monday
        val friday = base.at(monday.plusDays(4), 7)
        val trip = AwayDraft(monday.plusDays(5), monday.plusDays(8), AwayEquipment.BODYWEIGHT, 25) // Saturday to Tuesday, 4 days
        val preview = runBlocking { friday.previewAway(trip) }
        assertEquals(monday.plusDays(4), preview.preTripDate)
        assertEquals("Gym full body before the trip", preview.lines.first().text)
        runBlocking { friday.saveAway(trip) }
        val today = runBlocking { friday.todayState() }
        assertEquals(TodayKind.PRE_TRIP, today.kind)
        assertEquals("pre_trip_gym", today.plan!!.dayId)
        runBlocking { friday.finishSession(friday.startSession(today.plan)) }
        val after = runBlocking { friday.todayState() }
        assertEquals("pre_trip_gym", after.plan!!.dayId) // Today still shows the session that was done
        assertTrue(after.doneToday != null)
    }

    @Test
    fun theAwayFormChecksMinutesDatesAndOverlaps() {
        val wednesday = TestRepo.createSetUp(monday).at(monday.plusDays(2), 9)

        fun rejected(d: AwayDraft): String = assertFailsWith<InputRejected> { runBlocking { wednesday.previewAway(d) } }.field

        assertEquals("minutes", rejected(AwayDraft(thursday, sunday, AwayEquipment.DUMBBELLS, 19)))
        assertEquals("minutes", rejected(AwayDraft(thursday, sunday, AwayEquipment.DUMBBELLS, 46)))
        listOf(20, 45).forEach { m -> runBlocking { wednesday.previewAway(AwayDraft(thursday, sunday, AwayEquipment.DUMBBELLS, m)) } }
        assertEquals("start", rejected(AwayDraft(monday.plusDays(1), sunday, AwayEquipment.NONE)))
        assertEquals("end", rejected(AwayDraft(thursday, thursday.plusDays(61), AwayEquipment.NONE)))
        runBlocking { wednesday.saveAway(AwayDraft(thursday, sunday, AwayEquipment.NONE)) }
        assertEquals("dates", rejected(AwayDraft(sunday, sunday.plusDays(3), AwayEquipment.GYM)))
    }

    @Test
    fun theScreenDeclaresADayOff() {
        val repo = TestRepo.createSetUp()
        compose.setContent { RtbTheme { CompositionLocalProvider(LocalRepo provides repo) { PlanChangesScreen(onBack = {}) } } }
        // Both the day-off chip and the away form's start date read "Thu 22 Oct"; the chip comes first once both have loaded.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Thu 22 Oct").fetchSemanticsNodes().size >= 2 }
        compose.onAllNodesWithText("Thu 22 Oct")[0].performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Take Thursday off").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Take Thursday off").performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { repo.absences() }.isNotEmpty() }
        val off = runBlocking { repo.absences() }.single()
        assertEquals(AbsenceKind.DAY_OFF to LocalDate.of(2026, 10, 22), off.kind to off.start)
    }
}
