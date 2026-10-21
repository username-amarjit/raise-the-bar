package com.raisethebar.engine

import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/** One weekday in the schedule: 0 = Monday … 6 = Sunday. */
data class ScheduleDay(
    val weekday: Int,
    val training: Boolean,
    val time: LocalTime,
)

object Weekdays {
    fun index(date: LocalDate): Int = date.dayOfWeek.value - 1

    val NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /** Default schedule: Monday to Saturday at 07:30, rest on Sunday. */
    fun defaults(): List<ScheduleDay> = (0..6).map { ScheduleDay(it, it < 6, LocalTime.of(7, 30)) }
}

object AbsenceKind {
    const val DAY_OFF = "day_off"
    const val AWAY = "away"
}

object AwayEquipment {
    const val NONE = "none"
    const val BODYWEIGHT = "bodyweight"
    const val DUMBBELLS = "dumbbells"
    const val GYM = "gym"
}

data class Absence(
    val id: Long,
    val kind: String,
    val start: LocalDate,
    val end: LocalDate,
    val equipmentThere: String? = null,
    val minutesPerDay: Int? = null,
    val createdAt: OffsetDateTime? = null,
    val cancelledAt: OffsetDateTime? = null,
    val countsAsSkip: Boolean = false,
) {
    val isActive: Boolean get() = cancelledAt == null

    fun covers(d: LocalDate): Boolean = isActive && !d.isBefore(start) && !d.isAfter(end)

    val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1

    /** Away with a gym keeps the normal plan, so those days stay planned and skips count as usual. */
    val pausesPlan: Boolean get() = kind == AbsenceKind.DAY_OFF || equipmentThere != AwayEquipment.GYM
}

sealed interface DayStatus {
    /** Not a training weekday. */
    data object Rest : DayStatus

    data class DayOff(
        val absence: Absence,
    ) : DayStatus

    data class Away(
        val absence: Absence,
        val day: AwayDay,
    ) : DayStatus

    /** A planned training day: the only kind of day that can be a skip. */
    data object Planned : DayStatus
}

object Days {
    fun isTrainingWeekday(schedule: List<ScheduleDay>, date: LocalDate): Boolean =
        schedule.firstOrNull { it.weekday == Weekdays.index(date) }?.training == true

    fun timeOn(schedule: List<ScheduleDay>, date: LocalDate): LocalTime? =
        schedule.firstOrNull { it.weekday == Weekdays.index(date) && it.training }?.time

    /** Planned days are the schedule's training days, minus declared days off and away days. */
    fun status(date: LocalDate, schedule: List<ScheduleDay>, absences: List<Absence>, program: Program): DayStatus {
        absences
            .firstOrNull { it.kind == AbsenceKind.AWAY && it.pausesPlan && it.covers(date) }
            ?.let { return DayStatus.Away(it, Away.dayOf(it, date, program)) }
        if (!isTrainingWeekday(schedule, date)) return DayStatus.Rest
        absences.firstOrNull { it.kind == AbsenceKind.DAY_OFF && it.covers(date) }?.let { return DayStatus.DayOff(it) }
        return DayStatus.Planned
    }

    fun isPlanned(date: LocalDate, schedule: List<ScheduleDay>, absences: List<Absence>, program: Program): Boolean =
        status(date, schedule, absences, program) == DayStatus.Planned

    /** The next planned day on or after [from], looking ahead at most [limit] days. */
    fun nextPlanned(from: LocalDate, isPlanned: (LocalDate) -> Boolean, limit: Int = 60): LocalDate? =
        generateSequence(from) { it.plusDays(1) }.take(limit).firstOrNull(isPlanned)

    /**
     * The rotation position on a future planned [date]: the current position plus the planned days still to come
     * before it, assuming each one is completed. A missed day never skips a workout.
     */
    fun rotationIndexOn(date: LocalDate, today: LocalDate, rotationIndex: Int, isPlanned: (LocalDate) -> Boolean, doneToday: Boolean): Int {
        if (!date.isAfter(today)) return rotationIndex
        var d = if (doneToday) today.plusDays(1) else today
        var idx = rotationIndex
        while (d.isBefore(date)) {
            if (isPlanned(d)) idx++
            d = d.plusDays(1)
        }
        return idx
    }
}
