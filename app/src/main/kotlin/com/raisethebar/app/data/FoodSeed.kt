package com.raisethebar.app.data

import com.raisethebar.app.db.AppDatabase
import com.raisethebar.app.db.FoodEntity
import com.raisethebar.app.db.MetaEntity
import com.raisethebar.app.db.UsualMealEntity
import com.raisethebar.engine.Nutrition
import com.raisethebar.engine.SeedData
import com.raisethebar.engine.Totals
import com.raisethebar.engine.UsualMealItems

private const val KEY_MEALS_SEEDED = "usual_meals_seeded"

/** Quick portions from section 10, plus the three example usual meals (seeded once; the user can delete them). */
internal suspend fun seedFood(db: AppDatabase) {
    val file = SeedData.foods
    db.food().upsertAll(
        file.foods.mapIndexed { i, f ->
            FoodEntity(
                id = f.id,
                name = f.name,
                unit = f.unit,
                kcal = f.kcal,
                proteinG = f.proteinG,
                dietType = f.dietType,
                cooked = f.cooked,
                category = f.category,
                shortName = f.shortName,
                position = i,
            )
        },
    )
    if (db.meta().get(KEY_MEALS_SEEDED) != null) return
    val byId = file.foods.associateBy { it.id }
    file.usualMeals.forEach { m ->
        val totals =
            if (m.items.isEmpty()) {
                Totals(m.kcal ?: 0.0, m.proteinG ?: 0.0)
            } else {
                Nutrition.meal(m.items.map { byId.getValue(it.foodId) to it.qty }, canteen = false)
            }
        db.usualMeal().insert(
            UsualMealEntity(
                name = m.name,
                items = SeedData.json.encodeToString(UsualMealItems.serializer(), UsualMealItems(m.items, m.note)),
                kcal = totals.kcal,
                proteinG = totals.proteinG,
                seeded = true,
            ),
        )
    }
    db.meta().put(MetaEntity(KEY_MEALS_SEEDED, "1"))
}
