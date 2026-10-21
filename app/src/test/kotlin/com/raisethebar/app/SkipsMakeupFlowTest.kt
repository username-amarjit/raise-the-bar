package com.raisethebar.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raisethebar.app.data.AppClock
import com.raisethebar.app.data.Notifier
import com.raisethebar.app.data.Repo
import com.raisethebar.app.data.SetInput
import com.raisethebar.app.data.declareDayOff
import com.raisethebar.app.data.evaluateDays
import com.raisethebar.app.data.finishSession
import com.raisethebar.app.data.logSet
import com.raisethebar.app.data.planForToday
import com.raisethebar.app.data.rebuildCheckins
import com.raisethebar.app.data.recordReason
import com.raisethebar.app.data.sessionView
import com.raisethebar.app.data.startSession
import com.raisethebar.app.data.todayState
import com.raisethebar.app.data.updateProfile
import com.raisethebar.app.ui.LocalRepo
import com.raisethebar.app.ui.checkin.CheckinScreen
import com.raisethebar.app.ui.theme.RtbTheme
import com.raisethebar.engine.Checkin
import com.raisethebar.engine.Outcome
import com.raisethebar.engine.Reason
import com.raisethebar.engine.SessionKind
import com.raisethebar.engine.Skips
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Milestones 8 and 9 on the real data layer: check-ins, days off, the timeout and make-up mode. */
@RunWith(AndroidJUnit4::class)
class SkipsMakeupFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val monday = LocalDate.of(2026, 10, 19)

    private class FakeNotifier : Notifier {
        val scheduled = mutableListOf<Checkin>()
        val cancelled = mutableListOf<LocalDate>()

        override fun scheduleCheckins(checkins: List<Checkin>, previous: List<LocalDate>) {
            scheduled.clear()
            scheduled.addAll(checkins)
        }

        override fun cancelCheckin(date: LocalDate) {
            cancelled += date
        }
    }

    private fun Repo.at(date: LocalDate, h: Int, m: Int = 0, notifier: Notifier = Notifier.NONE) =
        Repo(db, AppClock(Clock.fixed(date.atTime(h, m).atZone(TestRepo.zone).toInstant(), TestRepo.zone)), notifier)

    private fun Repo.logAll(id: Long) {
        while (true) {
            val view = runBlocking { sessionView(id) }!!
            val pe = view.state.ordered.firstOrNull { !view.isComplete(it) } ?: break
            val input =
                if (pe.isTimed) {
                    SetInput(pe.key, null, null, null, seconds = pe.secLow)
                } else {
                    SetInput(
                        pe.key,
                        view.suggestedWeight(pe),
                        view.targetReps(pe) ?: 10,
                        2,
                    )
                }
            runBlocking { logSet(id, input) }
        }
    }

    @Test
    fun checkinsAreScheduledSevenDaysAheadMovedByGoingLaterAndCancelledOnStart() {
        val base = TestRepo.createSetUp(monday)
        runBlocking { base.updateProfile { it.copy(notificationsOn = true) } }
        val fake = FakeNotifier()
        val tuesday = base.at(monday.plusDays(1), 6, notifier = fake)
        runBlocking { tuesday.rebuildCheckins() }
        assertEquals(LocalDateTime.of(monday.plusDays(1), LocalTime.of(9, 30)), fake.scheduled.first().at)
        assertEquals(6, fake.scheduled.size) // Tuesday to Saturday and next Monday; Sunday is rest
        assertTrue(fake.scheduled.all { it.date.isBefore(monday.plusDays(8)) })

        assertTrue(runBlocking { tuesday.recordReason(monday.plusDays(1), Reason.GOING_LATER, LocalTime.of(18, 0)) })
        assertEquals(LocalDateTime.of(monday.plusDays(1), LocalTime.of(20, 0)), fake.scheduled.first().at)

        runBlocking { tuesday.startSession(tuesday.planForToday(SessionKind.FULL)!!) }
        assertTrue(monday.plusDays(1) in fake.cancelled)
        runBlocking { tuesday.rebuildCheckins() }
        assertFalse(fake.scheduled.any { it.date == monday.plusDays(1) })
    }

    @Test
    fun aThirdDayOffInOneMonthWarnsAndThenCountsAsASkip() {
        val base = TestRepo.createSetUp(monday)
        val sunday = base.at(monday.plusDays(6), 9)
        assertEquals(Skips.DayOffCheck.OK, runBlocking { sunday.declareDayOff(monday.plusDays(7), false) })
        assertEquals(Skips.DayOffCheck.OK, runBlocking { sunday.declareDayOff(monday.plusDays(8), false) })
        assertEquals(Skips.DayOffCheck.THIRD_THIS_MONTH, runBlocking { sunday.declareDayOff(monday.plusDays(9), false) })
        assertEquals(2, runBlocking { sunday.absences() }.size)
        assertEquals(Skips.DayOffCheck.THIRD_THIS_MONTH, runBlocking { sunday.declareDayOff(monday.plusDays(9), true) })
        assertTrue(runBlocking { sunday.absences() }.single { it.start == monday.plusDays(9) }.countsAsSkip)
        val later = base.at(monday.plusDays(10), 8)
        runBlocking { later.evaluateDays() }
        assertEquals(Outcome.DAY_OFF, runBlocking { base.db.dayLog().get(monday.plusDays(7)) }!!.outcome)
        assertEquals(Outcome.SKIP, runBlocking { base.db.dayLog().get(monday.plusDays(9)) }!!.outcome)
    }

    @Test
    fun aMinimumSessionStillGetsTheBlockAndSessionsLeftDropsOnlyWhenItIsLogged() {
        val base = TestRepo.createSetUp(monday)
        val wednesday = base.at(monday.plusDays(2), 8)
        runBlocking { wednesday.evaluateDays() }
        assertEquals(4, runBlocking { wednesday.makeupState() }.sessionsLeft)
        val minimum = runBlocking { wednesday.planForToday(SessionKind.MINIMUM) }!!
        assertEquals(3, minimum.exercises.size)
        assertEquals(2, minimum.makeup.size)
        val id = runBlocking { wednesday.startSession(minimum) }
        wednesday.logAll(id)
        assertEquals(4, runBlocking { wednesday.makeupState() }.sessionsLeft) // not until Finish
        runBlocking { wednesday.finishSession(id) }
        assertEquals(3, runBlocking { wednesday.makeupState() }.sessionsLeft)
    }

    @Test
    fun aSickDayPausesMakeupModeWithNoBlockAndTheCountUnchanged() {
        val base = TestRepo.createSetUp(monday)
        val wednesday = base.at(monday.plusDays(2), 8)
        runBlocking { wednesday.evaluateDays() }
        assertTrue(runBlocking { wednesday.recordReason(monday.plusDays(2), Reason.SICK) })
        val today = runBlocking { wednesday.todayState() }
        assertTrue(today.makeupPaused)
        assertTrue(today.plan!!.makeup.isEmpty())
        assertEquals(4, today.makeup.sessionsLeft)
    }

    @Test
    fun theCheckinSheetRecordsSkippingToday() {
        val repo = TestRepo.createSetUp()
        var closed = false
        val today = repo.time.today().toString()
        compose.setContent { RtbTheme { CompositionLocalProvider(LocalRepo provides repo) { CheckinScreen(today, { closed = true }, {}, {}) } } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Skip today").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Skip today").performScrollTo().performClick()
        compose.waitUntil(5_000) { closed }
        assertEquals(Reason.SKIPPING, runBlocking { repo.db.dayLog().get(repo.time.today()) }?.reason)
    }
}
