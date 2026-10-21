package com.raisethebar.app.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.raisethebar.app.data.OldInjury
import com.raisethebar.app.data.formatKg
import com.raisethebar.app.ui.components.AlertBox
import com.raisethebar.app.ui.components.AlertKind
import com.raisethebar.app.ui.components.Body
import com.raisethebar.app.ui.components.Chip
import com.raisethebar.app.ui.components.ChipFlow
import com.raisethebar.app.ui.components.IntStepperRow
import com.raisethebar.app.ui.components.LinkButton
import com.raisethebar.app.ui.components.MinTap
import com.raisethebar.app.ui.components.NumberField
import com.raisethebar.app.ui.components.RtbCard
import com.raisethebar.app.ui.components.SectionLabel
import com.raisethebar.app.ui.components.Segmented
import com.raisethebar.app.ui.components.SubHeading
import com.raisethebar.app.ui.components.TimeButton
import com.raisethebar.app.ui.theme.Rtb
import com.raisethebar.config.Rules
import com.raisethebar.engine.Equipment
import com.raisethebar.engine.EquipmentStatus
import com.raisethebar.engine.Goal
import com.raisethebar.engine.Groups
import com.raisethebar.engine.Injuries
import com.raisethebar.engine.Nutrition
import com.raisethebar.engine.SeedData
import com.raisethebar.engine.Side
import com.raisethebar.engine.Weekdays

private fun rangeError(text: String, ok: Boolean, allowed: String) = if (text.isNotEmpty() && !ok) "Enter $allowed" else null

@Composable
fun AboutYouForm(d: SetupDraft, showInjuries: Boolean = true) {
    val l = Rules.Limits
    NumberField(
        "Body weight",
        d.weight,
        {
            d.weight = it
        },
        suffix = "kg",
        error =
            rangeError(
                d.weight,
                d.weightKg != null,
                "${l.BODY_WEIGHT_KG.start.toInt()}–${l.BODY_WEIGHT_KG.endInclusive.toInt()} kg",
            ),
    )
    NumberField("Height", d.height, {
        d.height = it
    }, suffix = "cm", error = rangeError(d.height, d.heightCm != null, "${l.HEIGHT_CM.start.toInt()}–${l.HEIGHT_CM.endInclusive.toInt()} cm"))
    NumberField("Age", d.age, { d.age = it }, decimal = false, error = rangeError(d.age, d.ageYears != null, "${l.AGE.first}–${l.AGE.last}"))
    SectionLabel("What you eat")
    Segmented(
        SetupDraft.DIETS.map {
            it.second
        },
        SetupDraft.DIETS.indexOfFirst { it.first == d.diet },
        { d.diet = SetupDraft.DIETS[it].first },
        description = "Diet",
    )
    SectionLabel("How long you've lifted")
    ChipFlow {
        SetupDraft.EXPERIENCE.forEach { (id, label) -> Chip(label, selected = d.experience == id, onClick = { d.experience = id }) }
    }
    if (showInjuries) OldInjuries(d)
}

@Composable
private fun OldInjuries(d: SetupDraft) {
    SectionLabel("Old injuries")
    Body("Add anything that has bothered you before. If it hurts now, injury mode starts after setup.", muted = true)
    val areas = SeedData.injuryRules.areas
    d.injuries.forEachIndexed { i, inj ->
        RtbCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Where")
                ChipFlow { areas.forEach { a -> Chip(a.label, selected = inj.area == a.id, onClick = { d.injuries[i] = inj.copy(area = a.id) }) } }
                if (inj.area == Injuries.MUSCLE) {
                    SectionLabel("Which muscle")
                    ChipFlow {
                        Groups.ALL.forEach { g ->
                            Chip(Groups.label(g), selected = inj.muscle == g, onClick = {
                                d.injuries[i] =
                                    inj.copy(muscle = g)
                            })
                        }
                    }
                }
                if (areas.first { it.id == inj.area }.sided) {
                    SectionLabel("Side")
                    val sides = listOf(Side.LEFT, Side.RIGHT, Side.BOTH)
                    Segmented(listOf("Left", "Right", "Both"), sides.indexOf(inj.side), {
                        d.injuries[i] = inj.copy(side = sides[it])
                    }, description = "Side")
                }
                SectionLabel("Does it hurt now?")
                Segmented(listOf("Yes", "No"), if (inj.hurtsNow) 0 else 1, {
                    d.injuries[i] = inj.copy(hurtsNow = it == 0)
                }, description = "Hurts now")
                LinkButton("Remove", onClick = { d.injuries.removeAt(i) })
            }
        }
    }
    LinkButton("Add an old injury", onClick = { d.injuries.add(OldInjury("shoulder", Side.LEFT, null, hurtsNow = false)) })
}

