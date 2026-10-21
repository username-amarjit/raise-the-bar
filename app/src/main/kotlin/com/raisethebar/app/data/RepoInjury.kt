package com.raisethebar.app.data

import com.raisethebar.app.db.InjuryEntity
import com.raisethebar.app.db.MetaEntity
import com.raisethebar.app.db.PainLogEntity
import com.raisethebar.engine.GymContext
import com.raisethebar.engine.Injuries
import com.raisethebar.engine.InjuryState
import com.raisethebar.engine.InjuryStatus
import com.raisethebar.engine.Planner
import com.raisethebar.engine.PreInjury
import com.raisethebar.engine.Selection
import com.raisethebar.engine.Side
import com.raisethebar.engine.Triage

/** What injury mode would change in the six rotation days. */
data class InjuryPreview(
    val name: String,
    val takenOut: List<String>,
    val swaps: Int,
    val keeps: String,
)

private fun Repo.probe(a: Triage.Answers) =
    InjuryState(
        id = -1,
        area = a.area,
        side = a.side,
        status = InjuryStatus.ACTIVE,
        phase = 1,
        started = time.today(),
        phaseStarted = time.today(),
        redFlag = a.redFlags.isNotEmpty(),
        muscle = a.muscle,
    )

suspend fun Repo.previewInjury(a: Triage.Answers): InjuryPreview {
    val program = program()
    val lib = exercises()
    val gym = gym()
    val today = time.today()
    val existing = injuries()
    val probe = probe(a)
    val without = GymContext(today, lib, gym, existing, rules)
    val with = GymContext(today, lib, gym, existing + probe, rules)
    var swaps = 0
    val out = linkedSetOf<String>()
    program.rotation.map(program::day).forEach { day ->
        val usedBefore = mutableSetOf<String>()
        val usedAfter = mutableSetOf<String>()
        day.slots.forEach { slot ->
            val before = Selection.pick(slot, day.dayType, without, program, usedBefore).exerciseId
            val after = Selection.pick(slot, day.dayType, with, program, usedAfter).exerciseId
            before?.let(usedBefore::add)
            after?.let(usedAfter::add)
            if (before != after) {
                swaps++
                before?.let { lib[it]?.name }?.let(out::add)
            }
        }
    }
    val keeps = if (a.area == Injuries.MUSCLE) "Everything else" else rules.area(a.area).keeps
    return InjuryPreview(Injuries.areaName(probe, rules), out.toList(), swaps, keeps)
}

/**
 * Starts injury mode in phase 1 and saves pre-injury estimates for every exercise that uses the area. A red flag
 * locks the area until "I've been checked". A matching watch injury from setup becomes this one.
 */
suspend fun Repo.startInjury(a: Triage.Answers): Long =
    write {
        val today = time.today()
        val probe = probe(a)
        val est = estimates()
        val pre =
            exercises().values.filter { Injuries.usesArea(it, probe, rules) }.mapNotNull { ex ->
                est[ex.id]?.let { e -> ex.id to PreInjury(e.e1rm, e.currentWeight) }
            }.toMap()
        val row =
            InjuryEntity(
                area = a.area,
                side = a.side,
                status = InjuryStatus.ACTIVE,
                phase = 1,
                started = today,
                redFlag = a.redFlags.isNotEmpty(),
                checkedByDoctor = false,
                preInjury = Repo.encodePreInjury(pre),
                greenStreak = 0,
                muscle = a.muscle,
                feel = a.feel,
                painNow = a.painNow,
                phaseStarted = today,
                usualMorningPain = a.painNow,
            )
        val watch =
            db.injury().unresolved().firstOrNull {
                it.status == InjuryStatus.WATCH && it.area == a.area && (it.side == a.side || it.side == Side.BOTH || a.side == Side.BOTH)
            }
        val id =
            if (watch != null) {
                db.injury().update(row.copy(id = watch.id))
                watch.id
            } else {
                db.injury().insert(row)
            }
        db.painLog().insert(PainLogEntity(injuryId = id, localDate = today, kind = "morning", value = a.painNow, sharp = a.feel == Triage.FEEL_SHARP))
        db.meta().put(MetaEntity(Meta.MORNING_CHECK_DATE, today.toString()))
        id
    }

/** "I've been checked": unlocks an area after a red flag. */
suspend fun Repo.markChecked(injuryId: Long) =
    write {
        val row = db.injury().get(injuryId) ?: return@write
        db.injury().update(row.copy(checkedByDoctor = true))
    }

/** The first-open-of-the-day rating: worse than the usual morning steps back one phase; green moves phase 1 on. */
suspend fun Repo.morningCheck(ratings: Map<Long, Pair<Int, Boolean>>) =
    write {
        val today = time.today()
        ratings.forEach { (id, rating) ->
            val (value, sharp) = rating
            val row = db.injury().get(id) ?: return@forEach
            logPain(PainLogEntity(injuryId = id, localDate = today, kind = "morning", value = value, sharp = sharp))
            db.injury().update(row.withState(Injuries.afterMorning(row.toModel(), value, sharp, today)))
        }
        db.meta().put(MetaEntity(Meta.MORNING_CHECK_DATE, today.toString()))
    }

/** After "Something hurts" mid-workout: exercises not started yet are picked again with the new injury in mind. */
suspend fun Repo.replanAfterInjury(sessionId: Long) =
    write {
        val s = db.session().get(sessionId) ?: return@write
        val st = decodeState(s.state) ?: return@write
        val program = program()
        val day = program.dayOrNull(st.plan.dayId) ?: return@write
        val logged = db.setLog().forSession(sessionId).map { it.slotId }.toSet()
        val input = plannerInput(s.localDate, st.skipToday.toSet())
        val used = st.plan.exercises.filter { it.key in logged }.map { it.exerciseId }.toMutableSet()
        val replanned =
            st.plan.exercises.mapIndexed { i, pe ->
                val slot = pe.slotId?.let { program.slot(it) }
                if (pe.key in logged || slot == null) {
                    pe
                } else {
                    (Planner.planSlot(day, slot, st.plan.kind, input, used, first = i == 0) ?: pe).also { used += it.exerciseId }
                }
            }
        db.session().update(s.copy(state = encodeState(st.copy(plan = st.plan.copy(exercises = replanned)))))
    }
