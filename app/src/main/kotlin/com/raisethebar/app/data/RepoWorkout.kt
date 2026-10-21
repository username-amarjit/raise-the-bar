package com.raisethebar.app.data

import com.raisethebar.app.db.DayLogEntity
import com.raisethebar.app.db.EstimateEntity
import com.raisethebar.app.db.PainLogEntity
import com.raisethebar.app.db.SessionEntity
import com.raisethebar.app.db.SetLogEntity
import com.raisethebar.engine.Calendar
import com.raisethebar.engine.DayStatus
import com.raisethebar.engine.Estimates
import com.raisethebar.engine.Exercise
import com.raisethebar.engine.Gym
import com.raisethebar.engine.Injuries
import com.raisethebar.engine.InjuryStatus
import com.raisethebar.engine.Loads
import com.raisethebar.engine.LoggedSet
import com.raisethebar.engine.Makeup
import com.raisethebar.engine.Outcome
import com.raisethebar.engine.PainLight
import com.raisethebar.engine.Phase
import com.raisethebar.engine.PlannedExercise
import com.raisethebar.engine.Planner
import com.raisethebar.engine.Ramp
import com.raisethebar.engine.SeedData
import com.raisethebar.engine.SessionKind
import com.raisethebar.engine.SessionPlan
import com.raisethebar.engine.SetResult
import com.raisethebar.engine.Time
import com.raisethebar.engine.Workout
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlin.math.max

@Serializable
data class AdjustedWeight(
    val weight: Double,
    val targets: Map<Int, Int> = emptyMap(),
)

/** Everything needed to resume an unfinished session exactly where it was. Stored as JSON in session.state. */
@Serializable
data class SessionState(
    val plan: SessionPlan,
    val order: List<String> = plan.all.map { it.key },
    val current: String? = order.firstOrNull(),
    val restEndsAt: String? = null,
    val adjusted: Map<String, AdjustedWeight> = emptyMap(),
    val messages: Map<String, String> = emptyMap(),
    val rampNext: Map<String, Double> = emptyMap(),
    val rampConfirm: List<String> = emptyList(),
    val rampDone: Map<String, Double> = emptyMap(),
    val stopped: List<String> = emptyList(),
    val skipToday: List<String> = emptyList(),
) {
    fun exercise(key: String): PlannedExercise? = plan.all.firstOrNull { it.key == key }

    val ordered: List<PlannedExercise> get() = order.mapNotNull(::exercise)

    fun rampOngoing(pe: PlannedExercise): Boolean = pe.ramp && pe.key !in rampDone && pe.key !in stopped
}

private fun encodeState(s: SessionState): String = SeedData.json.encodeToString(SessionState.serializer(), s)

fun decodeState(json: String?): SessionState? =
    json?.let {
        runCatching { SeedData.json.decodeFromString(SessionState.serializer(), it) }.getOrNull()
    }

/** The plan for today's session of the given kind: the full workout, the minimum session, or the home version. */
suspend fun Repo.planForToday(kind: String): SessionPlan? {
    val state = todayState()
    return when (kind) {
        SessionKind.MINIMUM -> {
            val program = program()
            val day = program.day(state.plan?.dayId ?: return null)
            buildPlan(day, SessionKind.MINIMUM, state.date, withMakeup = true)
        }
        SessionKind.HOME -> state.homeDay?.let { Planner.build(it, SessionKind.HOME, plannerInput(state.date)) }
        else -> state.plan
    }
}

/** Starts a session; it counts for the day it started. Starting cancels that day's check-in. */
suspend fun Repo.startSession(plan: SessionPlan): Long {
    val id =
        write {
            db.session().open()?.let { return@write it.id }
            val phase = Calendar.phase(plan.week)
            db.session().insert(
                SessionEntity(
                    localDate = time.today(),
                    programDayId = plan.dayId,
                    kind = plan.kind,
                    blockWeek = (phase as? Phase.Block)?.week,
                    startedAt = time.now(),
                    completedAt = null,
                    planWeek = plan.week,
                    rotationIndex = db.plan().get()?.rotationIndex,
                    makeupBlock =
                        if (plan.makeup.isEmpty()) {
                            null
                        } else {
                            SeedData.json.encodeToString(
                                ListSerializer(PlannedExercise.serializer()),
                                plan.makeup,
                            )
                        },
                    state = encodeState(SessionState(plan)),
                ),
            )
        }
    notifier.cancelCheckin(time.today())
    return id
}

