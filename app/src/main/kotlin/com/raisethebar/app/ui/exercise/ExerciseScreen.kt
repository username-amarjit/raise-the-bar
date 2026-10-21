package com.raisethebar.app.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raisethebar.app.data.ExercisePage
import com.raisethebar.app.data.exercisePage
import com.raisethebar.app.data.promote
import com.raisethebar.app.ui.components.AlertBox
import com.raisethebar.app.ui.components.AlertKind
import com.raisethebar.app.ui.components.AssetImage
import com.raisethebar.app.ui.components.Body
import com.raisethebar.app.ui.components.Chip
import com.raisethebar.app.ui.components.ChipFlow
import com.raisethebar.app.ui.components.LinkButton
import com.raisethebar.app.ui.components.ListRow
import com.raisethebar.app.ui.components.ScreenFrame
import com.raisethebar.app.ui.components.SectionLabel
import com.raisethebar.app.ui.rememberActions
import com.raisethebar.app.ui.rememberLoad
import com.raisethebar.app.ui.theme.Rtb
import com.raisethebar.engine.Exercise
import com.raisethebar.engine.Groups
import com.raisethebar.engine.LoadKind

/** Exercise page (milestone 2): start and end photos, muscles, three form cues, and backups in order. No video buttons. */
@Composable
fun ExerciseScreen(exerciseId: String, slotId: String?, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val page = rememberLoad(exerciseId, slotId) { exercisePage(exerciseId, slotId) }
    val ex = page?.exercise
    val caption =
        page?.slot?.let { s -> "${page.day?.name ?: "Plan"}, slot ${s.position}" } ?: "Exercise library"
    ScreenFrame(title = ex?.name ?: "", onBack = onBack, caption = "$caption · Saved offline") {
        if (page == null || ex == null) return@ScreenFrame
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssetImage(ex.images.getOrNull(0), "${ex.name}, start position", "Start", Modifier.weight(1f).aspectRatio(1f))
            AssetImage(ex.images.getOrNull(1), "${ex.name}, end position", "End", Modifier.weight(1f).aspectRatio(1f))
        }
        if (ex.images.isEmpty()) Body("No photo for this one yet: follow the form cues below.", muted = true)
        Muscles(ex)
        Notes(page)
        SectionLabel("Form cues")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ex.cues.forEach { cue -> Text("•  $cue", style = MaterialTheme.typography.bodyLarge, color = Rtb.colors.ink) }
        }
        if (ex.instructions.isNotEmpty() && ex.instructions != ex.cues) {
            SectionLabel("How to do it")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ex.instructions.forEachIndexed { i, step -> Body("${i + 1}. $step") }
            }
        }
        if (page.ranked.size > 1) Backups(page, onOpen)
    }
}

@Composable
private fun Muscles(ex: Exercise) {
    ChipFlow {
        ex.primaryMuscles.forEach { Chip(it.replaceFirstChar(Char::uppercase), selected = true) }
        ex.secondaryMuscles.forEach { Chip(it.replaceFirstChar(Char::uppercase), selected = false) }
    }
    val counts = ex.countsToward.joinToString(", ") { Groups.label(it) }
    Body(if (counts.isEmpty()) "Counts toward no tracked muscle group." else "Counts toward: $counts.", muted = true)
}

@Composable
private fun Notes(page: ExercisePage) {
    val ex = page.exercise
    val how =
        listOfNotNull(
            if (ex.perSide) "Weight is per dumbbell and reps are per side." else null,
            if (ex.timed) "A timed hold: the app sets the seconds." else null,
            if (ex.loadKind == LoadKind.BODYWEIGHT) "Uses your body weight; hold a dumbbell to make it harder." else null,
            if (ex.loadKind == LoadKind.ASSISTED) "The stack helps you: more weight makes it easier." else null,
        )
    how.forEach { Body(it) }
    page.blockReason?.let { AlertBox("Not available today: $it.", kind = AlertKind.WARN) }
    page.caution?.let { AlertBox(it, kind = AlertKind.WARN) }
    if (page.outgrown) {
        AlertBox(
            "Even the heaviest weight here leaves you above the rep range. Try a harder version: one arm or leg at a time, " +
                "a pause, or a slower lowering.",
            kind = AlertKind.GOOD,
            title = "Ready for a harder version",
        )
    }
}

@Composable
private fun Backups(page: ExercisePage, onOpen: (String) -> Unit) {
    val actions = rememberActions()
    val slot = page.slot ?: return
    SectionLabel("If it's busy or broken")
    Body(page.ranked.drop(1).joinToString(", then ") { it.name.replaceFirstChar(Char::lowercase) }.replaceFirstChar(Char::uppercase))
    page.ranked.forEachIndexed { i, option ->
        val status = page.optionBlocks[option.id] ?: "Available"
        val rank = if (i == 0) "First choice" else "Backup $i"
        ListRow(name = option.name, meta = "$rank · $status", metaAccent = page.optionBlocks[option.id] != null, onClick = { onOpen(option.id) })
        if (i > 0) LinkButton("Make ${option.name.lowercase()} my first choice", onClick = { actions.run { promote(slot.id, option.id) } })
    }
    if (slot.promotedExerciseId != null) {
        LinkButton("Go back to the plan's order", onClick = { actions.run { promote(slot.id, null) } })
    }
}
