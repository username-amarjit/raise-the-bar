package com.raisethebar.engine

import com.raisethebar.config.Rules
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** Where a date falls in the plan: calibration, a block week, or a deload week. */
sealed interface Phase {
    val label: String

    data class Calibration(
        val week: Int,
    ) : Phase {
        override val label get() = "Calibration, week $week"
    }

    data class Block(
        val block: Int,
        val week: Int,
    ) : Phase {
        override val label get() = "Block $block, week $week"
    }

    data class Deload(
        val afterBlock: Int,
    ) : Phase {
        override val label get() = "Deload week"
    }
}

/** The block calendar and effort ramp from section 5. The calendar keeps running through days off and trips. */
object Calendar {
    /** week = floor((today − plan_start) / 7) + 1; dates before the start count as week 1. */
    fun planWeek(planStart: LocalDate, date: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(planStart, date)
        return maxOf(1, Math.floorDiv(days, Rules.DAYS_PER_WEEK.toLong()).toInt() + 1)
    }

    fun phase(week: Int): Phase {
        if (week <= Rules.CALIBRATION_WEEKS) return Phase.Calibration(maxOf(1, week))
        val n = week - Rules.CALIBRATION_WEEKS - 1
        val k = n % Rules.CYCLE_WEEKS
        val block = n / Rules.CYCLE_WEEKS + 1
        return if (k == Rules.BLOCK_WEEKS) Phase.Deload(block) else Phase.Block(block, k + 1)
    }

    fun phaseOn(planStart: LocalDate, date: LocalDate): Phase = phase(planWeek(planStart, date))

    /**
     * Reps to stop with. Calibration uses 3 for exercises that already have an estimate; exercises without one
     * run the find-your-weight ramp instead (see [Ramp]).
     */
    fun targetLeft(phase: Phase, big: Boolean): Int =
        when (phase) {
            is Phase.Calibration -> if (big) Rules.CALIBRATION_2_LEFT.big else Rules.CALIBRATION_2_LEFT.small
            is Phase.Block -> Rules.BLOCK_LEFT.getValue(phase.week).let { if (big) it.big else it.small }
            is Phase.Deload -> if (big) Rules.DELOAD_EFFORT.big else Rules.DELOAD_EFFORT.small
        }

    /** Working sets: 2 in calibration (week 1 is the final ramp set + 1 more), half rounded up in a deload. */
    fun workingSets(phase: Phase, programmed: Int): Int =
        when (phase) {
            is Phase.Calibration -> minOf(programmed, Rules.CALIBRATION_2_SETS)
            is Phase.Block -> programmed
            is Phase.Deload -> deloadSets(programmed)
        }

    fun deloadSets(programmed: Int): Int = ceil(programmed / Rules.DELOAD_SETS_DIVISOR.toDouble()).toInt()

    val Phase.isDeload: Boolean get() = this is Phase.Deload
}