data class SessionView(
    val session: SessionEntity,
    val state: SessionState,
    val sets: List<SetLogEntity>,
    val exercises: Map<String, Exercise>,
    val loads: Map<String, Loads>,
) {
    fun setsFor(key: String): List<SetLogEntity> = sets.filter { it.slotId == key && !it.isWarmup }

    fun warmupsFor(key: String): List<SetLogEntity> = sets.filter { it.slotId == key && it.isWarmup }

    /** Working sets done: of the ramp sets only the final one counts, once the ramp is over. */
    fun workingDone(pe: PlannedExercise): Int {
        val mine = setsFor(pe.key)
        if (!pe.ramp) return mine.size
        return if (pe.key in state.rampDone) 1 + mine.count { !it.isRamp } else 0
    }

    fun isComplete(pe: PlannedExercise): Boolean = pe.key in state.stopped || workingDone(pe) >= pe.sets

    val allComplete: Boolean get() = state.ordered.all(::isComplete)

    /** The weight the stepper starts at for the next set. */
    fun suggestedWeight(pe: PlannedExercise): Double? {
        val last = setsFor(pe.key).lastOrNull()?.weightKg
        return when {
            state.rampOngoing(pe) -> state.rampNext[pe.key] ?: last ?: pe.rampStart
            pe.ramp -> state.rampDone[pe.key]
            else -> state.adjusted[pe.key]?.weight ?: pe.weight ?: last
        }
    }

    fun nextSetNo(pe: PlannedExercise): Int = workingDone(pe) + 1

    /** Reps to aim for on the next set. */
    fun targetReps(pe: PlannedExercise): Int? {
        if (state.rampOngoing(pe)) return com.raisethebar.config.Rules.RAMP_REPS
        val n = nextSetNo(pe)
        state.adjusted[pe.key]?.targets?.get(n)?.let { return it }
        return pe.target(n)
    }
}

suspend fun Repo.sessionView(id: Long): SessionView? {
    val session = db.session().get(id) ?: return null
    val state = decodeState(session.state) ?: return null
    val lib = exercises()
    val gym = gym()
    val bw = bodyKg()
    val loads = state.plan.all.associate { pe -> pe.key to (lib[pe.exerciseId]?.let { Gym.loadsFor(it, gym, pe.bodyKg ?: bw) } ?: Loads.NONE) }
    return SessionView(session, state, db.setLog().forSession(id), lib, loads)
}

data class SetInput(
    val key: String,
    val weight: Double?,
    val reps: Int?,
    val left: Int?,
    val seconds: Int? = null,
    val pain: Int? = null,
    val sharp: Boolean = false,
    val warmup: Boolean = false,
)

