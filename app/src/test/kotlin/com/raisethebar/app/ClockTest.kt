package com.raisethebar.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raisethebar.app.data.AppClock
import com.raisethebar.app.data.Repo
import com.raisethebar.app.data.declareDayOff
import com.raisethebar.app.data.evaluateDays
import com.raisethebar.app.data.finishSession
import com.raisethebar.app.data.foodDay
import com.raisethebar.app.data.logMorningWeight
import com.raisethebar.app.data.planForToday
import com.raisethebar.app.data.startSession
import com.raisethebar.engine.Outcome
import com.raisethebar.engine.SessionKind
import com.raisethebar.engine.Skips.DayOffCheck
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate

/** Milestone 14: the data layer across midnight and month ends, with the clock in India time (Asia/Kolkata). */
@RunWith(AndroidJUnit4::class)
class ClockTest {
    private fun oct(d: Int) = LocalDate.of(2026, 10, d)

    private fun nov(d: Int) = LocalDate.of(2026, 11, d)

    private fun Repo.at(date: LocalDate, h: Int, m: Int = 0, s: Int = 0) =
        Repo(db, AppClock(Clock.fixed(date.atTime(h, m, s).atZone(TestRepo.zone).toInstant(), TestRepo.zone)))

    @Test
    fun saturday31OctoberTimesOutAtMidnightIntoNovember() {
        val repo = TestRepo.createSetUp(oct(31), 6)
        assertEquals(emptyList<LocalDate>(), runBlocking { repo.at(oct(31), 23, 59, 59).evaluateDays() })
        assertNull(runBlocking { repo.db.dayLog().get(oct(31)) })
        assertEquals(listOf(oct(31)), runBlocking { repo.at(nov(1), 0, 0, 0).evaluateDays() })
        assertEquals(Outcome.SKIP, runBlocking { repo.db.dayLog().get(oct(31)) }!!.outcome)
    }

    @Test
    fun aWorkoutStartedAt2350On31OctoberCountsForOctober() {
        val repo = TestRepo.createSetUp(oct(31), 6)
        val late = repo.at(oct(31), 23, 50)
        val id = runBlocking { late.startSession(late.planForToday(SessionKind.FULL)!!) }
        runBlocking { repo.at(nov(1), 0, 20).finishSession(id) }
        assertEquals(oct(31), runBlocking { repo.db.session().get(id) }!!.localDate)
        assertEquals(emptyList<LocalDate>(), runBlocking { repo.at(nov(1), 0, 30).evaluateDays() })
        assertEquals(Outcome.DONE, runBlocking { repo.db.dayLog().get(oct(31)) }!!.outcome)
    }

    @Test
    fun theDayOffAllowanceStartsAgainInNovember() {
        val monday = TestRepo.createSetUp(oct(26), 8)
        runBlocking {
            assertEquals(DayOffCheck.OK, monday.declareDayOff(oct(29), acceptAsSkip = false))
            assertEquals(DayOffCheck.OK, monday.declareDayOff(oct(30), acceptAsSkip = false))
            assertEquals(DayOffCheck.THIRD_THIS_MONTH, monday.declareDayOff(oct(31), acceptAsSkip = false))
            assertEquals(DayOffCheck.OK, monday.declareDayOff(nov(2), acceptAsSkip = false))
        }
    }

    @Test
    fun theCutoffAt2200On30NovemberGuardsTuesday1December() {
        val repo = TestRepo.createSetUp(nov(30), 6)
        val december1 = LocalDate.of(2026, 12, 1)
        assertEquals(DayOffCheck.TOO_LATE, runBlocking { repo.at(nov(30), 22, 0).declareDayOff(december1, acceptAsSkip = false) })
        assertEquals(DayOffCheck.OK, runBlocking { repo.at(nov(30), 21, 59).declareDayOff(december1, acceptAsSkip = false) })
    }

    @Test
    fun theSevenDayWeightAverageSpansTheMonthEnd() {
        val repo = TestRepo.createSetUp(oct(30), 6)
        runBlocking {
            repo.at(oct(30), 7).logMorningWeight(70.0)
            repo.at(oct(31), 7).logMorningWeight(70.4)
            repo.at(nov(1), 7).logMorningWeight(70.8)
        }
        assertEquals(70.4, runBlocking { repo.at(nov(1), 9).foodDay() }!!.average!!, 1e-9)
        assertNull(runBlocking { repo.at(nov(6), 9).foodDay() }!!.average) // 31 Oct to 6 Nov holds only two weigh-ins
    }
}
