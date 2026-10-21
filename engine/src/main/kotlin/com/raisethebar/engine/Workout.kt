package com.raisethebar.engine

/** A logged set as the session maths sees it. Weights are effective kilograms. */
data class LoggedSet(
    val exerciseId: String,
    val setNo: Int,
    val weight: Double?,
    val reps: Int?,
    val left: Int?,
    val seconds: Int? = null,
    val isWarmup: Boolean = false,
    val isRamp: Boolean = false,
    val isMakeup: Boolean = false,
    val inInjuryMode: Boolean = false,
)

/** What finishing a session does to estimates and holds (milestone 7). */
object Workout {
    /**
     * Runs afterSession for each loaded exercise. Deload sessions and injury-mode sets never change estimates.
     * A ramp sets the starting estimate (the best e1RM of its sets) and its final weight as the current weight.
     */
    fun estimateUpdates(kind: String, planned: List<PlannedExercise>, sets: List<LoggedSet>, current: Map<String, Estimate>): Map<String, Estimate> {
        if (kind == SessionKind.DELOAD) return emptyMap()
        val out = mutableMapOf<String, Estimate>()
        for (pe in planned) {
            if (pe.injuryLoad || pe.exerciseId in out) continue
            val mine =
                sets.filter {
                    it.exerciseId == pe.exerciseId && !it.isWarmup && !it.inInjuryMode && it.weight != null && it.reps != null && it.left != null
                }
            if (mine.isEmpty()) continue
            val results = mine.map { SetResult(it.weight!!, it.reps!!, it.left!!) }
            val prescribed = if (pe.ramp) (mine.lastOrNull { it.isRamp } ?: mine.first()).weight else pe.weight
            val updated = Estimates.afterSession(current[pe.exerciseId], results, prescribed) ?: continue
            out[pe.exerciseId] = updated
        }
        return out
    }

    /** Timed holds move up 5 s once every set reached the top of the range. */
    fun holdUpdates(planned: List<PlannedExercise>, sets: List<LoggedSet>, offsets: Map<String, Int>, slots: (String?) -> Slot?): Map<String, Int> {
        val out = mutableMapOf<String, Int>()
        planned.filter { it.isTimed }.forEach { pe ->
            val slot = slots(pe.slotId) ?: return@forEach
            val low = slot.secLow ?: return@forEach
            val high = slot.secHigh ?: return@forEach
            val held = sets.filter { it.exerciseId == pe.exerciseId && !it.isWarmup }.mapNotNull { it.seconds }
            if (held.size < pe.sets) return@forEach
            val now = offsets[pe.exerciseId] ?: 0
            val next = Holds.nextOffset(low, high, now, held)
            if (next != now) out[pe.exerciseId] = next
        }
        return out
    }

    /** "Harder than predicted, so sets 2 and 3 drop to 50 kg." */
    fun firstSetMessage(direction: Int, newWeightText: String?, remainingSets: List<Int>): String? {
        if (direction == 0 || newWeightText == null || remainingSets.isEmpty()) return null
        val which =
            when (remainingSets.size) {
                1 -> "set ${remainingSets[0]}"
                2 -> "sets ${remainingSets[0]} and ${remainingSets[1]}"
                else -> "sets ${remainingSets.first()} to ${remainingSets.last()}"
            }
        val one = remainingSets.size == 1
        return if (direction < 0) {
            "Harder than predicted, so $which ${if (one) "drops" else "drop"} to $newWeightText."
        } else {
            "Easier than predicted, so $which ${if (one) "goes" else "go"} up to $newWeightText."
        }
    }
}
