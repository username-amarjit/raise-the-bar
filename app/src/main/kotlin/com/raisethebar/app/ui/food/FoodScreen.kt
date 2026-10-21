package com.raisethebar.app.ui.food

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.raisethebar.app.data.FoodDay
import com.raisethebar.app.data.Meals
import com.raisethebar.app.data.addFood
import com.raisethebar.app.data.addUsualMealTotals
import com.raisethebar.app.data.deleteUsualMeal
import com.raisethebar.app.data.foodDay
import com.raisethebar.app.data.formatKg
import com.raisethebar.app.data.logMorningWeight
import com.raisethebar.app.data.logUsualMeal
import com.raisethebar.app.data.saveMealAsUsual
import com.raisethebar.app.data.setFoodQty
import com.raisethebar.app.ui.components.Body
import com.raisethebar.app.ui.components.Chip
import com.raisethebar.app.ui.components.ChipFlow
import com.raisethebar.app.ui.components.LinkButton
import com.raisethebar.app.ui.components.NumberField
import com.raisethebar.app.ui.components.QtyControl
import com.raisethebar.app.ui.components.RtbCard
import com.raisethebar.app.ui.components.ScreenFrame
import com.raisethebar.app.ui.components.SecondaryButton
import com.raisethebar.app.ui.components.SectionLabel
import com.raisethebar.app.ui.components.Segmented
import com.raisethebar.app.ui.components.SubHeading
import com.raisethebar.app.ui.components.SwitchRow
import com.raisethebar.app.ui.components.TextInput
import com.raisethebar.app.ui.rememberActions
import com.raisethebar.app.ui.rememberLoad
import com.raisethebar.app.ui.theme.Accent
import com.raisethebar.app.ui.theme.Rtb
import com.raisethebar.config.Rules
import com.raisethebar.engine.Nutrition
import com.raisethebar.engine.Validate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

private const val QTY_STEP = 0.5