/** Logs one set and runs the ramp, the first-set check, pain rules and the rest timer. Writes immediately. */
suspend fun Repo.logSet(sessionId: Long, input: SetInput) =
    write {
        val view = sessionView(sessionId) ?: return@write
        var st = view.state
        val pe = st.exercise(input.key) ?: return@write
        val loads = view.loads[pe.key] ?: Loads.NONE
        val now = time.now()
        val ramping = st.rampOngoing(pe) && !input.warmup
        val setNo = if (input.warmup) view.warmupsFor(pe.key).size + 1 else view.setsFor(pe.key).size + 1
        val entity =
            SetLogEntity(
                sessionId = sessionId,
                exerciseId = pe.exerciseId,
                slotId = pe.key,
                setNo = setNo,
                weightKg = input.weight,
                reps = input.reps,
                repsLeft = input.left,
                seconds = input.seconds,
                pain = input.pain,
                sharp = input.sharp,
                isWarmup = input.warmup,
                isRamp = ramping,
                isMakeup = pe.isMakeup,
                inInjuryMode = pe.injuryLoad,
                loggedAt = now,
                bwKg = pe.bodyKg,
                targetReps = view.targetReps(pe),
                targetLeft = pe.targetLeft,
            )
        Store.validateSet(entity)
        val setId = db.setLog().insert(entity)
        if (input.warmup) return@write

        val pain = input.pain
        if (pain != null) {
            val ex = view.exercises[pe.exerciseId]
            injuries().filter { it.isActive && ex != null && Injuries.usesArea(ex, it, rules) }.forEach { inj ->
                logPain(
                    PainLogEntity(
                        injuryId = inj.id,
                        localDate = time.today(),
                        kind = "set",
                        value = pain,
                        sharp = input.sharp,
                        exerciseId = pe.exerciseId,
                        setLogId = setId,
                    ),
                )
            }
            if (Injuries.painLight(pain, input.sharp) == PainLight.RED) st = st.copy(stopped = st.stopped + pe.key)
        }

        val w = input.weight
        val reps = input.reps
        val left = input.left
        if (w != null && reps != null && left != null) {
            val set = SetResult(w, reps, left)
            if (ramping) {
                val rampNo = view.setsFor(pe.key).count { it.isRamp } + 1
                st =
                    when (val step = Ramp.after(rampNo, set, loads)) {
                        is Ramp.Step.Next ->
                            st.copy(
                                rampNext = st.rampNext + (pe.key to step.suggested),
                                rampConfirm = if (step.canConfirm) (st.rampConfirm + pe.key).distinct() else st.rampConfirm - pe.key,
                            )
                        is Ramp.Step.Done -> rampFinished(st, pe, step.weight, view, set)
                    }
            } else if (setNo == 1 && !pe.ramp && !pe.injuryLoad && st.plan.kind != SessionKind.DELOAD && pe.sets > 1) {
                val e1rm = pe.e1rm ?: return@write finishLog(view, st, pe, now, sessionId)
                val check = Estimates.afterFirstSet(e1rm, set, loads)
                val newWeight = check.newWeight
                if (newWeight != null) {
                    val todayE = Estimates.e1rm(w, reps, left)
                    val remaining = (2..pe.sets).toList()
                    val targets = remaining.associateWith { n -> Estimates.adjustedTarget(todayE, newWeight, pe.targetLeft ?: 0, n) }
                    val msg = Workout.firstSetMessage(check.direction, weightText(pe, newWeight), remaining)
                    st =
                        st.copy(
                            adjusted = st.adjusted + (pe.key to AdjustedWeight(newWeight, targets)),
                            messages =
                                st.messages + (pe.key to (msg ?: "")),
                        )
                }
            }
        }
        finishLog(view, st, pe, now, sessionId)
    }

private suspend fun Repo.finishLog(view: SessionView, state: SessionState, pe: PlannedExercise, now: java.time.OffsetDateTime, sessionId: Long) {
    val restEnds = now.plusSeconds(pe.restS.toLong())
    var st = state.copy(restEndsAt = Time.timestamp(restEnds))
    st = advance(st, sessionId)
    db.session().update(view.session.copy(state = encodeState(st)))
    notifier.scheduleRestEnd(restEnds)
}

private fun rampFinished(st: SessionState, pe: PlannedExercise, weight: Double, view: SessionView, last: SetResult): SessionState {
    val rampSets =
        view.setsFor(pe.key).filter { it.isRamp }.mapNotNull { s -> s.weightKg?.let { w -> SetResult(w, s.reps ?: 0, s.repsLeft ?: 0) } } + last
    val e = Ramp.startingEstimate(rampSets) ?: Estimates.e1rm(last.weight, last.reps, last.left)
    val left = pe.targetLeft ?: 0
    val targets = (2..max(2, pe.sets)).associateWith { n -> Estimates.adjustedTarget(e, weight, left, n) }
    return st.copy(
        rampDone = st.rampDone + (pe.key to weight),
        rampNext = st.rampNext - pe.key,
        rampConfirm = st.rampConfirm - pe.key,
        adjusted = st.adjusted + (pe.key to AdjustedWeight(weight, targets)),
    )
}

/** "That's my weight": ends the ramp at the last ramp set (offered at 3 reps left). */
suspend fun Repo.confirmRamp(sessionId: Long, key: String) =
    write {
        val view = sessionView(sessionId) ?: return@write
        val pe = view.state.exercise(key) ?: return@write
        val last = view.setsFor(key).lastOrNull { it.isRamp } ?: return@write
        val w = last.weightKg ?: return@write
        var st = rampFinished(view.state, pe, w, view, SetResult(w, last.reps ?: 0, last.repsLeft ?: 0))
        st = advance(st, sessionId)
        db.session().update(view.session.copy(state = encodeState(st)))
    }

