package com.raisethebar.app.data

import com.raisethebar.app.db.FoodEntity
import com.raisethebar.app.db.FoodLogEntity
import com.raisethebar.app.db.ProfileEntity
import com.raisethebar.app.db.UsualMealEntity
import com.raisethebar.engine.Diet
import com.raisethebar.engine.Food
import com.raisethebar.engine.InputRejected
import com.raisethebar.engine.MealItem
import com.raisethebar.engine.Nutrition
import com.raisethebar.engine.SeedData
import com.raisethebar.engine.Totals
import com.raisethebar.engine.UsualMealItems
import java.time.LocalDate

object Meals {
    const val BREAKFAST = "breakfast"
    const val LUNCH = "lunch"
    const val SNACK = "snack"
    const val DINNER = "dinner"

    val ALL = listOf(BREAKFAST, LUNCH, SNACK, DINNER)

    fun label(meal: String) = meal.replaceFirstChar(Char::uppercase)

    /** The meal slot that fits the time of day. */
    fun forHour(hour: Int): String =
        when {
            hour < LUNCH_FROM -> BREAKFAST
            hour < SNACK_FROM -> LUNCH
            hour < DINNER_FROM -> SNACK
            else -> DINNER
        }

    private const val LUNCH_FROM = 11
    private const val SNACK_FROM = 16
    private const val DINNER_FROM = 19
}

/** Everything the Food screen shows for one day. */
data class FoodDay(
    val date: LocalDate,
    val profile: ProfileEntity,
    val foods: List<FoodEntity>,
    val usual: List<UsualMealEntity>,
    val logs: List<FoodLogEntity>,
    val totals: Totals,
    val weightToday: Double?,
    val average: Double?,
    val change: Double?,
    val weighInsThisWeek: Int,
)

internal const val MAX_QTY = 20.0
internal const val MAX_MEAL_KCAL = 5000.0
internal const val MAX_MEAL_PROTEIN_G = 300.0

fun FoodEntity.toModel() = Food(id, name, shortName ?: name, unit, kcal, proteinG, dietType, cooked, category)

suspend fun Repo.foodDay(): FoodDay? {
    val p = profile() ?: return null
    val today = time.today()
    val logs = db.foodLog().forDate(today)
    val weights = db.bodyLog().between(today.minusDays(13), today).associate { it.localDate to it.weightKg }
    return FoodDay(
        date = today,
        profile = p,
        foods = db.food().all().filter { Diet.allows(p.dietType, it.dietType) },
        usual = db.usualMeal().all(),
        logs = logs,
        totals = logs.fold(Totals.ZERO) { acc, l -> acc + Totals(l.kcal, l.proteinG) },
        weightToday = weights[today],
        average = Nutrition.weightAverage(weights, today),
        change = Nutrition.weightChange(weights, today),
        weighInsThisWeek = weights.keys.count { !it.isBefore(today.minusDays(6)) },
    )
}

/** A quick-add tap: one more portion of [foodId] in [meal]; Canteen adds the oil for cooked items. */
suspend fun Repo.addFood(meal: String, foodId: String, canteen: Boolean) =
    write {
        val today = time.today()
        val food = db.food().get(foodId)?.toModel() ?: return@write
        val existing = db.foodLog().forDate(today).firstOrNull { it.meal == meal && it.foodId == foodId && it.canteen == canteen }
        val qty = (existing?.qty ?: 0.0) + 1
        val t = Nutrition.item(food, qty, canteen)
        if (existing != null) {
            db.foodLog().update(existing.copy(qty = qty, kcal = t.kcal, proteinG = t.proteinG))
        } else {
            db.foodLog().insert(
                FoodLogEntity(
                    localDate = today,
                    meal = meal,
                    foodId = foodId,
                    usualMealId = null,
                    qty = qty,
                    kcal = t.kcal,
                    proteinG = t.proteinG,
                    canteen = canteen,
                    source = "tap",
                    name = food.shortName,
                    loggedAt = time.now(),
                ),
            )
        }
    }

/** Quantity steppers; zero or less removes the item. Usual meals scale their totals. */
suspend fun Repo.setFoodQty(logId: Long, qty: Double) =
    write {
        val row = db.foodLog().all().firstOrNull { it.id == logId } ?: return@write
        if (qty <= 0.0) {
            db.foodLog().delete(row)
            return@write
        }
        if (qty > MAX_QTY) throw InputRejected("quantity", qty, "up to ${MAX_QTY.toInt()}")
        val t =
            row.foodId?.let { id -> db.food().get(id)?.toModel()?.let { Nutrition.item(it, qty, row.canteen) } }
                ?: Totals(row.kcal / row.qty * qty, row.proteinG / row.qty * qty)
        db.foodLog().update(row.copy(qty = qty, kcal = t.kcal, proteinG = t.proteinG))
    }

