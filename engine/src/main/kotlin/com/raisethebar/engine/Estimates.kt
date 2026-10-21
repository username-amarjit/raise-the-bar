package com.raisethebar.engine

import com.raisethebar.config.Rules
import kotlin.math.max
import kotlin.math.min

/** What the app knows about one exercise. Weights are effective kilograms (see [Loads]). */
data class Estimate(
    val e1rm: Double,
    val currentWeight: Double?,
    val badDays: Int = 0,
    val sessions: Int = 0,
)

/** One logged set, as the estimate maths sees it. */
data class SetResult(
    val weight: Double,
    val reps: Int,
    val left: Int,
)

/** The weight and rep targets for the next session of one exercise. */
data class Prescription(
    val weight: Double,
    val set1Target: Int,
    val targetLeft: Int,
    val sets: Int,
    /** Heavier than last time's prescribed weight. */
    val wentUp: Boolean,
    /** Above the rep range, but the next weight up would undershoot it: stay and add reps. */
    val tooBigJump: Boolean,
    /** The heaviest available weight and still above the range: "Ready for a harder version". */
    val outgrown: Boolean,
) {
    /** setNTarget = set1Target − (n − 1) × REP_DROP_PER_SET, never below 1. */
    fun target(setNo: Int): Int = max(1, set1Target - (setNo - 1) * Rules.REP_DROP_PER_SET)
}

/** Result of the first-set check: how far off the prediction it was, and the weight for the remaining sets. */
data class FirstSetCheck(
    val gap: Int,
    val direction: Int,
    val newWeight: Double?,
)

/** The auto-enhancer core (milestone 6). Reference logic from the checklist, with the same names. */
object Estimates {
    /** e1rm(w, reps, left) = w × (1 + (reps + min(left, 4) + RIR_BIAS) / 30) */
    fun e1rm(w: Double, reps: Int, left: Int): Double = w * (1 + (reps + min(left, Rules.REPS_LEFT_MAX) + Rules.RIR_BIAS) / Rules.EPLEY_DIVISOR)

    /** predictedReps(E, w, left) = round(30 × (E / w − 1) − left) */
    fun predictedReps(e: Double, w: Double, left: Int): Int = roundHalfUp(Rules.EPLEY_DIVISOR * (e / w - 1) - left)

    fun nextWeight(est: Estimate, repLow: Int, repHigh: Int, left: Int, sets: Int, loads: Loads): Prescription? {
        if (loads.isEmpty) return null
        val e = est.e1rm
        var tooBigJump = false
        var w: Double
        val current = est.currentWeight?.let { loads.floorOrLightest(it) }
        if (current == null) {
            // First time: the heaviest weight predicting at least repLow + 1 reps, else the lightest.
            w = loads.values.lastOrNull { predictedReps(e, it, left) >= repLow + Rules.FIRST_TARGET_ABOVE_LOW } ?: loads.lightest!!
        } else {
            w = current
            if (predictedReps(e, w, left) > repHigh) {
                val up = loads.stepUp(w)
                if (up != null && predictedReps(e, up, left) >= repLow) {
                    w = up // earned it, one step only
                } else if (up != null) {
                    tooBigJump = true // the next weight up is too big a jump: stay and add reps
                }
            }
        }
        while (predictedReps(e, w, left) < repLow) {
            w = loads.stepDown(w) ?: break
        }
        val predicted = predictedReps(e, w, left)
        return Prescription(
            weight = w,
            set1Target = max(1, predicted),
            targetLeft = left,
            sets = sets,
            wentUp = est.currentWeight != null && w > est.currentWeight + Rules.WEIGHT_EPSILON_KG,
            tooBigJump = tooBigJump && predicted > repHigh,
            outgrown = loads.isHeaviest(w) && predicted > repHigh,
        )
    }

    /** gap = (reps + min(left, 4)) − predictedReps(E, weight, 0); ±2 or more moves the remaining sets one step. */
    fun afterFirstSet(e: Double, set: SetResult, loads: Loads): FirstSetCheck {
        val gap = (set.reps + min(set.left, Rules.REPS_LEFT_MAX)) - predictedReps(e, set.weight, 0)
        return when {
            gap <= -Rules.FIRST_SET_GAP -> FirstSetCheck(gap, -1, loads.stepDown(set.weight))
            gap >= Rules.FIRST_SET_GAP -> FirstSetCheck(gap, 1, loads.stepUp(set.weight))
            else -> FirstSetCheck(gap, 0, null)
        }
    }

