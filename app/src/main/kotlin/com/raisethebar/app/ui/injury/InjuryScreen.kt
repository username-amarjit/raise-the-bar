package com.raisethebar.app.ui.injury

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.raisethebar.app.data.TodayState
import com.raisethebar.app.data.markChecked
import com.raisethebar.app.data.morningCheck
import com.raisethebar.app.data.previewInjury
import com.raisethebar.app.data.replanAfterInjury
import com.raisethebar.app.data.startInjury
import com.raisethebar.app.ui.components.AlertBox
import com.raisethebar.app.ui.components.AlertKind
import com.raisethebar.app.ui.components.Body
import com.raisethebar.app.ui.components.Chip
import com.raisethebar.app.ui.components.ChipFlow
import com.raisethebar.app.ui.components.PainScale
import com.raisethebar.app.ui.components.PrimaryButton
import com.raisethebar.app.ui.components.RtbCard
import com.raisethebar.app.ui.components.ScreenFrame
import com.raisethebar.app.ui.components.SecondaryButton
import com.raisethebar.app.ui.components.SectionLabel
import com.raisethebar.app.ui.components.Segmented
import com.raisethebar.app.ui.rememberActions
import com.raisethebar.app.ui.rememberLoad
import com.raisethebar.app.ui.theme.Accent
import com.raisethebar.app.ui.theme.Rtb
import com.raisethebar.engine.Groups
import com.raisethebar.engine.Injuries
import com.raisethebar.engine.InjuryState
import com.raisethebar.engine.SeedData
import com.raisethebar.engine.Side
import com.raisethebar.engine.Triage
import kotlin.math.roundToInt

private val FEELS = listOf(Triage.FEEL_DULL to "Dull, spread across the muscle", Triage.FEEL_SHARP to "Sharp, pinching or in a joint")
private val ONSETS =
    listOf(
        Triage.ONSET_AFTER_TRAINING to "12–48 hours after training",
        Triage.ONSET_DURING_SET to "During a set",
        Triage.ONSET_MORNING_AFTER to "The morning after a heavy session",
        Triage.ONSET_OTHER to "Something else",
    )

/** "Something hurts" (milestone 11): area and side, what it feels like, pain and red flags, then the result. */
@Composable
fun InjuryScreen(fromSession: Long, onDone: () -> Unit) {
    val rules = SeedData.injuryRules
    val actions = rememberActions()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var area by rememberSaveable { mutableStateOf(rules.areas.first().id) }
    var side by rememberSaveable { mutableStateOf(Side.LEFT) }
    var muscle by rememberSaveable { mutableStateOf<String?>(null) }
    var feel by rememberSaveable { mutableStateOf(Triage.FEEL_SHARP) }
    var onset by rememberSaveable { mutableStateOf(Triage.ONSET_DURING_SET) }
    var eases by rememberSaveable { mutableStateOf(false) }
    var pain by rememberSaveable { mutableStateOf<Int?>(null) }
    val flags = remember { mutableStateMapOf<String, Boolean>() }
    var emergency by rememberSaveable { mutableStateOf(false) }
    val rule = rules.area(area)
    val answers =
        Triage.Answers(
            area = area,
            side = if (rule.sided) side else Side.BOTH,
            muscle = muscle,
            feel = feel,
            onset = onset,
            easesWithWarmUp = eases,
            painNow = pain ?: 0,
            redFlags = flags.filterValues { it }.keys,
            emergency = emergency,
        )
    val title =
        if (step ==
            0
        ) {
            "Something hurts"
        } else {
            Injuries.areaName(InjuryState(0, area, answers.side, "active", 1, java.time.LocalDate.MIN, muscle = muscle), rules)
        }
    val finish = {
        actions.run(then = onDone) {
            startInjury(answers)
            if (fromSession >= 0) replanAfterInjury(fromSession)
        }
    }
    ScreenFrame(
        title = title,
        caption = if (step < 3) "Something hurts · Step ${step + 1} of 3" else "Something hurts",
        onBack = if (step > 0) ({ step -= 1 }) else onDone,
        bottom = {
            if (step < 2) {
                PrimaryButton("Next", enabled = step != 0 || area != Injuries.MUSCLE || muscle != null, onClick = { step++ })
            } else if (step == 2) {
                PrimaryButton("See what changes", enabled = pain != null, onClick = { step++ })
            }
        },
    ) {
        when (step) {
            0 -> {
                SectionLabel("Where")
                ChipFlow { rules.areas.forEach { a -> Chip(a.label, selected = area == a.id, onClick = { area = a.id }) } }
                if (area == Injuries.MUSCLE) {
                    SectionLabel("Which muscle")
                    ChipFlow { Groups.ALL.forEach { g -> Chip(Groups.label(g), selected = muscle == g, onClick = { muscle = g }) } }
                }
                if (rule.sided) {
                    SectionLabel("Side")
                    val sides = listOf(Side.LEFT, Side.RIGHT, Side.BOTH)
                    Segmented(listOf("Left", "Right", "Both"), sides.indexOf(side), { side = sides[it] }, description = "Side")
                }
            }
            1 -> {
                SectionLabel("What it feels like")
                ChipFlow { FEELS.forEach { (id, label) -> Chip(label, selected = feel == id, onClick = { feel = id }) } }
                SectionLabel("When it started")
                ChipFlow { ONSETS.forEach { (id, label) -> Chip(label, selected = onset == id, onClick = { onset = id }) } }
                SectionLabel("Does it ease as you warm up?")
                Segmented(listOf("Yes", "No"), if (eases) 0 else 1, { eases = it == 0 }, description = "Eases with warm-up")
            }
            2 -> {
                SectionLabel("Pain right now, 0 to 10")
                PainScale(pain, { pain = it })
                SectionLabel("Any of these? Tap each one that's true")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rules.redFlags.forEach { f -> Chip(f.text, selected = flags[f.id] == true, onClick = { flags[f.id] = flags[f.id] != true }) }
                    Chip(rules.emergency.text, selected = emergency, accent = Accent.PUSH, onClick = { emergency = !emergency })
                }
            }
            else -> Result(answers, onDone, finish)
        }
    }
}