@Composable
fun GoalForm(d: SetupDraft) {
    val l = Rules.Limits
    NumberField("Waist, at the belly button", d.waist, {
        d.waist = it
    }, suffix = "cm", error = rangeError(d.waist, d.waistCm != null, "${l.WAIST_CM.start.toInt()}–${l.WAIST_CM.endInclusive.toInt()} cm"))
    NumberField("Height", d.height, {
        d.height = it
    }, suffix = "cm", error = rangeError(d.height, d.heightCm != null, "${l.HEIGHT_CM.start.toInt()}–${l.HEIGHT_CM.endInclusive.toInt()} cm"))
    val h = d.heightCm
    d.suggestedGoal?.let { s ->
        val half = formatKg(h!! * Rules.GOAL_WAIST_TO_HEIGHT_RATIO)
        AlertBox(
            if (s == Goal.LEAN_BULK) {
                "Your waist is less than half your height ($half cm), so a lean bulk is suggested."
            } else {
                "Your waist is more than half your height ($half cm), so a recomp first is suggested."
            },
            kind = AlertKind.GOOD,
        )
    }
    SectionLabel("Your goal")
    val goals = listOf(Goal.LEAN_BULK, Goal.RECOMP)
    Segmented(listOf("Lean bulk", "Recomp"), goals.indexOf(d.goal), { d.goalChoice = goals[it] }, description = "Goal")
    Body(
        if (d.goal == Goal.LEAN_BULK) {
            "Eat a little above maintenance and aim to gain 0.5–1 kg a month."
        } else {
            "Eat around maintenance with high protein: weight stays about the same while your waist shrinks."
        },
        muted = true,
    )
    val w = d.weightKg
    SectionLabel("Protein a day")
    NumberField(
        "Protein target",
        if (d.proteinTouched) d.protein else d.suggestedProtein?.toString().orEmpty(),
        {
            d.proteinTouched = true
            d.protein = it
        },
        suffix = "g",
        decimal = false,
        error =
            rangeError(
                if (d.proteinTouched) d.protein else "x",
                (d.proteinG ?: 0) in SetupDraft.PROTEIN_RANGE,
                "${SetupDraft.PROTEIN_RANGE.first}–${SetupDraft.PROTEIN_RANGE.last} g",
            ),
    )
    if (w != null) {
        val r = Nutrition.proteinRange(w)
        Body("1.6–2.0 g per kg of body weight is ${r.first}–${r.last} g. Spread it over 4 meals.", muted = true)
    }
    SectionLabel("Calories, a first guess")
    NumberField(
        "Calories",
        if (d.kcalTouched) d.kcal else d.suggestedKcal?.toString().orEmpty(),
        {
            d.kcalTouched = true
            d.kcal = it
        },
        suffix = "kcal",
        decimal = false,
        error =
            rangeError(
                if (d.kcalTouched) d.kcal else "x",
                (d.kcalTarget ?: 0) in SetupDraft.KCAL_RANGE,
                "${SetupDraft.KCAL_RANGE.first}–${SetupDraft.KCAL_RANGE.last} kcal",
            ),
    )
    Body("Rough is fine: the weekly weight trend corrects it.", muted = true)
}

