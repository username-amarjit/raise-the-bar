@file:UseSerializers(LocalDateSerializer::class)

package com.raisethebar.engine

import com.raisethebar.config.Rules
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

object SessionKind {
    const val FULL = "full"
    const val MINIMUM = "minimum"
    const val HOME = "home"
    const val AWAY = "away"
    const val PRE_TRIP = "pre_trip"
    const val DELOAD = "deload"

    /** The rotation advances only when a full, minimum or home session is completed (a deload session is a full one). */
    fun advancesRotation(kind: String): Boolean = kind == FULL || kind == MINIMUM || kind == HOME || kind == DELOAD

    /** Every completed session counts as done for the day it started. */
    val DONE_KINDS = setOf(FULL, MINIMUM, HOME, AWAY, PRE_TRIP, DELOAD)
}

@Serializable
data class Warmup(
    val weight: Double,
    val reps: Int,
)

/** One exercise in a planned session. Weights are effective kilograms; see [displayWeight]. */
@Serializable
data class PlannedExercise(
    val key: String,
    val slotId: String? = null,
    val exerciseId: String,
    val pickSource: String = Pick.Source.FIRST_CHOICE.name,
    val rank: Int = 0,
    val note: String? = null,
    val healthySide: String? = null,
    val sets: Int,
    val repLow: Int? = null,
    val repHigh: Int? = null,
    val secLow: Int? = null,
    val secHigh: Int? = null,
    val targetLeft: Int? = null,
    val restS: Int,
    val loadKind: String = LoadKind.NONE,
    val bodyKg: Double? = null,
    val weight: Double? = null,
    val set1Target: Int? = null,
    val wentUp: Boolean = false,
    val tooBigJump: Boolean = false,
    val outgrown: Boolean = false,
    val ramp: Boolean = false,
    val rampStart: Double? = null,
    val warmups: List<Warmup> = emptyList(),
    val isMakeup: Boolean = false,
    val makeupGroup: String? = null,
    val injuryLoad: Boolean = false,
    val injuryNote: String? = null,
    val painRequired: Boolean = false,
    val caution: String? = null,
    val e1rm: Double? = null,
) {
    val isTimed: Boolean get() = secLow != null

    fun target(setNo: Int): Int? = set1Target?.let { max(1, it - (setNo - 1) * Rules.REP_DROP_PER_SET) }

    /** What the user sets: plain kg, added kg for bodyweight exercises, or assistance kg on assisted machines. */
    fun displayWeight(effective: Double? = weight): Double? {
        val w = effective ?: return null
        val bw = bodyKg ?: return w
        return when (loadKind) {
            LoadKind.BODYWEIGHT -> w - bw
            LoadKind.ASSISTED -> bw - w
            else -> w
        }
    }

    fun toEffective(display: Double): Double {
        val bw = bodyKg ?: return display
        return when (loadKind) {
            LoadKind.BODYWEIGHT -> bw + display
            LoadKind.ASSISTED -> bw - display
            else -> display
        }
    }
}

@Serializable
data class SessionPlan(
    val date: LocalDate,
    val dayId: String,
    val dayName: String,
    val focus: String,
    val dayType: String,
    val kind: String,
    val week: Int,
    val phaseLabel: String,
    val minutes: Int? = null,
    val exercises: List<PlannedExercise>,
    val makeup: List<PlannedExercise> = emptyList(),
) {
    val all: List<PlannedExercise> get() = exercises + makeup

    /** Muscle groups today's workout trains, so make-up blocks avoid them. */
    fun groups(exercises: Map<String, Exercise>): Set<String> = this.exercises.flatMap { exercises[it.exerciseId]?.countsToward.orEmpty() }.toSet()
}

/** A make-up exercise for one muscle group. */
@Serializable
data class MakeupItem(
    val group: String,
    val exerciseId: String,
    val sets: Int,
)

class PlannerInput(
    val date: LocalDate,
    val planStart: LocalDate,
    val program: Program,
    val ctx: GymContext,
    val estimates: Map<String, Estimate>,
    val holdOffsets: Map<String, Int> = emptyMap(),
    val bodyKg: Double?,
    val preInjury: Map<Long, Map<String, PreInjury>> = emptyMap(),
)

