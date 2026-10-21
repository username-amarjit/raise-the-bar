package com.raisethebar.app.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.raisethebar.app.data.SessionView
import com.raisethebar.app.data.SetInput
import com.raisethebar.app.data.clearRest
import com.raisethebar.app.data.confirmRamp
import com.raisethebar.app.data.discardSession
import com.raisethebar.app.data.doNextFirst
import com.raisethebar.app.data.finishSession
import com.raisethebar.app.data.formatKg
import com.raisethebar.app.data.logSet
import com.raisethebar.app.data.selectExercise
import com.raisethebar.app.data.sessionView
import com.raisethebar.app.data.swapForToday
import com.raisethebar.app.notify.Haptics
import com.raisethebar.app.ui.Actions
import com.raisethebar.app.ui.LocalRepo
import com.raisethebar.app.ui.components.AlertBox
import com.raisethebar.app.ui.components.AlertKind
import com.raisethebar.app.ui.components.Body
import com.raisethebar.app.ui.components.Chip
import com.raisethebar.app.ui.components.LinkButton
import com.raisethebar.app.ui.components.ListRow
import com.raisethebar.app.ui.components.PainScale
import com.raisethebar.app.ui.components.PrimaryButton
import com.raisethebar.app.ui.components.RtbCard
import com.raisethebar.app.ui.components.ScreenFrame
import com.raisethebar.app.ui.components.SecondaryButton
import com.raisethebar.app.ui.components.SectionLabel
import com.raisethebar.app.ui.components.Segmented
import com.raisethebar.app.ui.components.Stepper
import com.raisethebar.app.ui.rememberActions
import com.raisethebar.app.ui.rememberLoad
import com.raisethebar.app.ui.theme.Rtb
import com.raisethebar.app.ui.today.setsLine
import com.raisethebar.config.Rules
import com.raisethebar.engine.Gym
import com.raisethebar.engine.LoadKind
import com.raisethebar.engine.Loads
import com.raisethebar.engine.PlannedExercise
import com.raisethebar.engine.Time
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

private const val EPS = Rules.WEIGHT_EPSILON_KG

