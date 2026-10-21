package com.raisethebar.engine

import com.raisethebar.config.Rules
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.min

@Serializable
data class AreaRule(
    val id: String,
    val label: String,
    val sided: Boolean = true,
    val removes: List<String> = emptyList(),
    val ifPainless: List<String> = emptyList(),
    val alternatives: List<String> = emptyList(),
    val takenOut: String = "",
    val instead: String = "",
    val keeps: String = "",
)

@Serializable
data class Flag(
    val id: String,
    val text: String,
)

@Serializable
data class InjuryRules(
    val note: String,
    val stopText: String,
    val emergencyText: String,
    val warmUpTip: String,
    val sickRule: String,
    val redFlags: List<Flag>,
    val emergency: Flag,
    val areas: List<AreaRule>,
) {
    fun area(id: String): AreaRule = areas.first { it.id == id }
}

object InjuryStatus {
    const val WATCH = "watch"
    const val ACTIVE = "active"
    const val RESOLVED = "resolved"
}

object Side {
    const val LEFT = "left"
    const val RIGHT = "right"
    const val BOTH = "both"
}

/** The engine's view of one injury row. */
data class InjuryState(
    val id: Long,
    val area: String,
    val side: String,
    val status: String,
    val phase: Int,
    val started: LocalDate,
    val phaseStarted: LocalDate? = null,
    val redFlag: Boolean = false,
    val checkedByDoctor: Boolean = false,
    val greenStreak: Int = 0,
    val loadFraction: Double? = null,
    val muscle: String? = null,
    val usualMorningPain: Int? = null,
) {
    val isActive: Boolean get() = status == InjuryStatus.ACTIVE

    /** A red flag locks the area's exercises until the user confirms "I've been checked". */
    val locked: Boolean get() = isActive && redFlag && !checkedByDoctor

    val healthySide: String?
        get() =
            when (side) {
                Side.LEFT -> Side.RIGHT
                Side.RIGHT -> Side.LEFT
                else -> null
            }
}

enum class PainLight { GREEN, AMBER, RED }

/** Saved when injury mode starts, for each affected exercise. */
@Serializable
data class PreInjury(
    val e1rm: Double? = null,
    val weight: Double? = null,
)

object Triage {
    const val FEEL_DULL = "dull"
    const val FEEL_SHARP = "sharp"
    const val ONSET_AFTER_TRAINING = "after_training"
    const val ONSET_DURING_SET = "during_set"
    const val ONSET_MORNING_AFTER = "morning_after"
    const val ONSET_OTHER = "other"

    data class Answers(
        val area: String,
        val side: String,
        val muscle: String? = null,
        val feel: String,
        val onset: String,
        val easesWithWarmUp: Boolean,
        val painNow: Int,
        val redFlags: Set<String> = emptySet(),
        val emergency: Boolean = false,
    )

    enum class Result { EMERGENCY, STOP, SORENESS, INJURY }

    /** Safety first: emergency, then red flags, then the soreness pattern; anything else starts injury mode. */
    fun classify(a: Answers): Result =
        when {
            a.emergency -> Result.EMERGENCY
            a.redFlags.isNotEmpty() -> Result.STOP
            isSoreness(a) -> Result.SORENESS
            else -> Result.INJURY
        }

    /** Dull, spread across the muscle, often both sides, 12–48 hours after training, eases as you warm up. */
    fun isSoreness(a: Answers): Boolean = a.feel == FEEL_DULL && a.easesWithWarmUp && (a.side == Side.BOTH || a.onset == ONSET_AFTER_TRAINING)
}

/** Injury mode rules (milestone 11, sections 5 and 9). */
object Injuries {
    const val MUSCLE = "muscle"

    fun painLight(value: Int, sharp: Boolean): PainLight =
        when {
            sharp || value > Rules.PAIN_AMBER_MAX -> PainLight.RED
            value > Rules.PAIN_GREEN_MAX -> PainLight.AMBER
            else -> PainLight.GREEN
        }

    /** Worst light across a session's ratings for the injured area; no ratings means green. */
    fun sessionLight(ratings: List<Pair<Int, Boolean>>): PainLight =
        ratings.map { (v, s) -> painLight(v, s) }.maxByOrNull { it.ordinal } ?: PainLight.GREEN

    /** Taken out by the area rules (or, for a strained muscle, the muscle is the main target). */
    fun clashes(ex: Exercise, i: InjuryState, rules: InjuryRules): Boolean =
        if (i.area == MUSCLE) {
            i.muscle != null && i.muscle in ex.countsToward
        } else {
            rules.area(i.area).removes.any { it in ex.stresses }
        }

    /** The exercise loads the injured area, so every set needs a pain rating. */
    fun usesArea(ex: Exercise, i: InjuryState, rules: InjuryRules): Boolean =
        clashes(ex, i, rules) ||
            (i.area != MUSCLE && i.area in ex.stresses) ||
            (i.area == MUSCLE && i.muscle != null && i.muscle in ex.countsToward)