private suspend fun Repo.mealTotals(meal: UsualMealEntity, canteen: Boolean): Totals {
    val items = decodeItems(meal.items).items
    if (items.isEmpty()) return Totals(meal.kcal, meal.proteinG)
    val foods = db.food().all().associate { it.id to it.toModel() }
    return Nutrition.meal(items.mapNotNull { i -> foods[i.foodId]?.let { it to i.qty } }, canteen)
}

/** Logging a usual meal is a single tap. */
suspend fun Repo.logUsualMeal(meal: String, mealId: Long, canteen: Boolean) =
    write {
        val m = db.usualMeal().get(mealId) ?: return@write
        val t = mealTotals(m, canteen)
        db.foodLog().insert(
            FoodLogEntity(
                localDate = time.today(),
                meal = meal,
                foodId = null,
                usualMealId = m.id,
                qty = 1.0,
                kcal = t.kcal,
                proteinG = t.proteinG,
                canteen = canteen,
                source = "usual",
                name = m.name,
                loggedAt = time.now(),
            ),
        )
    }

/** Saves today's items in [meal] as a usual meal. */
suspend fun Repo.saveMealAsUsual(name: String, meal: String): Long? =
    write {
        val logs = db.foodLog().forDate(time.today()).filter { it.meal == meal && it.foodId != null }
        if (logs.isEmpty() || name.isBlank()) return@write null
        val items = logs.map { MealItem(it.foodId!!, it.qty) }
        val note = logs.joinToString(", ") { "${formatKg(it.qty)} × ${it.name ?: it.foodId}" }
        db.usualMeal().insert(
            UsualMealEntity(
                name = name.trim(),
                items = SeedData.json.encodeToString(UsualMealItems.serializer(), UsualMealItems(items, note)),
                kcal = logs.sumOf { it.kcal },
                proteinG = logs.sumOf { it.proteinG },
            ),
        )
    }

/** A usual meal entered as totals after weighing it once. */
suspend fun Repo.addUsualMealTotals(name: String, kcal: Double, proteinG: Double): Long =
    write {
        if (name.isBlank()) throw InputRejected("name", name, "a name")
        if (kcal !in 0.0..MAX_MEAL_KCAL) throw InputRejected("kcal", kcal, "0–${MAX_MEAL_KCAL.toInt()}")
        if (proteinG !in 0.0..MAX_MEAL_PROTEIN_G) throw InputRejected("protein_g", proteinG, "0–${MAX_MEAL_PROTEIN_G.toInt()} g")
        db.usualMeal().insert(
            UsualMealEntity(
                name = name.trim(),
                items = SeedData.json.encodeToString(UsualMealItems.serializer(), UsualMealItems()),
                kcal = kcal,
                proteinG = proteinG,
            ),
        )
    }

suspend fun Repo.deleteUsualMeal(id: Long) =
    write {
        db.usualMeal().get(id)?.let { db.usualMeal().delete(it) }
    }

/**
 * Morning weight. Protein and calorie targets are recalculated when body weight moves 2 kg or more from the weight
 * they were set at (the 7-day average when there is one). Returns the new targets when they changed.
 */
suspend fun Repo.logMorningWeight(kg: Double): Pair<Int, Int>? {
    logBodyWeight(time.today(), kg)
    return write {
        val p = db.profile().get() ?: return@write null
        val today = time.today()
        val weights = db.bodyLog().between(today.minusDays(6), today).associate { it.localDate to it.weightKg }
        val current = Nutrition.weightAverage(weights, today) ?: kg
        if (!Nutrition.shouldRecalculate(current, p.targetsBasisKg ?: p.weightKg)) return@write null
        val targets = Nutrition.proteinTarget(current) to Nutrition.kcalGuess(current, p.goal)
        val next = p.copy(proteinTargetG = targets.first, kcalTarget = targets.second, targetsBasisKg = current)
        Store.validateProfile(next)
        db.profile().upsert(next)
        targets
    }
}

fun decodeItems(json: String): UsualMealItems =
    runCatching {
        SeedData.json.decodeFromString(UsualMealItems.serializer(), json)
    }.getOrDefault(UsualMealItems())