/** Food (milestone 12): protein first, calories smaller, quick portions, usual meals and the morning weight. */
@Composable
fun FoodScreen() {
    val actions = rememberActions()
    val day = rememberLoad { foodDay() }
    val now = rememberLoad { LocalTime.now(time.clock).hour }
    var canteen by rememberSaveable { mutableStateOf(false) }
    var meal by rememberSaveable { mutableStateOf<String?>(null) }
    val slot = meal ?: now?.let(Meals::forHour) ?: Meals.LUNCH
    ScreenFrame(title = "Food", onBack = null, caption = "Today") {
        val d = day ?: return@ScreenFrame
        ProteinRing(d.totals.proteinG, d.profile.proteinTargetG)
        Text(
            "${d.totals.kcal.roundToInt()} of ${d.profile.kcalTarget} kcal",
            style = MaterialTheme.typography.bodyMedium,
            color = Rtb.colors.muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        SwitchRow("Canteen food", canteen, { canteen = it }, detail = "Adds ${Rules.CANTEEN_OIL_KCAL} kcal of oil per cooked item")
        SectionLabel("Adding to")
        Segmented(Meals.ALL.map(Meals::label), Meals.ALL.indexOf(slot), { meal = Meals.ALL[it] }, description = "Meal")
        SectionLabel("Add")
        ChipFlow {
            d.foods.forEach { f ->
                Chip(f.shortName ?: f.name, selected = false, onClick = { actions.run { addFood(slot, f.id, canteen) } })
            }
        }
        if (d.usual.isNotEmpty()) {
            SectionLabel("My usual meals")
            ChipFlow {
                d.usual.forEach { m ->
                    Chip("${m.name}, ${m.proteinG.roundToInt()} g", selected = false, accent = Accent.GOLD, onClick = {
                        actions.run { logUsualMeal(slot, m.id, canteen) }
                    })
                }
            }
        }
        MealsLogged(d)
        Weight(d)
        UsualMeals(d)
    }
}

/** Today's protein against the target, as a ring with the number in the middle. */
@Composable
private fun ProteinRing(protein: Double, target: Int) {
    val c = Rtb.colors
    val fraction = if (target > 0) (protein / target).toFloat().coerceIn(0f, 1f) else 0f
    val p = protein.roundToInt()
    Box(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$p of $target grams of protein today" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(160.dp)) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(c.surface2, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(c.legs, -90f, 360f * fraction, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$p g", style = MaterialTheme.typography.displaySmall, color = c.ink)
            Text("of $target g protein", style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    }
}

@Composable
private fun MealsLogged(d: FoodDay) {
    val actions = rememberActions()
    var saving by remember { mutableStateOf<String?>(null) }
    Meals.ALL.forEach { m ->
        val items = d.logs.filter { it.meal == m }
        if (items.isEmpty()) return@forEach
        SubHeading("${Meals.label(m)}, ${items.sumOf { it.proteinG }.roundToInt()} g")
        RtbCard {
            items.forEach { l ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.name ?: "Item", style = MaterialTheme.typography.titleSmall, color = Rtb.colors.ink)
                        Body(
                            "${l.proteinG.roundToInt()} g protein · ${l.kcal.roundToInt()} kcal" + (if (l.canteen) " · canteen" else ""),
                            muted = true,
                        )
                    }
                    QtyControl(
                        formatKg(l.qty),
                        onMinus = { actions.run { setFoodQty(l.id, l.qty - QTY_STEP) } },
                        onPlus = { actions.run { setFoodQty(l.id, l.qty + QTY_STEP) } },
                        description = l.name ?: "item",
                    )
                }
            }
            if (items.any { it.foodId != null }) LinkButton("Save this as a usual meal", onClick = { saving = m })
        }
    }
    val forMeal = saving
    if (forMeal != null) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saving = null },
            title = { Text("Save as a usual meal") },
            text = { TextInput("Name, for example Office thali", name, { name = it }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    saving = null
                    actions.run { saveMealAsUsual(name, forMeal) }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { saving = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Weight(d: FoodDay) {
    val actions = rememberActions()
    var text by remember(d.weightToday) { mutableStateOf(d.weightToday?.let(::formatKg).orEmpty()) }
    SubHeading("Morning weight")
    val avg = d.average
    val change = d.change
    Body(
        when {
            avg == null -> "${d.weighInsThisWeek} of ${Rules.WEIGHT_AVERAGE_MIN_ENTRIES} weigh-ins this week: the 7-day average shows from 3."
            change == null -> "7-day average ${formatKg(Math.round(avg * 10) / 10.0)} kg."
            else ->
                "7-day average ${formatKg(Math.round(avg * 10) / 10.0)} kg, ${if (change >= 0) "up" else "down"} " +
                    "${formatKg(Math.round(abs(change) * 10) / 10.0)} kg on the week before."
        },
    )
    Body("After the bathroom, before food or water. Judge the average, never a single day.", muted = true)
    val kg = text.toDoubleOrNull()
    NumberField(
        "Today's weight",
        text,
        { text = it },
        suffix = "kg",
        error =
            if (text.isNotEmpty() &&
                (kg == null || !Validate.isBodyWeightKg(kg))
            ) {
                "Enter 30–250 kg"
            } else {
                null
            },
    )
    SecondaryButton("Save weight", enabled = kg != null && Validate.isBodyWeightKg(kg), onClick = {
        actions.run {
            val changed = logMorningWeight(kg!!)
            if (changed != null) actions.message("Weight moved 2 kg: targets are now ${changed.first} g protein and ${changed.second} kcal.")
        }
    })
}

@Composable
private fun UsualMeals(d: FoodDay) {
    val actions = rememberActions()
    var adding by remember { mutableStateOf(false) }
    SubHeading("Usual meals")
    Body("Save a meal from what you logged, or enter its totals after weighing it once. Logging one is a single tap.", muted = true)
    d.usual.forEach { m ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.titleSmall, color = Rtb.colors.ink)
                Body("${m.proteinG.roundToInt()} g protein · ${m.kcal.roundToInt()} kcal", muted = true)
            }
            LinkButton("Delete", onClick = { actions.run { deleteUsualMeal(m.id) } })
        }
    }
    LinkButton("Add a usual meal by its totals", onClick = { adding = true })
    if (adding) {
        var name by remember { mutableStateOf("") }
        var kcal by remember { mutableStateOf("") }
        var protein by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("A usual meal, by totals") },
            text = {
                Column {
                    TextInput("Name", name, { name = it })
                    NumberField("Calories", kcal, { kcal = it }, suffix = "kcal")
                    NumberField("Protein", protein, { protein = it }, suffix = "g")
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && kcal.toDoubleOrNull() != null && protein.toDoubleOrNull() != null, onClick = {
                    adding = false
                    actions.run { addUsualMealTotals(name, kcal.toDouble(), protein.toDouble()) }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
    Body(
        "Protein range for you: ${Nutrition.proteinRange(d.profile.weightKg).let {
            "${it.first}–${it.last}"
        }} g a day (1.6–2.0 g per kg).",
        muted = true,
    )
}
