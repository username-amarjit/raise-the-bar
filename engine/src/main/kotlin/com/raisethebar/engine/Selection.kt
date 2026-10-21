package com.raisethebar.engine

import java.time.LocalDate

/** Everything exercise selection needs to know about today. */
class GymContext(
    val today: LocalDate,
    val exercises: Map<String, Exercise>,
    val gym: Map<String, Equipment>,
    val injuries: List<InjuryState> = emptyList(),
    val rules: InjuryRules? = null,
    /** Exercises the user swapped away for today only ("Machine busy? Use the next backup"). */
    val skipToday: Set<String> = emptySet(),
) {
    val activeInjuries: List<InjuryState> get() = injuries.filter { it.isActive }
}

/** Which exercise fills a slot today, and why. */
data class Pick(
    val slotId: String,
    val exerciseId: String?,
    val rank: Int,
    val source: Source,
    val note: String?,
    val healthySide: String? = null,
) {
    enum class Source { FIRST_CHOICE, BACKUP, ONE_SIDED, ALTERNATIVE, HOME, NONE }

    val isSwap: Boolean get() = source != Source.FIRST_CHOICE
}

/** pickExercise (milestone 5, extended for injury mode in milestone 11). */
object Selection {
    /** The first piece of equipment that isn't usable today, or null when everything works. */
    fun blockingEquipment(ex: Exercise, ctx: GymContext): String? {
        for (id in ex.equipmentIds) {
            val item = ctx.gym[id] ?: return "$id missing"
            if (!item.usableOn(ctx.today)) return "${item.name} ${item.statusOn(ctx.today)}"
        }
        return null
    }

    fun equipmentOk(ex: Exercise, ctx: GymContext): Boolean = blockingEquipment(ex, ctx) == null

    /** The active injury that takes this exercise out today, if any. */
    fun blockingInjury(ex: Exercise, ctx: GymContext): InjuryState? {
        val rules = ctx.rules ?: return null
        return ctx.activeInjuries.firstOrNull { Injuries.removes(ex, it, rules) }
    }

    fun available(ex: Exercise, ctx: GymContext): Boolean = equipmentOk(ex, ctx) && blockingInjury(ex, ctx) == null

    /**
     * The first ranked option whose equipment all works and whose stress tags don't clash with an active injury.
     * A one-sided injury switches the healthy side to the one-arm or one-leg version where there is one. Then the
     * area's listed alternatives, then the day's home version.
     */
    fun pick(slot: Slot, dayType: String, ctx: GymContext, program: Program, used: Set<String> = emptySet()): Pick {
        var firstReason: String? = null
        val injuredBy = mutableListOf<InjuryState>()
        for ((rank, id) in slot.ranked.withIndex()) {
            val ex = ctx.exercises[id] ?: continue
            val reason =
                when (id) {
                    in used -> "Already in today's workout"
                    in ctx.skipToday -> "Swapped for today"
                    else -> blockingEquipment(ex, ctx)
                }
            if (reason != null) {
                if (firstReason == null) firstReason = reason
                continue
            }
            val injury = blockingInjury(ex, ctx)
            if (injury != null) {
                injuredBy += injury
                val label = ctx.rules?.let { Injuries.areaName(injury, it) } ?: injury.area
                val oneSided = ex.oneSidedVersionId?.let { ctx.exercises[it] }
                val side = injury.healthySide
                if (side != null && !injury.locked && oneSided != null && oneSided.id !in used && equipmentOk(oneSided, ctx)) {
                    return Pick(slot.id, oneSided.id, rank, Pick.Source.ONE_SIDED, "$label: $side side only", side)
                }
                if (firstReason == null) firstReason = label
                continue
            }
            return if (rank == 0) {
                Pick(slot.id, id, 0, Pick.Source.FIRST_CHOICE, null)
            } else {
                Pick(slot.id, id, rank, Pick.Source.BACKUP, "$firstReason: backup $rank")
            }
        }

        val slotGroups = ctx.exercises[slot.firstChoice]?.countsToward.orEmpty()
        val rules = ctx.rules
        if (injuredBy.isNotEmpty() && rules != null) {
            val alternatives =
                injuredBy
                    .flatMap { rules.area(it.area).alternatives }
                    .distinct()
                    .mapNotNull { ctx.exercises[it] }
                    .filter { it.id !in used && available(it, ctx) }
            val alt =
                if (slotGroups.isEmpty()) {
                    alternatives.firstOrNull()
                } else {
                    alternatives.firstOrNull { a -> a.countsToward.any { it in slotGroups } }
                }
            if (alt != null) return Pick(slot.id, alt.id, -1, Pick.Source.ALTERNATIVE, "${Injuries.areaName(injuredBy.first(), rules)}: swapped")
        }

        val home =
            program
                .homeDay(dayType)
                ?.slots
                ?.mapNotNull { ctx.exercises[it.firstChoice] }
                .orEmpty()
                .filter { it.id !in used && blockingInjury(it, ctx) == null }
        val h = home.firstOrNull { e -> e.countsToward.any { it in slotGroups } } ?: home.firstOrNull()
        if (h != null) return Pick(slot.id, h.id, -1, Pick.Source.HOME, "${firstReason ?: "Nothing available"}: home version")
        return Pick(slot.id, null, -1, Pick.Source.NONE, firstReason ?: "Nothing available today")
    }
}