    /**
     * After a session; skip it entirely for deload and injury-mode sets, and pass working sets only (no warm-ups).
     * currentWeight becomes the prescribed set-1 weight, not the in-session adjusted one.
     */
    fun afterSession(est: Estimate?, workingSets: List<SetResult>, prescribedWeight: Double?): Estimate? {
        if (workingSets.isEmpty()) return est
        val s = workingSets.maxOf { e1rm(it.weight, it.reps, it.left) }
        if (est == null) return Estimate(s, prescribedWeight ?: workingSets.first().weight, 0, 1)
        var e = est.e1rm
        var bad = est.badDays
        if (s >= e) {
            e = s // gains count in full
            bad = 0
        } else if (s >= Rules.BAD_DAY_RATIO * e) {
            e = (e + s) / 2
            bad = 0
        } else {
            bad += 1 // one bad day is noise
            if (bad >= Rules.BAD_DAYS_TO_COUNT) e = (e + s) / 2
        }
        return Estimate(e, prescribedWeight ?: est.currentWeight, bad, est.sessions + 1)
    }

    /** Reps for the sets after the first-set check moved the weight, from today's first-set estimate. */
    fun adjustedTarget(todayE1rm: Double, weight: Double, left: Int, setNo: Int): Int =
        max(1, predictedReps(todayE1rm, weight, left) - (setNo - 1) * Rules.REP_DROP_PER_SET)

    /** Deload: half the sets (rounded up), 90% of the normal weight rounded down, stop with 4 left. */
    fun deload(est: Estimate, normalWeight: Double, programmedSets: Int, loads: Loads): Prescription? {
        if (loads.isEmpty) return null
        val w = loads.floorOrLightest(normalWeight * Rules.DELOAD_WEIGHT)!!
        return Prescription(
            weight = w,
            set1Target = max(1, predictedReps(est.e1rm, w, Rules.DELOAD_LEFT)),
            targetLeft = Rules.DELOAD_LEFT,
            sets = Calendar.deloadSets(programmedSets),
            wentUp = false,
            tooBigJump = false,
            outgrown = false,
        )
    }
}

/** Find-your-weight ramp (milestone 6): sets of 8, used the first time any exercise or backup is done. */
object Ramp {
    sealed interface Step {
        /** Do another ramp set at [suggested]. [canConfirm] shows "That's my weight" (3 left). */
        data class Next(
            val suggested: Double,
            val canConfirm: Boolean,
        ) : Step

        /** The ramp is over: that set was working set 1, and the rest follow at [weight]. */
        data class Done(
            val weight: Double,
        ) : Step
    }

    /** A light start: the empty bar, the lightest stack plate, 5 kg dumbbells, or body weight. */
    fun start(loads: Loads): Double? {
        if (loads.isEmpty) return null
        return when (loads.kind) {
            LoadKind.DUMBBELL -> loads.floor(DUMBBELL_START_KG) ?: loads.lightest
            LoadKind.BODYWEIGHT -> loads.bodyKg?.let { bw -> loads.floor(bw) } ?: loads.lightest
            else -> loads.lightest
        }
    }

    /** What happens after ramp set number [setNo]. */
    fun after(setNo: Int, set: SetResult, loads: Loads): Step {
        if (set.left <= Rules.RAMP_STOP_LEFT || setNo >= Rules.RAMP_MAX_SETS) return Step.Done(set.weight)
        val up = loads.stepUp(set.weight) ?: return Step.Done(set.weight)
        val suggested =
            if (set.left >= Rules.REPS_LEFT_MAX) {
                val heavier = loads.floor(set.weight * Rules.RAMP_JUMP_4PLUS)
                if (heavier != null && heavier >= up) heavier else up
            } else {
                val e = Estimates.e1rm(set.weight, set.reps, set.left)
                val target = e / (1 + (Rules.RAMP_REPS + Rules.RAMP_SUGGEST_LEFT) / Rules.EPLEY_DIVISOR)
                val fits = loads.floor(target)
                if (fits != null && fits >= up) fits else up
            }
        return Step.Next(suggested, canConfirm = set.left == Rules.RAMP_CONFIRM_LEFT)
    }

    /** The starting estimate is the highest e1RM among the ramp sets. */
    fun startingEstimate(sets: List<SetResult>): Double? = sets.maxOfOrNull { Estimates.e1rm(it.weight, it.reps, it.left) }

    private const val DUMBBELL_START_KG = 5.0
}

/** Timed holds: target the bottom of the range; when every set reaches the top, add 5 s to both ends, up to 90 s. */
object Holds {
    fun range(secLow: Int, secHigh: Int, offset: Int): Pair<Int, Int> =
        min(secLow + offset, Rules.HOLD_MAX_S) to min(secHigh + offset, Rules.HOLD_MAX_S)

    fun target(secLow: Int, secHigh: Int, offset: Int): Int = range(secLow, secHigh, offset).first

    fun nextOffset(secLow: Int, secHigh: Int, offset: Int, heldSeconds: List<Int>): Int {
        val top = range(secLow, secHigh, offset).second
        if (heldSeconds.isEmpty() || heldSeconds.any { it < top }) return offset
        return min(offset + Rules.HOLD_STEP_S, max(0, Rules.HOLD_MAX_S - secHigh)).coerceAtLeast(offset)
    }
}