/** Builds the workout for a date (milestone 3), filled with weights from the auto-enhancer (milestone 6). */
object Planner {
    fun build(day: ProgramDay, requestedKind: String, input: PlannerInput, makeup: List<MakeupItem> = emptyList()): SessionPlan {
        val week = Calendar.planWeek(input.planStart, input.date)
        val phase = Calendar.phase(week)
        val kind = if (requestedKind == SessionKind.FULL && phase is Phase.Deload) SessionKind.DELOAD else requestedKind
        val gymDay = day.kind == DayKind.ROTATION || day.kind == DayKind.PRE_TRIP
        val slots = if (kind == SessionKind.MINIMUM) day.slots.take(Rules.MINIMUM_SESSION_EXERCISES) else day.slots
        val used = mutableSetOf<String>()
        val planned = mutableListOf<PlannedExercise>()
        for (slot in slots) {
            if (gymDay) {
                val pe = planSlot(day, slot, kind, input, used, first = planned.isEmpty()) ?: continue
                used += pe.exerciseId
                planned += pe
            } else {
                val ex = input.ctx.exercises[slot.firstChoice] ?: continue
                planned += planTemplate(ex, slot, phase, input)
            }
        }
        val block =
            makeup.mapIndexedNotNull { i, item ->
                val ex = input.ctx.exercises[item.exerciseId] ?: return@mapIndexedNotNull null
                val (lo, hi) = input.program.repRangeFor(ex.id) ?: (Rules.MAKEUP_DEFAULT_REP_LOW to Rules.MAKEUP_DEFAULT_REP_HIGH)
                val slot =
                    Slot(
                        id = "makeup_$i",
                        position = 100 + i,
                        sets = item.sets,
                        repLow = lo,
                        repHigh = hi,
                        restS = Rules.MAKEUP_REST_S,
                        options = listOf(ex.id),
                    )
                val pick = Pick(slot.id, ex.id, 0, Pick.Source.FIRST_CHOICE, null)
                planGym(ex, slot, pick, item.sets, phase, input, first = false)
                    .copy(slotId = null, isMakeup = true, makeupGroup = item.group, sets = item.sets)
            }
        return SessionPlan(
            date = input.date,
            dayId = day.id,
            dayName = day.name,
            focus = day.focus,
            dayType = day.dayType,
            kind = kind,
            week = week,
            phaseLabel = phase.label,
            minutes = day.minutes,
            exercises = planned,
            makeup = block,
        )
    }

    fun defaultRest(ex: Exercise): Int = if (ex.isBig) Rules.HOME_AWAY_REST_BIG_S else Rules.HOME_AWAY_REST_SMALL_S

    /** Picks and plans one gym slot; used by [build] and when the user swaps an exercise for today only. */
    fun planSlot(day: ProgramDay, slot: Slot, kind: String, input: PlannerInput, used: Set<String>, first: Boolean): PlannedExercise? {
        val phase = Calendar.phase(Calendar.planWeek(input.planStart, input.date))
        val pick = Selection.pick(slot, day.dayType, input.ctx, input.program, used)
        val ex = pick.exerciseId?.let { input.ctx.exercises[it] } ?: return null
        val sets = if (kind == SessionKind.MINIMUM) Rules.MINIMUM_SESSION_SETS else Calendar.workingSets(phase, slot.sets)
        return planGym(ex, slot, pick, sets, phase, input, first)
    }

