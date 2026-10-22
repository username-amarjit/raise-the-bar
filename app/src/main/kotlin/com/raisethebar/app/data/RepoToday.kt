package com.raisethebar.app.data

import com.raisethebar.app.db.SessionEntity
import com.raisethebar.engine.Absence
import com.raisethebar.engine.AbsenceKind
import com.raisethebar.engine.Away
import com.raisethebar.engine.AwayDayKind
import com.raisethebar.engine.Calendar
import com.raisethebar.engine.CountedSet
import com.raisethebar.engine.DayStatus
import com.raisethebar.engine.Days
import com.raisethebar.engine.Equipment
import com.raisethebar.engine.Injuries
import com.raisethebar.engine.InjuryState
import com.raisethebar.engine.Makeup
import com.raisethebar.engine.MakeupItem
import com.raisethebar.engine.MakeupState
import com.raisethebar.engine.Outcome
import com.raisethebar.engine.Planner
import com.raisethebar.engine.ProgramDay
import com.raisethebar.engine.Reason
import com.raisethebar.engine.Selection
import com.raisethebar.engine.SessionKind
import com.raisethebar.engine.SessionPlan
import com.raisethebar.engine.Skips
import java.time.LocalDate

enum class TodayKind { TRAIN, PRE_TRIP, AWAY_SESSION, AWAY_TRAVEL, AWAY_REST, AWAY_PAUSED, DAY_OFF, REST }

data class TodayState(
    val date: LocalDate,
    val kind: TodayKind,
    val phaseLabel: String,
    val plan: SessionPlan?,
    val homeDay: ProgramDay?,
    val openSession: SessionEntity?,
    val doneToday: SessionEntity?,
    val restText: String,
    val makeup: MakeupState,
    val makeupPaused: Boolean,
    val skipsLast7: Int,
    val banner: Boolean,
    val reason: String?,
    val morningCheck: Boolean,
    val fixedPrompts: List<Equipment>,
    val injuries: List<InjuryState>,
    val physio: Boolean,
    val absence: Absence?,
    /** Unexcused skips in the 7 days ending today, oldest first. */
    val recentSkips: List<LocalDate> = emptyList(),
)

suspend fun Repo.todayState(): TodayState {
    val today = time.today()
    val program = program()
    val status = dayStatus(today)
    val phase = Calendar.phaseOn(planStart(), today)
    val open = db.session().open()
    val done = db.session().forDate(today).lastOrNull { it.completedAt != null }
    val makeup = makeupState()
    val injuries = injuries()
    val reason = db.dayLog().get(today)?.reason
    val skipDates = recentSkipDates(today)
    val rotation = program.rotationDay(db.plan().get()?.rotationIndex ?: 0)
    var plan: SessionPlan? = null
    var absence: Absence? = null
    val kind =
        when (status) {
            DayStatus.Rest -> TodayKind.REST
            is DayStatus.DayOff -> {
                absence = status.absence
                TodayKind.DAY_OFF
            }
            is DayStatus.Away -> {
                absence = status.absence
                when (status.day.kind) {
                    AwayDayKind.SESSION -> {
                        plan = Planner.build(program.day(status.day.templateId!!), SessionKind.AWAY, plannerInput(today))
                        TodayKind.AWAY_SESSION
                    }
                    AwayDayKind.TRAVEL -> TodayKind.AWAY_TRAVEL
                    AwayDayKind.REST -> TodayKind.AWAY_REST
                    AwayDayKind.PAUSED -> TodayKind.AWAY_PAUSED
                }
            }
            DayStatus.Planned -> {
                val preTrip = preTripToday(today)
                plan =
                    if (preTrip) {
                        buildPlan(program.day(program.preTrip), SessionKind.PRE_TRIP, today, withMakeup = true)
                    } else {
                        buildPlan(rotation, SessionKind.FULL, today, withMakeup = true)
                    }
                if (preTrip) TodayKind.PRE_TRIP else TodayKind.TRAIN
            }
        }
    // After today's workout, show the session that was done, not the next one in the rotation.
    if (done != null && open == null) decodeState(done.state)?.plan?.let { plan = it }
    val morningAsked = db.meta().get(Meta.MORNING_CHECK_DATE) == today.toString()
    return TodayState(
        date = today,
        kind = kind,
        phaseLabel = phase.label,
        plan = plan,
        homeDay = program.homeDay(rotation.dayType),
        openSession = open,
        doneToday = done,
        restText = program.restDayText,
        makeup = makeup,
        makeupPaused = Makeup.isPaused(reason == Reason.SICK, injuries),
        skipsLast7 = Skips.skipsInWindow(skipDates, today),
        banner = Skips.bannerShows(skipDates, today),
        reason = reason,
        morningCheck = !morningAsked && injuries.any { it.isActive },
        fixedPrompts = fixedPrompts(),
        injuries = injuries,
        physio = injuries.any { Injuries.needsPhysio(it, today) },
        absence = absence,
        recentSkips = skipDates.filter { !it.isBefore(today.minusDays(6)) }.sorted(),
    )
}

