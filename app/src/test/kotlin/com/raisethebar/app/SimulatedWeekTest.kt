package com.raisethebar.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raisethebar.app.data.AppClock
import com.raisethebar.app.data.Repo
import com.raisethebar.app.data.TodayKind
import com.raisethebar.app.data.TodayState
import com.raisethebar.app.data.declareDayOff
import com.raisethebar.app.data.evaluateDays
import com.raisethebar.app.data.finishSession
import com.raisethebar.app.data.planForToday
import com.raisethebar.app.data.skipScreen
import com.raisethebar.app.data.startSession
import com.raisethebar.app.data.todayState
import com.raisethebar.app.ui.LocalRepo
import com.raisethebar.app.ui.progress.ProgressScreen
import com.raisethebar.app.ui.theme.RtbTheme
import com.raisethebar.engine.Outcome
import com.raisethebar.engine.SessionKind
import com.raisethebar.engine.Skips.DayOffCheck
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate

/** Milestone 14: Monday to Saturday with one unexcused skip and one declared day off, on the real data layer. */
@RunWith(AndroidJUnit4::class)
class SimulatedWeekTest {
    @get:Rule
    val compose = createComposeRule()

    private val monday = LocalDate.of(2026, 10, 26)

    private fun day(n: Long) = monday.plusDays(n)

    private fun Repo.at(date: LocalDate, h: Int) = Repo(db, AppClock(Clock.fixed(date.atTime(h, 0).atZone(TestRepo.zone).toInstant(), TestRepo.zone)))

    /** What every app open does: the midnight timeout, then today's screen. */
    private fun Repo.open(): TodayState =
        runBlocking {
            evaluateDays()
            todayState()
        }

    /** Opens the app, notes the day's workout and trains it. */
    private fun Repo.train(): String {
        val name = open().plan!!.dayName
        runBlocking { finishSession(startSession(planForToday(SessionKind.FULL)!!)) }
        return name
    }

    private class Week(
        val repo: Repo,
        val trained: List<String>,
        val friday: TodayKind,
        val sunday: TodayKind,
        val nextMonday: TodayState,
    )

    /** Trains Monday, Tuesday, Thursday and Saturday; Wednesday is missed and Friday is declared off on Thursday. */
    private fun runWeek(): Week {
        val repo = TestRepo.createSetUp(monday, 6)
        val trained = mutableListOf<String>()
        trained += repo.at(day(0), 8).train()
        trained += repo.at(day(1), 8).train()
        val thursday = repo.at(day(3), 8)
        trained += thursday.train()
        assertEquals(DayOffCheck.OK, runBlocking { thursday.declareDayOff(day(4), acceptAsSkip = false) })
        val friday = repo.at(day(4), 8).open().kind
        trained += repo.at(day(5), 8).train()
        val sunday = repo.at(day(6), 8).open().kind
        return Week(repo, trained, friday, sunday, repo.at(day(7), 8).open())
    }

    @Test
    fun aWeekWithOneSkipAndOneDayOff() {
        val week = runWeek()
        val repo = week.repo
        assertEquals(TodayKind.DAY_OFF to TodayKind.REST, week.friday to week.sunday)

        // The rotation only moves on completed sessions: the skip and the day off don't use up a workout.
        assertEquals(listOf("Push A", "Pull A", "Legs A", "Push B"), week.trained)
        assertEquals("Pull B", week.nextMonday.plan!!.dayName)

        val outcomes = runBlocking { repo.db.dayLog().all() }.associate { it.localDate to it.outcome }
        assertEquals(
            mapOf(
                day(0) to Outcome.DONE,
                day(1) to Outcome.DONE,
                day(2) to Outcome.SKIP,
                day(3) to Outcome.DONE,
                day(4) to Outcome.DAY_OFF,
                day(5) to Outcome.DONE,
            ),
            outcomes,
        )

        // One unexcused skip in 7 days is counted but doesn't start make-up mode (that takes 2).
        val makeup = runBlocking { repo.makeupState() }
        assertEquals(1 to 0, makeup.skipsCounted to makeup.sessionsLeft)
        val stats = runBlocking { repo.at(day(5), 21).skipScreen() }.stats
        assertEquals(4 to 1, stats.doneThisMonth to stats.skipsLast7)
    }

    @Test
    fun theProgressTabShowsTheWeek() {
        val saturdayEvening = runWeek().repo.at(day(5), 21)
        compose.setContent { RtbTheme { CompositionLocalProvider(LocalRepo provides saturdayEvening) { ProgressScreen() } } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Sessions done this month").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Wednesday 28 October").performScrollTo()
        compose.onNodeWithText("Sat 31 Oct · Push B").performScrollTo()
        compose.onNodeWithText("Mon 26 Oct · Push A").performScrollTo()
    }
}