    private fun planGym(ex: Exercise, slot: Slot, pick: Pick, sets: Int, phase: Phase, input: PlannerInput, first: Boolean): PlannedExercise {
        val rules = input.ctx.rules
        val active = input.ctx.activeInjuries
        val painRequired = rules != null && active.any { Injuries.usesArea(ex, it, rules) }
        val loadInjury = rules?.let { r -> active.firstOrNull { !it.locked && it.phase >= 2 && Injuries.clashes(ex, it, r) } }
        val caution =
            rules?.let { r ->
                input.ctx.injuries
                    .firstOrNull { it.status == InjuryStatus.WATCH && Injuries.usesArea(ex, it, r) }
                    ?.let { "Careful with your ${Injuries.areaName(it, r).lowercase()}: stop if it hurts" }
            }
        val notes = listOfNotNull(pick.note, slot.note).joinToString(". ").ifEmpty { null }
        val base =
            PlannedExercise(
                key = slot.id,
                slotId = slot.id,
                exerciseId = ex.id,
                pickSource = pick.source.name,
                rank = pick.rank,
                note = notes,
                healthySide = pick.healthySide,
                sets = sets,
                restS = slot.restS ?: defaultRest(ex),
                loadKind = ex.loadKind,
                bodyKg = input.bodyKg,
                painRequired = painRequired,
                caution = caution,
            )
        if (ex.timed) {
            val low = slot.secLow ?: DEFAULT_HOLD_LOW
            val high = slot.secHigh ?: DEFAULT_HOLD_HIGH
            val (l, h) = Holds.range(low, high, input.holdOffsets[ex.id] ?: 0)
            return base.copy(secLow = l, secHigh = h, loadKind = LoadKind.NONE)
        }
        val repLow = slot.repLow ?: Rules.MAKEUP_DEFAULT_REP_LOW
        val repHigh = slot.repHigh ?: Rules.MAKEUP_DEFAULT_REP_HIGH
        val left = slot.stopLeft ?: Calendar.targetLeft(phase, ex.isBig)
        val withRange = base.copy(repLow = repLow, repHigh = repHigh, targetLeft = left)
        val loads = Gym.loadsFor(ex, input.ctx.gym, input.bodyKg)
        val est = input.estimates[ex.id]

        if (loadInjury != null && !loads.isEmpty) {
            val fraction = Injuries.loadFraction(loadInjury) ?: 1.0
            val ref = input.preInjury[loadInjury.id]?.get(ex.id)?.weight ?: est?.currentWeight
            val w = if (ref != null) loads.floorOrLightest(ref * fraction) else loads.lightest
            val (lo, hi) = Injuries.repRange(loadInjury, repLow, repHigh)
            val pct = (fraction * 100).roundToInt()
            val how = if (loadInjury.phase == 2) ", pain-free range only, slow lowering" else ""
            return withRange.copy(
                repLow = lo,
                repHigh = hi,
                targetLeft = null,
                weight = w,
                injuryLoad = true,
                painRequired = true,
                injuryNote = "${Injuries.areaName(loadInjury, rules)}, phase ${loadInjury.phase}: $pct% of your pre-injury weight$how",
            )
        }
        if (!ex.isLoaded || loads.isEmpty) return withRange.copy(loadKind = if (ex.isLoaded) ex.loadKind else LoadKind.NONE)
        if (est == null) return withRange.copy(ramp = true, rampStart = Ramp.start(loads))

        val p =
            (
                if (phase is Phase.Deload) {
                    val normal = est.currentWeight ?: Estimates.nextWeight(est, repLow, repHigh, left, sets, loads)?.weight
                    normal?.let { Estimates.deload(est, it, slot.sets, loads) }
                } else {
                    Estimates.nextWeight(est, repLow, repHigh, left, sets, loads)
                }
            ) ?: return withRange
        val warmups =
            if (first && ex.loadKind in WARMUP_KINDS) {
                Rules.WARMUP.mapNotNull { step ->
                    loads.floorOrLightest(p.weight * step.fraction)?.takeIf { it < p.weight - Rules.WEIGHT_EPSILON_KG }?.let { Warmup(it, step.reps) }
                }
            } else {
                emptyList()
            }
        return withRange.copy(
            weight = p.weight,
            set1Target = p.set1Target,
            targetLeft = p.targetLeft,
            wentUp = p.wentUp,
            tooBigJump = p.tooBigJump,
            outgrown = p.outgrown,
            warmups = warmups,
            e1rm = est.e1rm,
        )
    }

    /** Home and away templates: reps and effort only, no weights. */
    private fun planTemplate(ex: Exercise, slot: Slot, phase: Phase, input: PlannerInput): PlannedExercise {
        val rules = input.ctx.rules
        val painRequired = rules != null && input.ctx.activeInjuries.any { Injuries.usesArea(ex, it, rules) }
        val base =
            PlannedExercise(
                key = slot.id,
                slotId = slot.id,
                exerciseId = ex.id,
                note = slot.note,
                sets = slot.sets,
                restS = slot.restS ?: defaultRest(ex),
                loadKind = LoadKind.NONE,
                painRequired = painRequired,
            )
        return if (ex.timed || slot.isTimed) {
            base.copy(secLow = slot.secLow ?: DEFAULT_HOLD_LOW, secHigh = slot.secHigh ?: slot.secLow ?: DEFAULT_HOLD_HIGH)
        } else {
            base.copy(repLow = slot.repLow, repHigh = slot.repHigh, targetLeft = slot.stopLeft ?: Calendar.targetLeft(phase, ex.isBig))
        }
    }

    private val WARMUP_KINDS = setOf(LoadKind.BARBELL, LoadKind.DUMBBELL, LoadKind.STACK)
    private const val DEFAULT_HOLD_LOW = 30
    private const val DEFAULT_HOLD_HIGH = 45
}
