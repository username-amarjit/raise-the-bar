package com.raisethebar.engine

import com.raisethebar.config.Rules
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class AwayDayKind { TRAVEL, SESSION, REST, PAUSED }

data class AwayDay(
    val date: LocalDate,
    val index: Int,
    val kind: AwayDayKind,
    val templateId: String? = null,
    val minutes: Int? = null,
)

data class PreviewLine(
    val date: LocalDate,
    val text: String,
)

/** Away mode (milestone 10). Away days are never planned days, so they can't be skips or start make-up mode. */
object Away {
    /** round(days × 3 / 7), at least 1 and at most 3. */
    fun sessionCount(days: Int): Int =
        roundHalfUp(days * Rules.AWAY_SESSIONS_PER_WEEK / Rules.DAYS_PER_WEEK.toDouble())
            .coerceIn(Rules.AWAY_MIN_SESSIONS, Rules.AWAY_MAX_SESSIONS)

    /** Day 1 is travel; sessions then go on alternate days (2, 4, 6), within the trip. */
    fun sessionDays(days: Int): Set<Int> =
        (0 until sessionCount(days))
            .map { Rules.AWAY_FIRST_SESSION_DAY + it * Rules.AWAY_SESSION_EVERY_DAYS }
            .filter { it <= days }
            .toSet()

    fun plan(a: Absence, program: Program): List<AwayDay> = (1..a.days).map { dayAt(a, it, program) }

    fun dayOf(a: Absence, date: LocalDate, program: Program): AwayDay = dayAt(a, ChronoUnit.DAYS.between(a.start, date).toInt() + 1, program)

    private fun dayAt(a: Absence, index: Int, program: Program): AwayDay {
        val date = a.start.plusDays(index - 1L)
        val template = a.equipmentThere?.let { program.awayTemplates[it] }
        return when {
            template == null -> AwayDay(date, index, AwayDayKind.PAUSED)
            index == 1 -> AwayDay(date, index, AwayDayKind.TRAVEL)
            index in sessionDays(a.days) -> AwayDay(date, index, AwayDayKind.SESSION, template, a.minutesPerDay ?: Rules.AWAY_MINUTES_DEFAULT)
            else -> AwayDay(date, index, AwayDayKind.REST)
        }
    }

    /**
     * The pre-trip rule: if chest, back, quads or hamstrings-and-glutes would go more than 7 days untrained
     * (days since last trained + trip length), the last session before leaving becomes the gym full-body template.
     */
    fun preTripNeeded(tripStart: LocalDate, tripDays: Int, lastTrained: Map<String, LocalDate?>): Boolean =
        Rules.PRE_TRIP_GROUPS.any { g ->
            val last = lastTrained[g] ?: return@any true
            ChronoUnit.DAYS.between(last, tripStart) + tripDays > Rules.PRE_TRIP_MAX_UNTRAINED_DAYS
        }

    /** The last planned day before leaving, if it is still ahead (today or later) and not trained yet. */
    fun lastDayBefore(trip: Absence, today: LocalDate, isPlanned: (LocalDate) -> Boolean, doneOn: (LocalDate) -> Boolean): LocalDate? {
        var d = trip.start.minusDays(1)
        while (!d.isBefore(today)) {
            if (isPlanned(d)) return if (doneOn(d)) null else d
            d = d.minusDays(1)
        }
        return null
    }

    fun templateName(templateId: String?, program: Program): String = templateId?.let { program.dayOrNull(it)?.name } ?: "Session"

    /**
     * Undo: before the change starts it is cancelled, which restores the original plan exactly. During a trip the
     * remaining away days are cancelled (the trip ends today) and planned training resumes tomorrow.
     */
    fun undo(a: Absence, now: java.time.OffsetDateTime, today: LocalDate): Absence =
        when {
            today.isBefore(a.start) -> a.copy(cancelledAt = now)
            !today.isAfter(a.end) -> a.copy(end = today)
            else -> a
        }

    /** Each affected date and what happens on it. The screen adds "No skips counted". */
    fun preview(a: Absence, program: Program, preTripDate: LocalDate?, nextPlanned: LocalDate?, nextWorkout: String?): List<PreviewLine> {
        val lines = mutableListOf<PreviewLine>()
        if (preTripDate != null) lines += PreviewLine(preTripDate, "${program.day(program.preTrip).name} before the trip")
        if (a.equipmentThere == AwayEquipment.GYM) {
            lines += PreviewLine(a.start, "Normal plan at the gym there; skips count as usual")
            return lines
        }
        plan(a, program).forEach { d ->
            lines +=
                PreviewLine(
                    d.date,
                    when (d.kind) {
                        AwayDayKind.TRAVEL -> "Travel, paused"
                        AwayDayKind.SESSION -> "${templateName(d.templateId, program)}, ${d.minutes} min"
                        AwayDayKind.REST -> "Rest"
                        AwayDayKind.PAUSED -> "Paused"
                    },
                )
        }
        if (nextPlanned != null && nextWorkout != null) lines += PreviewLine(nextPlanned, "$nextWorkout, the next workout in the rotation")
        return lines
    }
}