@Composable
fun WeekForm(d: SetupDraft, onNotificationsWanted: () -> Unit) {
    Body("Pick your training days and a time for each. The check-ins and the midnight timeout follow these.", muted = true)
    d.schedule.forEachIndexed { i, day ->
        Row(Modifier.fillMaxWidth().heightIn(min = MinTap), verticalAlignment = Alignment.CenterVertically) {
            Text(Weekdays.NAMES[day.weekday], style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Switch(
                checked = day.training,
                onCheckedChange = { d.schedule[i] = day.copy(training = it) },
                colors = SwitchDefaults.colors(checkedTrackColor = Rtb.colors.pull, checkedThumbColor = Rtb.colors.onAccent),
            )
            if (day.training) {
                TimeButton(day.time, {
                    d.schedule[i] = day.copy(time = it)
                }, label = "${Weekdays.NAMES[day.weekday]} training time", modifier = Modifier.width(110.dp))
            } else {
                Text("Rest", style = MaterialTheme.typography.titleSmall, color = Rtb.colors.muted, modifier = Modifier.width(110.dp))
            }
        }
    }
    val rest = d.schedule.filter { !it.training }.joinToString(", ") { Weekdays.NAMES[it.weekday] }
    Body(if (rest.isEmpty()) "No rest day: one a week is strongly suggested." else "Rest: $rest.", muted = true)
    SectionLabel("Check-ins")
    Row(
        Modifier.fillMaxWidth().heightIn(min = MinTap).toggleable(d.notifications, role = Role.Switch) { on ->
            d.notifications = on
            if (on) onNotificationsWanted()
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Check-in notifications", style = MaterialTheme.typography.titleSmall)
            Body("Two hours after your planned time, if no workout has started.", muted = true)
        }
        Switch(checked = d.notifications, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = Rtb.colors.pull))
    }
}

@Composable
fun GymForm(items: SnapshotStateList<Equipment>) {
    Body(
        "Everything is ticked as \"have it\": untick what your gym doesn't have, and mark anything broken. Tap Weights to set dumbbells, plates and stacks.",
        muted = true,
    )
    items.groupBy { it.section }.forEach { (section, group) ->
        SubHeading(section)
        group.forEach { item ->
            val i = items.indexOfFirst { it.id == item.id }
            EquipmentRow(item, onChange = { items[i] = it })
        }
    }
}

@Composable
private fun EquipmentRow(item: Equipment, onChange: (Equipment) -> Unit) {
    var open by remember(item.id) { mutableStateOf(false) }
    val have = item.status != EquipmentStatus.GONE
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = MinTap), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).heightIn(min = MinTap).toggleable(have, role = Role.Checkbox) { on ->
                    onChange(item.copy(status = if (on) EquipmentStatus.WORKING else EquipmentStatus.GONE))
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = have, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Rtb.colors.pull))
                Text(item.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
            if (have) {
                Chip(
                    "Broken",
                    selected = item.status == EquipmentStatus.BROKEN,
                    onClick = {
                        onChange(
                            item.copy(
                                status =
                                    if (item.status ==
                                        EquipmentStatus.BROKEN
                                    ) {
                                        EquipmentStatus.WORKING
                                    } else {
                                        EquipmentStatus.BROKEN
                                    },
                            ),
                        )
                    },
                )
            }
        }
        if (have && hasWeights(item)) {
            LinkButton(if (open) "Hide weights" else "Weights", onClick = { open = !open })
            if (open) WeightsEditor(item, onChange)
        }
        item.note?.let { Body(it, muted = true) }
    }
}

fun hasWeights(e: Equipment) = e.dumbbellPairsKg.isNotEmpty() || e.platePairsKg.isNotEmpty() || e.barKg != null || e.hasStack

private fun parseKgList(text: String): List<Double>? {
    val parts = text.split(',', ' ', ';').filter { it.isNotBlank() }
    val values = parts.map { it.toDoubleOrNull() ?: return null }
    return if (values.all { it in Rules.Limits.EXERCISE_WEIGHT_KG }) values else null
}

private fun kgList(values: List<Double>) = values.joinToString(", ") { formatKg(it) }

