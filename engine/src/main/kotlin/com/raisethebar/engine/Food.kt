package com.raisethebar.engine

import com.raisethebar.config.Rules
import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlin.math.abs

@Serializable
data class Food(
    val id: String,
    val name: String,
    val shortName: String = name,
    val unit: String,
    val kcal: Double,
    val proteinG: Double,
    val dietType: String,
    val cooked: Boolean = false,
    val category: String = "",
)

@Serializable
data class MealItem(
    val foodId: String,
    val qty: Double,
)

/** What a usual meal stores in its `items` JSON: the portions it is made of (empty for totals) and a note. */
@Serializable
data class UsualMealItems(
    val items: List<MealItem> = emptyList(),
    val note: String = "",
)

@Serializable
data class UsualMealSeed(
    val name: String,
    val note: String = "",
    val items: List<MealItem> = emptyList(),
    val kcal: Double? = null,
    val proteinG: Double? = null,
)

@Serializable
data class FoodSeedFile(
    val foods: List<Food>,
    val usualMeals: List<UsualMealSeed>,
)

data class Totals(
    val kcal: Double,
    val proteinG: Double,
) {
    operator fun plus(o: Totals) = Totals(kcal + o.kcal, proteinG + o.proteinG)

    companion object {
        val ZERO = Totals(0.0, 0.0)
    }
}

object Diet {
    const val VEG = "veg"
    const val EGG = "egg"
    const val NON_VEG = "non_veg"

    /** `veg` items show for everyone, `egg` items for egg and non-veg, `non_veg` items for non-veg only. */
    fun allows(userDiet: String, itemDiet: String): Boolean =
        when (itemDiet) {
            VEG -> true
            EGG -> userDiet == EGG || userDiet == NON_VEG
            NON_VEG -> userDiet == NON_VEG
            else -> false
        }
}

object Goal {
    const val LEAN_BULK = "lean_bulk"
    const val RECOMP = "recomp"
}

/** Food and body rules from section 5. */
object Nutrition {
    /** 1.7 g per kg body weight, rounded to the nearest 5 g. */
    fun proteinTarget(bodyKg: Double): Int = roundToNearest(Rules.PROTEIN_G_PER_KG * bodyKg, Rules.PROTEIN_ROUND_TO_G)

    /** The 1.6–2.0 g/kg range shown next to the target. */
    fun proteinRange(bodyKg: Double): IntRange =
        roundHalfUp(Rules.PROTEIN_RANGE_LOW_G_PER_KG * bodyKg)..roundHalfUp(Rules.PROTEIN_RANGE_HIGH_G_PER_KG * bodyKg)

    /** 36 × kg for lean bulk, 31 × kg for recomp, rounded to the nearest 100. */
    fun kcalGuess(bodyKg: Double, goal: String): Int {
        val perKg = if (goal == Goal.LEAN_BULK) Rules.KCAL_PER_KG_LEAN_BULK else Rules.KCAL_PER_KG_RECOMP
        return roundToNearest(perKg * bodyKg, Rules.KCAL_ROUND_TO)
    }

    /** Waist less than half the height suggests lean bulk; otherwise recomp. The user can override. */
    fun suggestGoal(waistCm: Double, heightCm: Double): String =
        if (waistCm < heightCm * Rules.GOAL_WAIST_TO_HEIGHT_RATIO) Goal.LEAN_BULK else Goal.RECOMP

    /** Canteen oil: +45 kcal (1 tsp) per cooked portion when Canteen is on. */
    fun item(food: Food, qty: Double, canteen: Boolean): Totals {
        val oil = if (canteen && food.cooked) Rules.CANTEEN_OIL_KCAL * qty else 0.0
        return Totals(food.kcal * qty + oil, food.proteinG * qty)
    }

    fun meal(items: List<Pair<Food, Double>>, canteen: Boolean): Totals =
        items.fold(Totals.ZERO) { acc, (food, qty) -> acc + item(food, qty, canteen) }

    /** Mean of the morning weights in the 7 days ending [today]; null with fewer than 3 entries. */
    fun weightAverage(weights: Map<LocalDate, Double>, today: LocalDate): Double? =
        averageOver(weights, today.minusDays(Rules.WEIGHT_AVERAGE_DAYS - 1L), today)

    /** This 7-day average minus the previous 7 days' average, when both have enough entries. */
    fun weightChange(weights: Map<LocalDate, Double>, today: LocalDate): Double? {
        val now = weightAverage(weights, today) ?: return null
        val before = weightAverage(weights, today.minusDays(Rules.WEIGHT_AVERAGE_DAYS.toLong())) ?: return null
        return now - before
    }

    /** Targets are recalculated when body weight moves 2 kg or more from the weight they were set at. */
    fun shouldRecalculate(currentKg: Double, basisKg: Double?): Boolean =
        basisKg == null || abs(currentKg - basisKg) >= Rules.TARGETS_RECALC_WEIGHT_CHANGE_KG - Rules.WEIGHT_EPSILON_KG

    private fun averageOver(weights: Map<LocalDate, Double>, from: LocalDate, to: LocalDate): Double? {
        val inRange = weights.filterKeys { !it.isBefore(from) && !it.isAfter(to) }.values
        return if (inRange.size >= Rules.WEIGHT_AVERAGE_MIN_ENTRIES) inRange.average() else null
    }
}
