package com.raisethebar.app.data

import com.raisethebar.app.db.AbsenceEntity
import com.raisethebar.app.db.DayLogEntity
import com.raisethebar.app.db.MetaEntity
import com.raisethebar.engine.AbsenceKind
import com.raisethebar.engine.DayRecord
import com.raisethebar.engine.DayStatus
import com.raisethebar.engine.Days
import com.raisethebar.engine.Makeup
import com.raisethebar.engine.MakeupState
import com.raisethebar.engine.Outcome
import com.raisethebar.engine.SessionKind
import com.raisethebar.engine.SkipStats
import com.raisethebar.engine.Skips
import com.raisethebar.engine.Time
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime

private const val MAX_DAYS_TO_EVALUATE = 400

private fun Repo.nowZoned(): ZonedDateTime = time.now().atZoneSameInstant(time.zone)

/**
 * The midnight timeout, with no background job: on every app start and resume, each day from
 * meta.last_evaluated_date up to yesterday gets its outcome. New unexcused skips then run make-up mode in order.
 */
suspend fun Repo.evaluateDays(): List<LocalDate> {
    if (!isSetupDone()) return emptyList()
    val newSkips =
        write {
            val today = time.today()
            val yesterday = today.minusDays(1)
            val last = db.meta().get(Meta.LAST_EVALUATED_DATE)?.let(LocalDate::parse) ?: planStart().minusDays(1)
            var d = maxOf(last.plusDays(1), today.minusDays(MAX_DAYS_TO_EVALUATE.toLong()))
            if (d.isAfter(yesterday)) return@write emptyList()
            val schedule = schedule()
            val absences = absences()
            val program = program()
            val skips = mutableListOf<LocalDate>()
            while (!d.isAfter(yesterday)) {
                val status = Days.status(d, schedule, absences, program)
                val done = db.session().forDate(d).any { it.completedAt != null && it.kind in SessionKind.DONE_KINDS }
                val log = db.dayLog().get(d)
                val outcome = Skips.outcome(d, status, done, log?.reason, log?.reasonAt, time.zone)
                if (outcome != null || log != null) {
                    val planned = status == DayStatus.Planned || (status is DayStatus.DayOff && status.absence.countsAsSkip)
                    db.dayLog().upsert(DayLogEntity(d, planned, log?.reason, log?.reasonAt, outcome, log?.laterTime))
                }
                if (outcome == Outcome.SKIP) skips += d
                d = d.plusDays(1)
            }
            if (skips.isNotEmpty()) {
                val allSkips = db.dayLog().all().filter { it.outcome == Outcome.SKIP }.map { it.localDate }
                val settings = makeupSettings()
                val paused = Makeup.isPaused(sickToday = false, injuries = injuries())
                var state: MakeupState = makeupState()
                val start = planStart()
                for (skip in skips) {
                    val lastDone =
                        db
                            .session()
                            .between(skip.minusDays(60), skip.minusDays(1))
                            .filter { it.completedAt != null }
                            .maxOfOrNull { it.localDate }
                    state = Makeup.onSkip(state, skip, allSkips.filter { !it.isAfter(skip) }, lastDone, start, settings, paused)
                }
                saveMakeup(state)
            }
            db.meta().put(MetaEntity(Meta.LAST_EVALUATED_DATE, yesterday.toString()))
            skips
        }
    rebuildCheckins()
    return newSkips
}

/**
 * Records today's check-in answer. Reasons must come before that day's midnight; later ones are rejected.
 * Returns false when rejected.
 */
suspend fun Repo.recordReason(date: LocalDate, reason: String, laterTime: LocalTime? = null): Boolean {
    val ok =
        write {
            if (!Skips.canRecordReason(date, nowZoned())) return@write false
            val log = db.dayLog().get(date)
            db.dayLog().upsert(
                DayLogEntity(
                    localDate = date,
                    planned = dayStatus(date) == DayStatus.Planned,
                    reason = reason,
                    reasonAt = time.now(),
                    outcome = log?.outcome,
                    laterTime = laterTime?.let(Time::hhmm) ?: log?.laterTime,
                ),
            )
            true
        }
    if (ok) rebuildCheckins()
    return ok
}

suspend fun Repo.dayOffCutoff(): LocalTime = profile()?.dayOffCutoff?.let { runCatching { Time.parseHhmm(it) }.getOrNull() } ?: LocalTime.of(22, 0)

suspend fun Repo.checkDayOff(date: LocalDate): Skips.DayOffCheck =
    Skips.checkDayOff(date, nowZoned(), dayOffCutoff(), dayStatus(date), Skips.daysOffInMonth(absences(), YearMonth.from(date)))

/** Declares a day off. The 3rd in a calendar month is saved only after the warning, as an unexcused skip. */
suspend fun Repo.declareDayOff(date: LocalDate, acceptAsSkip: Boolean): Skips.DayOffCheck {
    val check = checkDayOff(date)
    val save = check == Skips.DayOffCheck.OK || (check == Skips.DayOffCheck.THIRD_THIS_MONTH && acceptAsSkip)
    if (!save) return check
    write {
        val id =
            db.absence().insert(
                AbsenceEntity(
                    kind = AbsenceKind.DAY_OFF,
                    start = date,
                    end = date,
                    equipmentThere = null,
                    minutesPerDay = null,
                    createdAt = time.now(),
                    cancelledAt = null,
                    countsAsSkip = check == Skips.DayOffCheck.THIRD_THIS_MONTH,
                ),
            )
        rememberChange(id)
    }
    rebuildCheckins()
    return check
}

internal suspend fun Repo.rememberChange(absenceId: Long) {
    db.plan().get()?.let { db.plan().upsert(it.copy(previous = """{"absence_id":$absenceId}""")) }
}

/** Schedules only the next 7 days of check-ins; called on every app open and after any change. */
suspend fun Repo.rebuildCheckins() {
    val previous =
        db.meta().get(Meta.CHECKINS_SCHEDULED)?.split(",")?.filter {
            it.isNotBlank()
        }?.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.orEmpty()
    val list =
        if (profile()?.notificationsOn == true && isSetupDone()) {
            val today = time.today()
            val schedule = schedule()
            val absences = absences()
            val program = program()
            val logs = db.dayLog().between(today, today.plusDays(7))
            Skips.checkins(
                now = LocalDateTime.now(time.clock),
                schedule = schedule,
                isPlanned = { Days.isPlanned(it, schedule, absences, program) },
                started = db.session().between(today, today.plusDays(7)).map { it.localDate }.toSet(),
                reasons = logs.mapNotNull { l -> l.reason?.let { l.localDate to it } }.toMap(),
                laterTimes = logs.mapNotNull { l -> l.laterTime?.let { l.localDate to Time.parseHhmm(it) } }.toMap(),
            )
        } else {
            emptyList()
        }
    notifier.scheduleCheckins(list, previous)
    tx { db.meta().put(MetaEntity(Meta.CHECKINS_SCHEDULED, list.joinToString(",") { it.date.toString() })) }
}

data class SkipScreen(
    val stats: SkipStats,
    val makeup: MakeupState,
    val banner: Boolean,
)

suspend fun Repo.skipScreen(): SkipScreen {
    val today = time.today()
    val records = db.dayLog().all().map { DayRecord(it.localDate, it.planned, it.reason, it.outcome) }
    val completed = db.session().all().filter { it.completedAt != null }.map { it.localDate }
    val skipDates = records.filter { it.outcome == Outcome.SKIP }.map { it.date }
    return SkipScreen(Skips.stats(records, completed, today), makeupState(), Skips.bannerShows(skipDates, today))
}
