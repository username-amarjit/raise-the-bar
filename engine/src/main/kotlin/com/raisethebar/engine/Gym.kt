package com.raisethebar.engine

import com.raisethebar.config.Rules
import kotlin.math.roundToInt

/**
 * The weights the gym can put on one exercise, as effective kilograms in ascending order.
 * For bodyweight exercises the effective load is body weight + added weight − assistance (decision 6).
 */
class Loads(
    val kind: String,
    val values: List<Double>,
    private val minStep: Double,
    val bodyKg: Double? = null,
) {
    val isEmpty: Boolean get() = values.isEmpty()
    val lightest: Double? get() = values.firstOrNull()
    val heaviest: Double? get() = values.lastOrNull()

    /** The next weight up: one plate step for barbells (2.5 or 5 kg), else the next pair or stack plate. Null at the top. */
    fun stepUp(w: Double): Double? = values.firstOrNull { it > w + EPS && it >= w + minStep - EPS }

    /** The next weight down, or null at the bottom. */
    fun stepDown(w: Double): Double? = values.lastOrNull { it < w - EPS && it <= w - minStep + EPS }

    /** The heaviest available weight at or below [w] (rounded down), or null if [w] is below the lightest. */
    fun floor(w: Double): Double? = values.lastOrNull { it <= w + EPS }

    /** [w] rounded down to an available weight, or the lightest if [w] is below everything. */
    fun floorOrLightest(w: Double): Double? = floor(w) ?: lightest

    fun isHeaviest(w: Double): Boolean = heaviest?.let { w >= it - EPS } ?: false

    fun contains(w: Double): Boolean = values.any { kotlin.math.abs(it - w) < EPS }

    /** What the user sets on the bar or machine: added kg for bodyweight exercises, assistance kg for assisted ones. */
    fun toDisplay(effective: Double): Double =
        when (kind) {
            LoadKind.BODYWEIGHT -> effective - (bodyKg ?: 0.0)
            LoadKind.ASSISTED -> (bodyKg ?: 0.0) - effective
            else -> effective
        }

    fun fromDisplay(display: Double): Double =
        when (kind) {
            LoadKind.BODYWEIGHT -> (bodyKg ?: 0.0) + display
            LoadKind.ASSISTED -> (bodyKg ?: 0.0) - display
            else -> display
        }

    companion object {
        private const val EPS = Rules.WEIGHT_EPSILON_KG
        val NONE = Loads(LoadKind.NONE, emptyList(), 0.0)
    }
}

/** Available weights per item (milestone 5). Anything in `missing_kg` is removed. */
object Gym {
    /** Weights are computed in quarter-kilogram units so plate sums never drift. */
    private const val UNITS_PER_KG = 4

    fun loadsFor(ex: Exercise, gym: Map<String, Equipment>, bodyKg: Double?): Loads {
        val items = ex.equipmentIds.mapNotNull { gym[it] }
        return when (ex.loadKind) {
            LoadKind.BARBELL -> {
                val bar = items.firstOrNull { it.barKg != null } ?: return Loads.NONE
                val plates = gym[PLATES]?.takeIf { it.status != EquipmentStatus.GONE }
                Loads(LoadKind.BARBELL, barbellWeights(bar, plates), ex.stepKg ?: Rules.STEP_BARBELL_UPPER_KG)
            }
            LoadKind.DUMBBELL -> Loads(LoadKind.DUMBBELL, dumbbellWeights(gym[DUMBBELLS]), 0.0)
            LoadKind.STACK -> Loads(LoadKind.STACK, stackWeights(items.firstOrNull { it.hasStack }), 0.0)
            LoadKind.BODYWEIGHT -> {
                if (bodyKg == null) return Loads.NONE
                val added = listOf(0.0) + dumbbellWeights(gym[DUMBBELLS].takeIf { it?.status != EquipmentStatus.GONE })
                Loads(LoadKind.BODYWEIGHT, added.map { bodyKg + it }.distinctSorted(), 0.0, bodyKg)
            }
            LoadKind.ASSISTED -> {
                if (bodyKg == null) return Loads.NONE
                val assistance = stackWeights(items.firstOrNull { it.hasStack })
                Loads(LoadKind.ASSISTED, assistance.map { bodyKg - it }.filter { it > 0 }.distinctSorted(), 0.0, bodyKg)
            }
            else -> Loads.NONE
        }
    }

    /** The bar plus any combination of plate pairs. */
    fun barbellWeights(bar: Equipment, plates: Equipment?): List<Double> {
        val barUnits = toUnits(bar.barKg ?: 0.0)
        val pairs = plates?.platePairsKg.orEmpty().filterNot { p -> plates?.missingKg.orEmpty().any { sameKg(it, p) } }
        var sums = setOf(0)
        for (p in pairs) {
            val u = toUnits(p)
            sums = sums + sums.map { it + u }
        }
        return sums.map { fromUnits(barUnits + 2 * it) }.filter { it >= Rules.Limits.EXERCISE_WEIGHT_KG.start }.distinctSorted()
    }

    fun dumbbellWeights(item: Equipment?): List<Double> {
        if (item == null) return emptyList()
        return item.dumbbellPairsKg.filterNot { p -> item.missingKg.any { sameKg(it, p) } }.distinctSorted()
    }

    /** From stack min to max by step. */
    fun stackWeights(item: Equipment?): List<Double> {
        if (item == null || !item.hasStack) return emptyList()
        val min = toUnits(item.stackMin!!)
        val max = toUnits(item.stackMax!!)
        val inc = toUnits(item.stackStep!!).coerceAtLeast(1)
        if (max < min) return emptyList()
        return (min..max step inc).map(::fromUnits).filterNot { w -> item.missingKg.any { sameKg(it, w) } }
    }

    private fun toUnits(kg: Double): Int = (kg * UNITS_PER_KG).roundToInt()

    private fun fromUnits(u: Int): Double = u.toDouble() / UNITS_PER_KG

    private fun sameKg(a: Double, b: Double) = kotlin.math.abs(a - b) < Rules.WEIGHT_EPSILON_KG

    private fun List<Double>.distinctSorted(): List<Double> = map { Math.round(it * UNITS_PER_KG) / UNITS_PER_KG.toDouble() }.distinct().sorted()

    const val PLATES = "plates"
    const val DUMBBELLS = "dumbbells"

    private val REPORT_DATE = java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale.ENGLISH)

    /** The "report it" message for the facilities team. */
    fun reportMessage(equipmentName: String, since: java.time.LocalDate): String =
        "Hi, the ${equipmentName.replaceFirstChar { it.lowercase() }} in the office gym has been out of order since " +
            "${since.format(REPORT_DATE)}. Could someone take a look? Thanks."

    /** Broken items: every 3 days the app asks "Is it fixed yet?". */
    fun shouldAskFixed(item: Equipment, lastAsked: java.time.LocalDate?, today: java.time.LocalDate): Boolean {
        if (item.status != EquipmentStatus.BROKEN) return false
        val since = item.brokenSince ?: item.statusDate ?: return false
        val every = Rules.BROKEN_ASK_EVERY_DAYS.toLong()
        val sinceDays = java.time.temporal.ChronoUnit.DAYS.between(since, today)
        val askedDays = lastAsked?.let { java.time.temporal.ChronoUnit.DAYS.between(it, today) }
        return sinceDays >= every && (askedDays == null || askedDays >= every)
    }

    /** "Machine busy?": doing the next exercise first is only offered when it uses different equipment. */
    fun canDoNextFirst(current: Exercise, next: Exercise?): Boolean = next != null && next.equipmentIds.none { it in current.equipmentIds }
}
