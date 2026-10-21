package com.raisethebar.app.data

import com.raisethebar.app.db.AbsenceEntity
import com.raisethebar.config.Rules
import com.raisethebar.engine.Absence
import com.raisethebar.engine.AbsenceKind
import com.raisethebar.engine.Away
import com.raisethebar.engine.AwayEquipment
import com.raisethebar.engine.Days
import com.raisethebar.engine.InputRejected
import com.raisethebar.engine.PreviewLine
import java.time.LocalDate

/** The away form (milestone 10). Minutes only matter with bodyweight or dumbbells. */
data class AwayDraft(
    val start: LocalDate,
    val end: LocalDate,
    val equipmentThere: String,
    val minutes: Int = Rules.AWAY_MINUTES_DEFAULT,
) {
    fun toAbsence(id: Long = 0) =
        Absence(
            id = id,
            kind = AbsenceKind.AWAY,
            start = start,
            end = end,
            equipmentThere = equipmentThere,
            minutesPerDay = if (equipmentThere == AwayEquipment.BODYWEIGHT || equipmentThere == AwayEquipment.DUMBBELLS) minutes else null,
        )
}

data class AwayPreview(
    val lines: List<PreviewLine>,
    val preTripDate: LocalDate?,
    val countsSkips: Boolean,
)

private const val MAX_AWAY_DAYS = 60L

private suspend fun Repo.checkAway(d: AwayDraft) {
    val today = time.today()
    if (d.start.isBefore(today)) throw InputRejected("start", d.start, "today or later")
    if (d.end.isBefore(d.start)) throw InputRejected("end", d.end, "on or after ${d.start}")
    if (d.end.isAfter(d.start.plusDays(MAX_AWAY_DAYS))) throw InputRejected("end", d.end, "within $MAX_AWAY_DAYS days of the start")
    if (d.minutes !in
        Rules.AWAY_MINUTES_RANGE
    ) {
        throw InputRejected("minutes", d.minutes, "${Rules.AWAY_MINUTES_RANGE.first}–${Rules.AWAY_MINUTES_RANGE.last}")
    }
    val clash = absences().firstOrNull { it.kind == AbsenceKind.AWAY && it.isActive && !it.end.isBefore(d.start) && !it.start.isAfter(d.end) }
    if (clash != null) throw InputRejected("dates", "${d.start}–${d.end}", "clear of your trip from ${Dates.short(clash.start)}")
}

/** Each affected date and what happens on it, before saving. */
suspend fun Repo.previewAway(d: AwayDraft): AwayPreview {
    checkAway(d)
    val trip = d.toAbsence()
    val today = time.today()
    val schedule = schedule()
    val program = program()
    val withTrip = absences() + trip
    val isPlanned = { x: LocalDate -> Days.isPlanned(x, schedule, withTrip, program) }
    val doneDates = db.session().between(today, d.start).filter { it.completedAt != null }.map { it.localDate }.toSet()
    val preTrip =
        if (trip.pausesPlan) {
            Away.lastDayBefore(trip, today, isPlanned) { it in doneDates }?.takeIf { Away.preTripNeeded(d.start, trip.days, lastTrained(d.start)) }
        } else {
            null
        }
    val next = Days.nextPlanned(d.end.plusDays(1), isPlanned)
    val nextName =
        next?.let {
            val doneToday = today in doneDates
            var idx = Days.rotationIndexOn(it, today, db.plan().get()?.rotationIndex ?: 0, isPlanned, doneToday)
            val from = if (doneToday) today.plusDays(1) else today
            if (preTrip != null && !preTrip.isBefore(from) && preTrip.isBefore(it)) idx -= 1 // the pre-trip session doesn't advance the rotation
            program.rotationDay(idx).name
        }
    return AwayPreview(Away.preview(trip, program, preTrip, next, nextName), preTrip, countsSkips = !trip.pausesPlan)
}

suspend fun Repo.saveAway(d: AwayDraft): Long {
    val id =
        write {
            checkAway(d)
            val a = d.toAbsence()
            val row =
                AbsenceEntity(
                    kind = a.kind,
                    start = a.start,
                    end = a.end,
                    equipmentThere = a.equipmentThere,
                    minutesPerDay = a.minutesPerDay,
                    createdAt = time.now(),
                    cancelledAt = null,
                )
            Store.validateAbsence(row)
            db.absence().insert(row).also { rememberChange(it) }
        }
    rebuildCheckins()
    return id
}

/** Days off and trips that haven't finished yet, so they can be undone. */
suspend fun Repo.activeChanges(): List<Absence> {
    val today = time.today()
    return absences().filter { it.isActive && !it.end.isBefore(today) }.sortedBy { it.start }
}

/**
 * Undo: before a change starts it is cancelled, which restores the plan exactly; during a trip the remaining away
 * days are cancelled and planned training resumes tomorrow.
 */
suspend fun Repo.undoChange(absenceId: Long) {
    write {
        val row = db.absence().get(absenceId) ?: return@write
        val undone = Away.undo(row.toModel(), time.now(), time.today())
        db.absence().update(row.copy(end = undone.end, cancelledAt = undone.cancelledAt))
    }
    rebuildCheckins()
}
