package com.raisethebar.engine

import com.raisethebar.config.Rules
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

object Outcome {
    const val DONE = "done"
    const val EXCUSED = "excused"
    const val DAY_OFF = "day_off"
    const val AWAY = "away"
    const val SKIP = "skip"
}

object Reason {
    const val SICK = "sick"
    const val INJURED = "injured"
    const val GYM_CLOSED = "gym_closed"
    const val GOING_LATER = "going_later"
    const val SKIPPING = "skipping"

    val EXCUSING = setOf(SICK, INJURED)

    fun label(reason: String?): String =
        when (reason) {
            SICK -> "Sick"
            INJURED -> "Injured"
            GYM_CLOSED -> "Gym closed"
            GOING_LATER -> "Going later"
            SKIPPING -> "Skipping today"
            null -> "No reason by midnight"
            else -> reason
        }
}

/** A check-in notification: the planned time + 2 hours on a planned day with no session started. */
data class Checkin(
    val date: LocalDate,
    val at: LocalDateTime,
)

data class SkipStats(
    val doneThisMonth: Int,
    val plannedThisMonth: Int,
    val donePlannedThisMonth: Int,
    val skips: List<Pair<LocalDate, String?>>,
    val skipsLast7: Int,
) {
    /** done ÷ planned, where excused days still count as planned. */
    val adherence: Double? get() = if (plannedThisMonth == 0) null else donePlannedThisMonth.toDouble() / plannedThisMonth
}

/** One evaluated day, as stored in day_log. */
data class DayRecord(
    val date: LocalDate,
    val planned: Boolean,
    val reason: String?,
    val outcome: String?,
)

/** Skips, check-ins and the midnight timeout (milestone 8). */
object Skips {
    /**
     * The timeout for one past day. Done: a session was completed that day (it counts for the day it started).
     * Excused: sick or injured recorded before that day's midnight. Day off or away: not a skip.
     * Otherwise: an unexcused skip. Rest days return null (nothing to record).
     */
    fun outcome(date: LocalDate, status: DayStatus, sessionDone: Boolean, reason: String?, reasonAt: OffsetDateTime?, zone: ZoneId): String? =
        when (status) {
            DayStatus.Rest -> if (sessionDone) Outcome.DONE else null
            is DayStatus.Away -> if (sessionDone) Outcome.DONE else Outcome.AWAY
            is DayStatus.DayOff ->
                if (sessionDone) {
                    Outcome.DONE
                } else if (status.absence.countsAsSkip) {
                    Outcome.SKIP
                } else {
                    Outcome.DAY_OFF
                }
            DayStatus.Planned ->
                when {
                    sessionDone -> Outcome.DONE
                    reason in Reason.EXCUSING && reasonAt != null && reasonBeforeMidnight(date, reasonAt, zone) -> Outcome.EXCUSED
                    else -> Outcome.SKIP
                }
        }

    fun reasonBeforeMidnight(date: LocalDate, reasonAt: OffsetDateTime, zone: ZoneId): Boolean =
        reasonAt.toInstant().isBefore(Time.endOfDay(date, zone).toInstant())

    /** Reasons can only be given on the day itself, before its midnight. Reasons after midnight are rejected. */
    fun canRecordReason(date: LocalDate, now: ZonedDateTime): Boolean = now.toLocalDate() == date && now.isBefore(Time.endOfDay(date, now.zone))

    fun dayOffCutoff(date: LocalDate, cutoff: LocalTime, zone: ZoneId): ZonedDateTime = date.minusDays(1).atTime(cutoff).atZone(zone)

    enum class DayOffCheck { OK, TOO_LATE, NOT_PLANNED, THIRD_THIS_MONTH }

    /** A day off is declared until the cut-off (default 22:00 the night before); the 3rd in a month counts as a skip. */
    fun checkDayOff(date: LocalDate, now: ZonedDateTime, cutoff: LocalTime, status: DayStatus, daysOffThisMonth: Int): DayOffCheck =
        when {
            !now.isBefore(dayOffCutoff(date, cutoff, now.zone)) -> DayOffCheck.TOO_LATE
            status != DayStatus.Planned -> DayOffCheck.NOT_PLANNED
            daysOffThisMonth >= Rules.DAY_OFF_PER_MONTH -> DayOffCheck.THIRD_THIS_MONTH
            else -> DayOffCheck.OK
        }

    fun daysOffInMonth(absences: List<Absence>, month: YearMonth): Int =
        absences.count { it.kind == AbsenceKind.DAY_OFF && it.isActive && YearMonth.from(it.start) == month }

    /** Check-ins for the next 7 days only (iOS-style limits apply on Android too); rebuilt on every app open. */
    fun checkins(
        now: LocalDateTime,
        schedule: List<ScheduleDay>,
        isPlanned: (LocalDate) -> Boolean,
        started: Set<LocalDate>,
        reasons: Map<LocalDate, String>,
        laterTimes: Map<LocalDate, LocalTime>,
    ): List<Checkin> {
        val today = now.toLocalDate()
        return (0 until Rules.CHECKIN_DAYS_AHEAD).mapNotNull { i ->
            val d = today.plusDays(i.toLong())
            val reason = reasons[d]
            val time = laterTimes[d] ?: Days.timeOn(schedule, d)
            when {
                !isPlanned(d) || d in started -> null
                reason != null && reason != Reason.GOING_LATER -> null
                time == null -> null
                else -> Checkin(d, d.atTime(time).plusMinutes(Rules.CHECKIN_DELAY_MIN.toLong())).takeIf { it.at.isAfter(now) }
            }
        }
    }

    /** Unexcused skips in the 7 days ending [day], including [day]. */
    fun skipsInWindow(skipDates: Collection<LocalDate>, day: LocalDate): Int =
        skipDates.count { !it.isAfter(day) && !it.isBefore(day.minusDays(Rules.MAKEUP_WINDOW_DAYS - 1L)) }

    /** "Four skips this week. Would a different training time work better?" */
    fun bannerShows(skipDates: Collection<LocalDate>, today: LocalDate): Boolean = skipsInWindow(skipDates, today) >= Rules.SKIP_BANNER_AT

    /** The skip screen numbers for the month containing [today]. */
    fun stats(records: List<DayRecord>, completedSessionDates: List<LocalDate>, today: LocalDate): SkipStats {
        val month = YearMonth.from(today)
        val inMonth = records.filter { YearMonth.from(it.date) == month && it.date.isBefore(today) }
        val planned = inMonth.filter { it.outcome == Outcome.DONE && it.planned || it.outcome == Outcome.EXCUSED || it.outcome == Outcome.SKIP }
        val skips = records.filter { it.outcome == Outcome.SKIP }.sortedByDescending { it.date }
        return SkipStats(
            doneThisMonth = completedSessionDates.count { YearMonth.from(it) == month },
            plannedThisMonth = planned.size,
            donePlannedThisMonth = planned.count { it.outcome == Outcome.DONE },
            skips = skips.map { it.date to it.reason },
            skipsLast7 = skipsInWindow(skips.map { it.date }, today),
        )
    }
}
