@file:UseSerializers(LocalDateSerializer::class)

package com.raisethebar.engine

import com.raisethebar.config.Rules
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

/** The user's make-up rules (setup "Your rules"), with the allowed ranges from milestone 4. */
@Serializable
data class MakeupSettings(
    val trigger: Int = Rules.MAKEUP_TRIGGER,
    val sessionsPerSkip: Int = Rules.MAKEUP_SESSIONS_PER_SKIP,
    val cap: Int = Rules.MAKEUP_MAX_SESSIONS,
    val blockMin: Int = Rules.MAKEUP_BLOCK_MIN,
) {
    fun clamped() =
        MakeupSettings(
            trigger = trigger.coerceIn(Rules.MAKEUP_TRIGGER_RANGE),
            sessionsPerSkip = sessionsPerSkip.coerceIn(Rules.MAKEUP_SESSIONS_PER_SKIP_RANGE),
            cap = cap.coerceIn(Rules.MAKEUP_MAX_SESSIONS_RANGE),
            blockMin = blockMin.coerceIn(Rules.MAKEUP_BLOCK_MIN_RANGE),
        )
}

data class MakeupState(
    val triggeredOn: LocalDate? = null,
    val skipsCounted: Int = 0,
    val sessionsLeft: Int = 0,
    val paused: Boolean = false,
) {
    val active: Boolean get() = sessionsLeft > 0
}

/** A logged set as weekly counting sees it. */
data class CountedSet(
    val sessionId: Long,
    val exerciseId: String,
    val isWarmup: Boolean,
    val isRamp: Boolean,
)

/** Make-up mode (milestone 9). */
object Makeup {
    /**
     * On each new unexcused skip: with [MakeupSettings.trigger] or more skips in the 7 days ending that day,
     * sessions_left = min(sessionsPerSkip × count, cap), which also restarts the clock. One skip changes only the count.
     * Paused (sick, or an injury in phase 1): the count is frozen. Comeback stub: if the last completed session is
     * 7 or more days old, make-up mode doesn't start.
     */
    fun onSkip(
        state: MakeupState,
        skipDate: LocalDate,
        skipDates: Collection<LocalDate>,
        lastCompleted: LocalDate?,
        planStart: LocalDate,
        settings: MakeupSettings,
        paused: Boolean,
    ): MakeupState {
        val count = Skips.skipsInWindow(skipDates, skipDate)
        if (paused) return state.copy(paused = true)
        val reference = lastCompleted ?: planStart.minusDays(1)
        if (ChronoUnit.DAYS.between(reference, skipDate) >= Rules.COMEBACK_STUB_GAP_DAYS) return state.copy(skipsCounted = count)
        if (count < settings.trigger) return state.copy(skipsCounted = count)
        return state.copy(triggeredOn = skipDate, skipsCounted = count, sessionsLeft = min(settings.sessionsPerSkip * count, settings.cap))
    }

    /** Make-up mode pauses on sick days and while any injury is in phase 1. */
    fun isPaused(sickToday: Boolean, injuries: List<InjuryState>): Boolean = sickToday || injuries.any { it.isActive && it.phase == 1 }

    /** sessions_left drops by 1 only when a block is logged. */
    fun afterBlockLogged(state: MakeupState): MakeupState = state.copy(sessionsLeft = max(0, state.sessionsLeft - 1))

    /**
     * Sets that count toward weekly targets: no warm-ups, and of the ramp sets only the final one per exercise per
     * session. Deload, make-up, home and away sets all count.
     */
    fun countable(sets: List<CountedSet>): List<CountedSet> {
        val lastRamp =
            sets.withIndex().filter { it.value.isRamp }.groupBy { it.value.sessionId to it.value.exerciseId }.mapValues { e ->
                e.value.last().index
            }
        return sets.withIndex().filter { (i, s) -> !s.isWarmup && (!s.isRamp || lastRamp[s.sessionId to s.exerciseId] == i) }.map { it.value }
    }

    /** Hard sets per muscle group: each counted set adds 1 to every group in its exercise's "Counts toward" list. */
    fun weeklySets(sets: List<CountedSet>, exercises: Map<String, Exercise>): Map<String, Int> {
        val out = Groups.ALL.associateWith { 0 }.toMutableMap()
        countable(sets).forEach { s -> exercises[s.exerciseId]?.countsToward?.forEach { g -> out[g] = (out[g] ?: 0) + 1 } }
        return out
    }

    /**
     * The 2 muscle groups furthest below their weekly targets, skipping groups trained today and groups already at
     * 20 hard sets; each uses its first available candidate. If only one group qualifies, 2 of its candidates.
     * Blocks never push a group past 20 sets.
     */
    fun buildBlock(
        todayGroups: Set<String>,
        setsLast7: Map<String, Int>,
        targets: Map<String, Int>,
        candidates: Map<String, List<String>>,
        isAvailable: (String) -> Boolean,
    ): List<MakeupItem> {
        val eligible =
            Groups.ALL
                .filter { g -> g !in todayGroups && (setsLast7[g] ?: 0) < Rules.WEEKLY_SET_CAP && candidates[g].orEmpty().any(isAvailable) }
                .sortedWith(compareByDescending<String> { (targets[it] ?: 0) - (setsLast7[it] ?: 0) }.thenBy { Groups.ALL.indexOf(it) })
        if (eligible.isEmpty()) return emptyList()
        if (eligible.size == 1) {
            val g = eligible.first()
            var room = Rules.WEEKLY_SET_CAP - (setsLast7[g] ?: 0)
            return candidates.getValue(g).filter(isAvailable).take(Rules.MAKEUP_BLOCK_EXERCISES).mapNotNull { id ->
                val sets = min(Rules.MAKEUP_BLOCK_SETS, room)
                room -= sets
                if (sets > 0) MakeupItem(g, id, sets) else null
            }
        }
        return eligible.take(Rules.MAKEUP_BLOCK_EXERCISES).map { g ->
            MakeupItem(g, candidates.getValue(g).first(isAvailable), min(Rules.MAKEUP_BLOCK_SETS, Rules.WEEKLY_SET_CAP - (setsLast7[g] ?: 0)))
        }
    }
}