/** The workout (milestone 7): exercises in order, the set logger, the rest timer and Finish. Every set is saved at once. */
@Composable
fun SessionScreen(id: Long, onClose: () -> Unit, onExercise: (String) -> Unit, onSomethingHurts: (Long) -> Unit) {
    val actions = rememberActions()
    val view = rememberLoad(id) { sessionView(id) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val st = view?.state
    val current = st?.let { s -> s.current?.let(s::exercise) ?: s.ordered.firstOrNull { !view.isComplete(it) } }
    ScreenFrame(
        title = current?.let { view.exercises[it.exerciseId]?.name } ?: "All sets logged",
        caption = st?.plan?.let { "${it.dayName} · ${it.phaseLabel}" },
        onBack = onClose,
        bottom = {
            if (view != null && view.session.completedAt == null) {
                if (view.allComplete) {
                    PrimaryButton("Finish workout", onClick = { actions.run(then = onClose) { finishSession(id) } })
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("Finish early", onClick = { confirmFinish = true }, modifier = Modifier.weight(1f))
                        SecondaryButton("Discard", onClick = { confirmDiscard = true }, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
    ) {
        if (view == null || st == null) return@ScreenFrame
        if (view.session.completedAt != null) {
            AlertBox("This workout is finished and saved.", kind = AlertKind.GOOD)
            return@ScreenFrame
        }
        RestTimer(st.restEndsAt, onSkip = { actions.run { clearRest(id) } })
        if (current != null) SetLogger(view, current, actions, onExercise, onSomethingHurts)
        ExerciseList(view, current?.key) { key -> actions.run { selectExercise(id, key) } }
    }
    if (confirmFinish) {
        Confirm("Finish now?", "Sets you haven't logged are left out. Estimates update from what you did log.", "Finish", onDismiss = {
            confirmFinish =
                false
        }) {
            actions.run(then = onClose) { finishSession(id) }
        }
    }
    if (confirmDiscard) {
        Confirm("Discard this workout?", "Nothing from it is saved, and today stays open.", "Discard", onDismiss = { confirmDiscard = false }) {
            actions.run(then = onClose) { discardSession(id) }
        }
    }
}

@Composable
private fun Confirm(title: String, text: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(action) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Counts down from the stored end time, so it survives the screen locking; buzzes at zero. */
@Composable
private fun RestTimer(endsAt: String?, onSkip: () -> Unit) {
    val repo = LocalRepo.current
    val context = LocalContext.current
    val end = endsAt?.let { Time.parseTimestamp(it).toInstant().toEpochMilli() }
    var remaining by remember(end) { mutableLongStateOf(end?.let { it - repo.time.clock.millis() } ?: 0L) }
    LaunchedEffect(end) {
        if (end == null) return@LaunchedEffect
        var stale = 0
        while (true) {
            val left = end - repo.time.clock.millis()
            stale = if (left == remaining) stale + 1 else 0
            remaining = left
            if (left <= 0) {
                Haptics.restOver(context, end)
                break
            }
            if (stale > STALE_TICKS) break
            delay(min(left, TICK_MS))
        }
    }
    if (end == null || remaining <= 0) return
    val secs = (remaining + 999) / 1000
    RtbCard {
        SectionLabel("Rest")
        Text("%d:%02d".format(java.util.Locale.ROOT, secs / 60, secs % 60), style = MaterialTheme.typography.displayMedium, color = Rtb.colors.ink)
        LinkButton("Skip rest", onClick = onSkip)
    }
}

private const val TICK_MS = 250L
private const val STALE_TICKS = 40

private fun displayValue(pe: PlannedExercise, effective: Double): Pair<String, String> {
    val shown = pe.displayWeight(effective) ?: effective
    return when (pe.loadKind) {
        LoadKind.BODYWEIGHT -> if (shown <= EPS) "Body" to "body weight" else "+${formatKg(shown)}" to "kg added"
        LoadKind.ASSISTED -> formatKg(shown) to "kg assistance"
        else -> formatKg(shown) to "kg"
    }
}

@Composable
private fun SetLogger(view: SessionView, pe: PlannedExercise, actions: Actions, onExercise: (String) -> Unit, onSomethingHurts: (Long) -> Unit) {
    val st = view.state
    val id = view.session.id
    val ex = view.exercises[pe.exerciseId]
    val loads = view.loads[pe.key] ?: Loads.NONE
    val logged = view.setsFor(pe.key)
    val ramping = st.rampOngoing(pe)
    val target = view.targetReps(pe)
    val n = logged.size
    var weight by remember(pe.key, n) { mutableStateOf(view.suggestedWeight(pe) ?: loads.lightest) }
    var reps by remember(pe.key, n) { mutableIntStateOf(target ?: pe.repLow ?: Rules.MAKEUP_DEFAULT_REP_LOW) }
    var seconds by remember(pe.key, n) { mutableIntStateOf(pe.secLow ?: 0) }
    var left by remember(pe.key, n) { mutableStateOf<Int?>(null) }
    var pain by remember(pe.key, n) { mutableStateOf<Int?>(null) }
    var sharp by remember(pe.key, n) { mutableStateOf(false) }
    var busy by remember(pe.key) { mutableStateOf(false) }

    val caption =
        when {
            ramping -> "Find your weight, set ${logged.count { it.isRamp } + 1}"
            else -> "Set ${min(view.nextSetNo(pe), pe.sets)} of ${pe.sets}"
        }
    Text(caption, style = MaterialTheme.typography.labelMedium, color = Rtb.colors.muted)
    Text(
        when {
            ramping -> "Do ${Rules.RAMP_REPS} reps, then say how many were left"
            pe.isTimed -> "Hold ${pe.secLow} s"
            pe.repLow == null -> "Stop with ${pe.targetLeft} left"
            pe.targetLeft == null -> "Target $target reps"
            else -> "Target $target reps, ${pe.targetLeft} left"
        },
        style = MaterialTheme.typography.headlineMedium,
        color = Rtb.colors.ink,
    )
    Notes(pe, ramping)
    if (pe.warmups.isNotEmpty() && logged.isEmpty()) Warmups(view, pe, actions)

    val w = weight
    if (!loads.isEmpty && w != null) {
        val (value, unit) = displayValue(pe, w)
        val lower = loads.values.lastOrNull { it < w - EPS }
        val higher = loads.values.firstOrNull { it > w + EPS }
        Stepper(value, unit, onMinus = {
            weight = lower
        }, onPlus = { weight = higher }, minusEnabled = lower != null, plusEnabled = higher != null, label = "Weight")
    }
    if (pe.isTimed) {
        HoldTimer(seconds, onChange = { seconds = it })
    } else {
        Stepper(
            "$reps",
            "reps",
            onMinus = { reps = max(0, reps - 1) },
            onPlus = { reps = min(Rules.Limits.REPS.last, reps + 1) },
            minusEnabled =
                reps > 0,
            label = "Reps",
        )
        SectionLabel("Reps left in the tank?")
        Segmented(listOf("0", "1", "2", "3", "4+"), left, { left = it }, description = "Reps left")
    }
    if (pe.painRequired) {
        SectionLabel("Pain during this set, 0 to 10")
        PainScale(pain, { pain = it }, sharp = sharp)
        Chip("Sharp pain", selected = sharp, onClick = { sharp = !sharp })
    }
    st.messages[pe.key]?.takeIf { it.isNotEmpty() && logged.isNotEmpty() }?.let { AlertBox(it, kind = AlertKind.WARN) }
    val ready = (pe.isTimed || left != null) && (!pe.painRequired || pain != null) && (loads.isEmpty || w != null)
    PrimaryButton("Log set", enabled = ready, onClick = {
        actions.run {
            logSet(
                id,
                SetInput(
                    key = pe.key,
                    weight = if (loads.isEmpty) null else w,
                    reps = if (pe.isTimed) null else reps,
                    left = if (pe.isTimed) null else left,
                    seconds = if (pe.isTimed) seconds else null,
                    pain = pain,
                    sharp = sharp,
                ),
            )
        }
    })
    if (pe.key in st.rampConfirm) {
        SecondaryButton("That's my weight", onClick = { actions.run { confirmRamp(id, pe.key) } })
    }
    Row {
        if (ex != null && ex.equipmentIds.isNotEmpty() && pe.slotId != null) LinkButton("Machine busy? Swap", onClick = { busy = true })
        LinkButton("Something hurts", onClick = { onSomethingHurts(id) })
        if (ex != null) LinkButton("Form", onClick = { onExercise(ex.id) })
    }
    if (logged.isNotEmpty()) {
        SectionLabel("Logged")
        logged.forEachIndexed { i, s ->
            val load = s.weightKg?.let { w -> displayValue(pe, w).let { (v, u) -> "$v $u" } }
            val what =
                s.seconds?.let { "$it s" }
                    ?: (listOfNotNull(load, s.reps?.let { "× $it" }).joinToString(" ") + (s.repsLeft?.let { ", $it left" } ?: ""))
            Body("${if (s.isRamp) "Ramp ${i + 1}" else "Set ${i + 1}"}: $what${s.pain?.let { ", pain $it" } ?: ""}", muted = true)
        }
    }
    if (busy && ex != null) {
        val next = view.state.ordered.dropWhile { it.key != pe.key }.drop(1).firstOrNull { !view.isComplete(it) }
        val nextEx = next?.let { view.exercises[it.exerciseId] }
        AlertDialog(
            onDismissRequest = { busy = false },
            title = { Text("Machine busy?") },
            text = { Text("Wait and do the next exercise first, or use the next backup for today only.") },
            confirmButton = {
                TextButton(onClick = {
                    busy = false
                    actions.run { swapForToday(id, pe.key) }
                }) { Text("Use the next backup") }
            },
            dismissButton = {
                if (Gym.canDoNextFirst(ex, nextEx)) {
                    TextButton(onClick = {
                        busy = false
                        actions.run { doNextFirst(id, pe.key) }
                    }) { Text("Do ${nextEx?.name?.lowercase()} first") }
                } else {
                    TextButton(onClick = { busy = false }) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun Notes(pe: PlannedExercise, ramping: Boolean) {
    if (ramping) Body("Start light. After each set the app suggests the next weight; stop when 2 or fewer reps are left.", muted = true)
    pe.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Rtb.colors.gold) }
    pe.injuryNote?.let { AlertBox(it, kind = AlertKind.WARN) }
    pe.caution?.let { AlertBox(it, kind = AlertKind.WARN) }
    if (pe.tooBigJump) Body("The next weight up is too big a jump, so stay here and add reps.", muted = true)
    if (pe.outgrown) Body("Ready for a harder version: try one side at a time, a pause, or a slower lowering.", muted = true)
}

@Composable
private fun Warmups(view: SessionView, pe: PlannedExercise, actions: Actions) {
    val done = view.warmupsFor(pe.key).size
    RtbCard {
        SectionLabel("Warm-up sets (optional to log)")
        pe.warmups.forEachIndexed { i, wu ->
            val (v, u) = displayValue(pe, wu.weight)
            Row {
                Text("$v $u × ${wu.reps}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (i < done) {
                    Text("✓ Done", style = MaterialTheme.typography.labelMedium, color = Rtb.colors.legs)
                } else if (i == done) {
                    LinkButton("Done", onClick = {
                        actions.run { logSet(view.session.id, SetInput(pe.key, wu.weight, wu.reps, null, warmup = true)) }
                    })
                }
            }
        }
    }
}

@Composable
private fun HoldTimer(seconds: Int, onChange: (Int) -> Unit) {
    var startedAt by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(startedAt) {
        val start = startedAt ?: return@LaunchedEffect
        while (true) {
            onChange(((System.currentTimeMillis() - start) / 1000).toInt().coerceAtMost(Rules.Limits.HOLD_S.last))
            delay(TICK_MS)
        }
    }
    Stepper(
        "$seconds",
        "seconds",
        onMinus = { onChange(max(Rules.Limits.HOLD_S.first, seconds - Rules.HOLD_STEP_S)) },
        onPlus = { onChange(min(Rules.Limits.HOLD_S.last, seconds + Rules.HOLD_STEP_S)) },
        label = "Hold",
    )
    if (startedAt == null) {
        SecondaryButton("Start the timer", onClick = {
            onChange(0)
            startedAt = System.currentTimeMillis()
        })
    } else {
        SecondaryButton("Stop", onClick = { startedAt = null })
    }
}

@Composable
private fun ExerciseList(view: SessionView, currentKey: String?, onSelect: (String) -> Unit) {
    SectionLabel("Today's exercises")
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        view.state.ordered.forEach { pe ->
            val name = view.exercises[pe.exerciseId]?.name ?: pe.exerciseId
            val done = view.isComplete(pe)
            val progress =
                when {
                    pe.key in view.state.stopped -> "Stopped today"
                    done -> "✓ Done"
                    pe.key == currentKey -> "Now · ${view.workingDone(pe)} of ${pe.sets} sets"
                    else -> "${view.workingDone(pe)} of ${pe.sets} sets · ${setsLine(pe)}"
                }
            ListRow(name = (if (pe.isMakeup) "Make-up: " else "") + name, meta = progress, metaAccent = pe.key == currentKey, onClick = {
                onSelect(pe.key)
            })
        }
    }
}