    fun ifPainless(ex: Exercise, i: InjuryState, rules: InjuryRules): Boolean =
        i.area != MUSCLE && rules.area(i.area).ifPainless.any { it in ex.stresses }

    /** Swapped out today: clashing exercises in phase 1, and everything using a locked area. */
    fun removes(ex: Exercise, i: InjuryState, rules: InjuryRules): Boolean =
        when {
            !i.isActive -> false
            i.locked -> usesArea(ex, i, rules)
            i.phase == 1 -> clashes(ex, i, rules)
            else -> false
        }

    /** In phases 2–4 clashing exercises come back at a fraction of the pre-injury working weight. */
    fun loadFraction(i: InjuryState): Double? =
        when {
            !i.isActive || i.locked -> null
            i.phase == 2 -> i.loadFraction ?: Rules.PHASE_2_START_FRACTION
            i.phase == 3 -> i.loadFraction ?: Rules.PHASE_3_START_FRACTION
            i.phase == 4 -> 1.0
            else -> null
        }

    /** Phase 2 uses 12–20 reps; phases 3 and 4 use the slot's range. */
    fun repRange(i: InjuryState, slotLow: Int, slotHigh: Int): Pair<Int, Int> =
        if (i.phase == 2) Rules.PHASE_2_REP_LOW to Rules.PHASE_2_REP_HIGH else slotLow to slotHigh

    /** Enters [phase] at its starting load. */
    fun enter(i: InjuryState, phase: Int, today: LocalDate): InjuryState =
        i.copy(
            phase = phase.coerceIn(1, 4),
            phaseStarted = today,
            greenStreak = 0,
            loadFraction =
                when (phase) {
                    2 -> Rules.PHASE_2_START_FRACTION
                    3 -> Rules.PHASE_3_START_FRACTION
                    4 -> 1.0
                    else -> null
                },
        )

    /** After a session that used the injured area: green progresses, amber holds, red steps back one phase. */
    fun afterSession(i: InjuryState, light: PainLight, today: LocalDate): InjuryState {
        if (!i.isActive || i.locked) return i
        if (light == PainLight.RED) return if (i.phase > 1) enter(i, i.phase - 1, today) else i
        if (light == PainLight.AMBER) return i
        return when (i.phase) {
            2 -> {
                val streak = i.greenStreak + 1
                if (streak >= Rules.PHASE_2_GREEN_SESSIONS) {
                    enter(i, 3, today)
                } else {
                    val f = (i.loadFraction ?: Rules.PHASE_2_START_FRACTION) + Rules.PHASE_2_STEP_FRACTION
                    i.copy(greenStreak = streak, loadFraction = min(f, Rules.PHASE_2_MAX_FRACTION))
                }
            }
            3 -> {
                val f = (i.loadFraction ?: Rules.PHASE_3_START_FRACTION) + Rules.PHASE_3_STEP_FRACTION
                if (f >= 1.0 - Rules.WEIGHT_EPSILON_KG) enter(i, 4, today) else i.copy(loadFraction = f)
            }
            4 -> {
                val streak = i.greenStreak + 1
                if (streak >= Rules.PHASE_4_GREEN_SESSIONS) resolve(i) else i.copy(greenStreak = streak)
            }
            else -> i
        }
    }

    /**
     * The morning check: worse than the usual morning steps back one phase. Phase 1 moves on to phase 2
     * after at least a day once the morning check is green.
     */
    fun afterMorning(i: InjuryState, value: Int, sharp: Boolean, today: LocalDate): InjuryState {
        if (!i.isActive) return i
        val usual = i.usualMorningPain
        val worse = usual != null && (value > usual || sharp)
        val next = i.copy(usualMorningPain = if (usual == null) value else min(usual, value))
        if (worse) return if (next.phase > 1) enter(next, next.phase - 1, today) else next
        val since = ChronoUnit.DAYS.between(next.phaseStarted ?: next.started, today)
        if (next.phase == 1 && !next.locked && painLight(value, sharp) == PainLight.GREEN && since >= Rules.PHASE_1_MIN_DAYS) {
            return enter(next, 2, today)
        }
        return next
    }

    fun resolve(i: InjuryState): InjuryState = i.copy(status = InjuryStatus.RESOLVED, greenStreak = 0)

    /** "Hasn't improved in two weeks" is a red flag: prompt for a physio after 14 days still in phase 1 or 2. */
    fun needsPhysio(i: InjuryState, today: LocalDate): Boolean =
        i.isActive && i.phase <= 2 && ChronoUnit.DAYS.between(i.started, today) >= Rules.INJURY_NO_IMPROVEMENT_DAYS

    fun areaName(i: InjuryState, rules: InjuryRules): String {
        val base = if (i.area == MUSCLE) (i.muscle?.let(Groups::label) ?: "Muscle") else rules.area(i.area).label
        return if (i.side == Side.LEFT || i.side == Side.RIGHT) "${i.side.replaceFirstChar { it.uppercase() }} ${base.lowercase()}" else base
    }
}