/** Edits an item's weights: dumbbell pairs, bar weight, plate pairs, or a stack's min, max and step, plus missing weights. */
@Composable
fun WeightsEditor(item: Equipment, onChange: (Equipment) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (item.dumbbellPairsKg.isNotEmpty()) {
            KgListField("Dumbbell pairs (kg)", item.dumbbellPairsKg) { onChange(item.copy(dumbbellPairsKg = it.sorted())) }
        }
        if (item.barKg != null) {
            KgField("Bar weight", item.barKg) { onChange(item.copy(barKg = it)) }
        }
        if (item.platePairsKg.isNotEmpty()) {
            KgListField("Plate pairs (kg), one entry per pair", item.platePairsKg) { onChange(item.copy(platePairsKg = it.sorted())) }
        }
        if (item.hasStack) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KgField("Min", item.stackMin, Modifier.weight(1f)) { onChange(item.copy(stackMin = it)) }
                KgField("Max", item.stackMax, Modifier.weight(1f)) { onChange(item.copy(stackMax = it)) }
                KgField("Step", item.stackStep, Modifier.weight(1f)) { onChange(item.copy(stackStep = it)) }
            }
        }
        KgListField("Missing weights (kg)", item.missingKg, allowEmpty = true) { onChange(item.copy(missingKg = it)) }
    }
}

@Composable
private fun KgListField(label: String, values: List<Double>, allowEmpty: Boolean = false, onValid: (List<Double>) -> Unit) {
    var text by remember(label) { mutableStateOf(kgList(values)) }
    val parsed = parseKgList(text)
    val ok = parsed != null && (allowEmpty || parsed.isNotEmpty())
    androidx.compose.material3.OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v.filter { it.isDigit() || it == '.' || it == ',' || it == ' ' }
            parseKgList(text)?.let { if (allowEmpty || it.isNotEmpty()) onValid(it) }
        },
        label = { Text(label) },
        isError = !ok,
        supportingText = if (!ok) ({ Text("Numbers from 0.5 to 500, separated by commas") }) else null,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun KgField(label: String, value: Double?, modifier: Modifier = Modifier, onValid: (Double) -> Unit) {
    var text by remember(label) { mutableStateOf(value?.let(::formatKg).orEmpty()) }
    val v = text.toDoubleOrNull()
    val ok = v != null && v in Rules.Limits.EXERCISE_WEIGHT_KG
    NumberField(
        label,
        text,
        {
            text = it
            it.toDoubleOrNull()?.takeIf { x -> x in Rules.Limits.EXERCISE_WEIGHT_KG }?.let(onValid)
        },
        modifier = modifier,
        suffix = "kg",
        error = if (ok) null else "0.5–500",
    )
}

@Composable
fun RulesForm(d: SetupDraft) {
    SectionLabel("One day off")
    Body(
        "Declare a day off until this time the night before. Up to ${Rules.DAY_OFF_PER_MONTH} a month; after that it counts as a skip.",
        muted = true,
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Cut-off the night before", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TimeButton(d.cutoff, { d.cutoff = it }, label = "Day-off cut-off", modifier = Modifier.width(110.dp))
    }
    SectionLabel("Make-up mode")
    Body("Skip twice in a week and the next few sessions get a short extra block for the muscles you missed.", muted = true)
    val m = d.makeup
    IntStepperRow("Skips in 7 days that start it", m.trigger, Rules.MAKEUP_TRIGGER_RANGE, { d.makeup = m.copy(trigger = it) })
    IntStepperRow("Make-up sessions per skip", m.sessionsPerSkip, Rules.MAKEUP_SESSIONS_PER_SKIP_RANGE, { d.makeup = m.copy(sessionsPerSkip = it) })
    IntStepperRow("Most make-up sessions at once", m.cap, Rules.MAKEUP_MAX_SESSIONS_RANGE, { d.makeup = m.copy(cap = it) })
    IntStepperRow("Block length (minutes)", m.blockMin, Rules.MAKEUP_BLOCK_MIN_RANGE, { d.makeup = m.copy(blockMin = it) }, unit = "minutes")
}