private suspend fun Repo.advance(st: SessionState, sessionId: Long): SessionState {
    val view = sessionView(sessionId)?.copy(state = st) ?: return st
    val current = st.current?.let(st::exercise)
    if (current != null && !view.isComplete(current)) return st
    val next = st.ordered.firstOrNull { !view.isComplete(it) }
    return st.copy(current = next?.key)
}

suspend fun Repo.selectExercise(sessionId: Long, key: String) =
    write {
        val s = db.session().get(sessionId) ?: return@write
        val st = decodeState(s.state) ?: return@write
        db.session().update(s.copy(state = encodeState(st.copy(current = key))))
    }

suspend fun Repo.clearRest(sessionId: Long) =
    write {
        val s = db.session().get(sessionId) ?: return@write
        val st = decodeState(s.state) ?: return@write
        db.session().update(s.copy(state = encodeState(st.copy(restEndsAt = null))))
        notifier.cancelRestEnd()
    }

/** "Machine busy?": use the next backup for today only. */
suspend fun Repo.swapForToday(sessionId: Long, key: String) =
    write {
        val s = db.session().get(sessionId) ?: return@write
        val st = decodeState(s.state) ?: return@write
        val pe = st.exercise(key) ?: return@write
        val program = program()
        val slot = pe.slotId?.let { program.slot(it) } ?: return@write
        val day = program.dayOrNull(st.plan.dayId) ?: return@write
        val skip = (st.skipToday + pe.exerciseId).distinct()
        val input = plannerInput(s.localDate, skip.toSet())
        val used = st.plan.all.filter { it.key != key }.map { it.exerciseId }.toSet()
        val first = st.plan.exercises.firstOrNull()?.key == key
        val replacement = Planner.planSlot(day, slot, st.plan.kind, input, used, first)?.copy(note = "Swapped for today") ?: return@write
        val plan = st.plan.copy(exercises = st.plan.exercises.map { if (it.key == key) replacement else it })
        val next = st.copy(plan = plan, skipToday = skip, adjusted = st.adjusted - key, messages = st.messages - key)
        db.session().update(s.copy(state = encodeState(next)))
    }

/** "Machine busy?": wait, and do the next exercise first (only offered when it uses different equipment). */
suspend fun Repo.doNextFirst(sessionId: Long, key: String) =
    write {
        val s = db.session().get(sessionId) ?: return@write
        val st = decodeState(s.state) ?: return@write
        val view = sessionView(sessionId) ?: return@write
        val i = st.order.indexOf(key)
        val nextKey = st.order.drop(i + 1).firstOrNull { k -> st.exercise(k)?.let { !view.isComplete(it) } == true } ?: return@write
        val order =
            st.order.toMutableList().apply {
                remove(nextKey)
                add(i, nextKey)
            }
        db.session().update(s.copy(state = encodeState(st.copy(order = order, current = nextKey))))
    }

fun weightText(pe: PlannedExercise, effective: Double): String {
    val shown = pe.displayWeight(effective) ?: effective
    val kg = formatKg(shown)
    return when (pe.loadKind) {
        com.raisethebar.engine.LoadKind.BODYWEIGHT -> if (shown <= 0.0) "body weight" else "+$kg kg"
        com.raisethebar.engine.LoadKind.ASSISTED -> "$kg kg assistance"
        else -> "$kg kg"
    }
}

/** Throws the session away (nothing is saved). */
suspend fun Repo.discardSession(sessionId: Long) =
    write {
        val s = db.session().get(sessionId) ?: return@write
        if (s.completedAt == null) db.session().update(s.copy(abandoned = true))
        notifier.cancelRestEnd()
    }

/**
 * Finish: mark the session complete, run afterSession for each exercise, progress holds and injury phases,
 * count the make-up block, and advance the rotation for full, minimum and home sessions.
 */