@Composable
private fun Result(answers: Triage.Answers, onDone: () -> Unit, apply: () -> Unit) {
    val rules = SeedData.injuryRules
    when (Triage.classify(answers)) {
        Triage.Result.EMERGENCY -> {
            AlertBox(rules.emergencyText, kind = AlertKind.DANGER, title = "Stop now and get medical help")
            PrimaryButton("Back", onClick = onDone)
        }
        Triage.Result.STOP -> {
            AlertBox(rules.stopText, kind = AlertKind.DANGER, title = "See a doctor or physio first")
            Body("Until then, every exercise that uses this area stays out of your plan. Everything else carries on.", muted = true)
            PrimaryButton("Lock this area until I've been checked", onClick = apply)
        }
        Triage.Result.SORENESS -> {
            AlertBox(rules.warmUpTip, kind = AlertKind.GOOD, title = "Sounds like normal soreness")
            Body("No injury mode needed.", muted = true)
            PrimaryButton("Back to training", onClick = onDone)
        }
        Triage.Result.INJURY -> {
            val preview = rememberLoad(answers) { previewInjury(answers) }
            if (preview != null) {
                val out =
                    if (preview.takenOut.isEmpty()) {
                        "Nothing in your plan needs swapping."
                    } else {
                        "${preview.takenOut.joinToString(", ")} ${if (preview.takenOut.size == 1) "is" else "are"} out, " +
                            "with ${preview.swaps} ${if (preview.swaps == 1) "swap" else "swaps"} made."
                    }
                AlertBox("$out ${preview.keeps} carry on as normal.", kind = AlertKind.GOOD, title = "${preview.name} mode, phase 1.")
            }
            Body(rules.note, muted = true)
            PrimaryButton("Apply to my plan", onClick = apply)
        }
    }
}

private fun phaseText(i: InjuryState): String {
    val pct = ((Injuries.loadFraction(i) ?: 0.0) * 100).roundToInt()
    return when (i.phase) {
        1 -> "Calm it down: what provokes it is out, and everything else carries on. Phase 2 starts after a green morning check."
        2 -> "Rebuild: the affected exercises come back over a pain-free range at $pct% of your old weight, 12–20 reps, slow lowering."
        3 -> "Return: the original exercises at $pct% of your old weight, up 10% after each green session."
        else -> "Back: your pre-injury weights. Two green sessions here end injury mode."
    }
}

/** Today's injury cards: the morning rating, the phase, "I've been checked" and the physio prompt. */
@Composable
fun InjuryCards(s: TodayState) {
    val rules = SeedData.injuryRules
    val actions = rememberActions()
    val active = s.injuries.filter { it.isActive }
    if (active.isEmpty()) return
    if (s.morningCheck) {
        val ratings = remember { mutableStateMapOf<Long, Int>() }
        val sharp = remember { mutableStateMapOf<Long, Boolean>() }
        RtbCard(accent = Accent.GOLD) {
            Text("Morning check", style = MaterialTheme.typography.headlineSmall, color = Rtb.colors.ink)
            active.forEach { i ->
                Body("How does your ${Injuries.areaName(i, rules).lowercase()} feel this morning, 0 to 10?")
                PainScale(ratings[i.id], { ratings[i.id] = it }, sharp = sharp[i.id] == true)
                Chip("Sharp pain", selected = sharp[i.id] == true, onClick = { sharp[i.id] = sharp[i.id] != true })
            }
            PrimaryButton("Save", enabled = active.all { it.id in ratings }, onClick = {
                actions.run { morningCheck(active.associate { it.id to (ratings.getValue(it.id) to (sharp[it.id] == true)) }) }
            })
        }
    }
    active.forEach { i ->
        RtbCard(accent = if (i.locked) Accent.PUSH else Accent.GOLD) {
            Text("${Injuries.areaName(i, rules)}: phase ${i.phase} of 4", style = MaterialTheme.typography.headlineSmall, color = Rtb.colors.ink)
            if (i.locked) {
                AlertBox(rules.stopText, kind = AlertKind.DANGER)
                SecondaryButton("I've been checked", onClick = { actions.run { markChecked(i.id) } })
            } else {
                Body(phaseText(i))
            }
            if (Injuries.needsPhysio(i, s.date)) AlertBox("It hasn't cleared in two weeks: please see a physiotherapist.", kind = AlertKind.WARN)
            Body(rules.note, muted = true)
        }
    }
}