suspend fun Repo.recentSkipDates(today: LocalDate): List<LocalDate> =
    db.dayLog().between(today.minusDays(13), today).filter { it.outcome == Outcome.SKIP }.map { it.localDate }

/** Builds a session plan, appending today's make-up block when make-up mode is running and not paused. */
suspend fun Repo.buildPlan(day: ProgramDay, kind: String, date: LocalDate, withMakeup: Boolean, skipToday: Set<String> = emptySet()): SessionPlan {
    val input = plannerInput(date, skipToday)
    val draft = Planner.build(day, kind, input)
    if (!withMakeup) return draft
    val block = makeupBlock(date, draft.groups(input.ctx.exercises))
    return if (block.isEmpty()) draft else Planner.build(day, kind, input, block)
}

suspend fun Repo.makeupBlock(date: LocalDate, todayGroups: Set<String>): List<MakeupItem> {
    val state = makeupState()
    if (!state.active) return emptyList()
    val sick = db.dayLog().get(date)?.reason == Reason.SICK
    if (Makeup.isPaused(sick, injuries())) return emptyList()
    val program = program()
    val lib = exercises()
    val ctx = ctx(date)
    return Makeup.buildBlock(todayGroups, weeklySets(date), program.weeklyTargets, program.makeupCandidates) { id ->
        lib[id]?.let { Selection.available(it, ctx) } == true
    }
}

/** Hard sets per muscle group over the rolling 7 days ending [date]. */
suspend fun Repo.weeklySets(date: LocalDate): Map<String, Int> {
    val sets = db.setLog().completedBetween(date.minusDays(6), date)
    return Makeup.weeklySets(sets.map { CountedSet(it.sessionId, it.exerciseId, it.isWarmup, it.isRamp) }, exercises())
}

/** The last day each muscle group got a counted set, from sessions completed before [before]. */
suspend fun Repo.lastTrained(before: LocalDate): Map<String, LocalDate?> {
    val lib = exercises()
    val out = mutableMapOf<String, LocalDate?>()
    db.session().between(before.minusDays(60), before.minusDays(1)).filter { it.completedAt != null }.sortedBy { it.localDate }.forEach { s ->
        db.setLog().forSession(s.id).filter { !it.isWarmup }.forEach { set ->
            lib[set.exerciseId]?.countsToward?.forEach { g -> out[g] = s.localDate }
        }
    }
    return out
}

/** Today becomes the gym full-body session when it is the last planned day before a trip and the pre-trip rule applies. */
suspend fun Repo.preTripToday(today: LocalDate): Boolean {
    val trips = absences().filter { it.kind == AbsenceKind.AWAY && it.isActive && it.pausesPlan && it.start.isAfter(today) }
    val trip = trips.minByOrNull { it.start } ?: return false
    val schedule = schedule()
    val absences = absences()
    val program = program()
    val doneDates = db.session().between(today, trip.start).filter { it.completedAt != null }.map { it.localDate }.toSet()
    val last = Away.lastDayBefore(trip, today, { Days.isPlanned(it, schedule, absences, program) }) { it in doneDates }
    return last == today && Away.preTripNeeded(trip.start, trip.days, lastTrained(trip.start))
}