suspend fun Repo.finishSession(sessionId: Long) =
    write {
        val s = db.session().get(sessionId) ?: return@write
        if (s.completedAt != null) return@write
        val st = decodeState(s.state) ?: return@write
        val now = time.now()
        db.session().update(s.copy(completedAt = now, state = encodeState(st.copy(restEndsAt = null))))
        notifier.cancelRestEnd()
        val sets = db.setLog().forSession(sessionId)
        val logged =
            sets.map {
                LoggedSet(
                    it.exerciseId,
                    it.setNo,
                    it.weightKg,
                    it.reps,
                    it.repsLeft,
                    it.seconds,
                    it.isWarmup,
                    it.isRamp,
                    it.isMakeup,
                    it.inInjuryMode,
                )
            }
        val existing = db.estimate().all().associateBy { it.exerciseId }

        Workout.estimateUpdates(st.plan.kind, st.plan.all, logged, estimates()).forEach { (id, e) ->
            db.estimate().upsert(
                EstimateEntity(
                    id,
                    e.e1rm,
                    e.currentWeight,
                    e.badDays,
                    e.sessions,
                    calibrated = true,
                    updatedAt = now,
                    holdOffsetS =
                        existing[id]?.holdOffsetS ?: 0,
                ),
            )
        }
        val program = program()
        Workout.holdUpdates(st.plan.all, logged, holdOffsets()) { id -> id?.let { program.slot(it) } }.forEach { (id, offset) ->
            val row = db.estimate().get(id)
            db.estimate().upsert(
                row?.copy(holdOffsetS = offset, updatedAt = now)
                    ?: EstimateEntity(id, null, null, 0, 0, calibrated = false, updatedAt = now, holdOffsetS = offset),
            )
        }

        val lib = exercises()
        val setIds = sets.map { it.id }.toSet()
        db.injury().unresolved().forEach { row ->
            val inj = row.toModel()
            if (!inj.isActive || inj.locked) return@forEach
            val ratings = db.painLog().forInjuryOn(inj.id, s.localDate).filter { it.kind == "set" && it.setLogId in setIds }
            if (ratings.isEmpty()) return@forEach
            val light = Injuries.sessionLight(ratings.map { it.value to it.sharp })
            val rebuilt = st.plan.all.any { pe -> pe.injuryLoad && lib[pe.exerciseId]?.let { Injuries.clashes(it, inj, rules) } == true }
            if (light != PainLight.RED && !rebuilt) return@forEach
            val next = Injuries.afterSession(inj, light, s.localDate)
            db.injury().update(row.withState(next).copy(resolvedAt = if (next.status == InjuryStatus.RESOLVED) now else null))
            if (next.status == InjuryStatus.RESOLVED) restorePreInjury(row.preInjury)
        }

        if (st.plan.makeup.isNotEmpty() && st.plan.makeup.all { pe -> sets.count { it.slotId == pe.key && !it.isWarmup } >= pe.sets }) {
            saveMakeup(Makeup.afterBlockLogged(makeupState()))
        }
        if (SessionKind.advancesRotation(st.plan.kind)) {
            db.plan().get()?.let { db.plan().upsert(it.copy(rotationIndex = (it.rotationIndex + 1) % program.rotation.size)) }
        }
        val dayLog = db.dayLog().get(s.localDate)
        db.dayLog().upsert(
            DayLogEntity(
                localDate = s.localDate,
                planned = dayStatus(s.localDate) == DayStatus.Planned,
                reason = dayLog?.reason,
                reasonAt = dayLog?.reasonAt,
                outcome = Outcome.DONE,
                laterTime = dayLog?.laterTime,
            ),
        )
    }

/** When injury mode ends, normal estimates continue from the saved pre-injury values. */
private suspend fun Repo.restorePreInjury(json: String) {
    val now = time.now()
    Repo.decodePreInjury(json).forEach { (id, pre) ->
        val e1rm = pre.e1rm ?: return@forEach
        val row = db.estimate().get(id)
        if (row != null) {
            db.estimate().upsert(row.copy(e1rmKg = e1rm, currentWeightKg = pre.weight ?: row.currentWeightKg, badDays = 0, updatedAt = now))
        } else {
            db.estimate().upsert(EstimateEntity(id, e1rm, pre.weight, 0, 0, calibrated = true, updatedAt = now))
        }
    }
}
