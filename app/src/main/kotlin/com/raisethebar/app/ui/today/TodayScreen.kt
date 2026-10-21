package com.raisethebar.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raisethebar.app.data.Dates
import com.raisethebar.app.data.TodayKind
import com.raisethebar.app.data.TodayState
import com.raisethebar.app.data.formatKg
import com.raisethebar.app.data.todayState
import com.raisethebar.app.ui.components.AlertBox
import com.raisethebar.app.ui.components.AlertKind
import com.raisethebar.app.ui.components.Body
import com.raisethebar.app.ui.components.DayTag
import com.raisethebar.app.ui.components.LinkButton
import com.raisethebar.app.ui.components.ListRow
import com.raisethebar.app.ui.components.PrimaryButton
import com.raisethebar.app.ui.components.RtbCard
import com.raisethebar.app.ui.components.ScreenTitle
import com.raisethebar.app.ui.components.SecondaryButton
import com.raisethebar.app.ui.components.SectionLabel
import com.raisethebar.app.ui.rememberLoad
import com.raisethebar.app.ui.theme.Accent
import com.raisethebar.app.ui.theme.Rtb
import com.raisethebar.config.Rules
import com.raisethebar.engine.DayType
import com.raisethebar.engine.Exercise
import com.raisethebar.engine.Groups
import com.raisethebar.engine.LoadKind
import com.raisethebar.engine.Pick
import com.raisethebar.engine.PlannedExercise
import com.raisethebar.engine.Reason
import com.raisethebar.engine.SessionKind
import java.time.LocalDate

class TodayActions(
    val onSettings: () -> Unit = {},
    val onStart: (kind: String) -> Unit = {},
    val onResume: (sessionId: Long) -> Unit = {},
    val onExercise: (exerciseId: String, slotId: String?) -> Unit = { _, _ -> },
    val onSomethingHurts: () -> Unit = {},
    val onPlanChanges: () -> Unit = {},
    val onCheckin: (LocalDate) -> Unit = {},
    val onEditWeek: () -> Unit = {},
)

fun accentFor(dayType: String): Accent =
    when (dayType) {
        DayType.PUSH -> Accent.PUSH
        DayType.PULL -> Accent.PULL
        DayType.LEGS -> Accent.LEGS
        else -> Accent.GOLD
    }

/** "3 × 6–8, stop with 2 left", "2 × 30–60 s" or "Find your weight: sets of 8". */
fun setsLine(pe: PlannedExercise): String {
    val range = { lo: Int, hi: Int -> if (lo == hi) "$lo" else "$lo–$hi" }
    return when {
        pe.ramp -> "Find your weight: work up in sets of ${Rules.RAMP_REPS}"
        pe.isTimed -> "${pe.sets} × ${range(pe.secLow!!, pe.secHigh ?: pe.secLow!!)} s"
        pe.repLow == null -> "${pe.sets} × stop with ${pe.targetLeft} left"
        pe.targetLeft == null -> "${pe.sets} × ${range(pe.repLow!!, pe.repHigh ?: pe.repLow!!)}"
        else -> "${pe.sets} × ${range(pe.repLow!!, pe.repHigh ?: pe.repLow!!)}, stop with ${pe.targetLeft} left"
    }
}

/** The big number on the right of a row: kilograms, "Body", "+5", "30 help", seconds or "Ramp". */
fun weightLabel(pe: PlannedExercise): String? {
    val shown = pe.displayWeight()
    return when {
        pe.ramp -> "Ramp"
        pe.isTimed -> "${pe.secLow} s"
        pe.loadKind == LoadKind.BODYWEIGHT -> if (shown == null || shown <= 0.0) "Body" else "+${formatKg(shown)}"
        pe.loadKind == LoadKind.ASSISTED -> shown?.let { "${formatKg(it)} help" }
        shown != null -> formatKg(shown)
        else -> null
    }
}

@Composable
fun TodayScreen(a: TodayActions = TodayActions()) {
    val state = rememberLoad { todayState() }
    val lib = rememberLoad { exercises() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                state?.let { s -> listOfNotNull(Dates.day(s.date), s.phaseLabel).joinToString(" · ") }.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = Rtb.colors.muted,
                modifier = Modifier.weight(1f),
            )
            LinkButton("Settings", onClick = a.onSettings)
        }
        if (state == null || lib == null) return@Column
        Banners(state, a)
        when (state.kind) {
            TodayKind.TRAIN, TodayKind.PRE_TRIP, TodayKind.AWAY_SESSION -> Workout(state, lib, a)
            else -> NoWorkout(state, a)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Banners(s: TodayState, a: TodayActions) {
    if (s.banner) {
        AlertBox("Four skips this week. Would a different training time work better?", kind = AlertKind.WARN)
        LinkButton("Change my training times", onClick = a.onEditWeek)
    }
    s.reason?.let { r ->
        val text =
            when (r) {
                Reason.SICK -> "Recorded: sick. Today is excused and make-up mode is paused."
                Reason.INJURED -> "Recorded: injured. Today is excused."
                Reason.GOING_LATER -> "Going later: the check-in moves with you."
                Reason.GYM_CLOSED -> "Gym closed: the home version counts as done."
                Reason.SKIPPING -> "Skipping today: it counts as a skip at midnight."
                else -> null
            }
        if (text != null && s.doneToday == null) AlertBox(text)
    }
}

@Composable
private fun Workout(s: TodayState, lib: Map<String, Exercise>, a: TodayActions) {
    val plan = s.plan ?: return
    DayTag(plan.dayName, accentFor(plan.dayType))
    ScreenTitle(plan.focus)
    if (s.kind == TodayKind.PRE_TRIP) {
        AlertBox("Your last session before the trip is full body, so no big muscle goes more than a week untrained.", kind = AlertKind.WARN)
    }
    MakeupStatus(s, lib)
    RtbCard(accent = accentFor(plan.dayType)) {
        plan.exercises.forEach { pe -> ExerciseRow(pe, lib, a) }
        if (plan.makeup.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Make-up block, ${Rules.MAKEUP_BLOCK_MIN} minutes")
            plan.makeup.forEach { pe -> ExerciseRow(pe, lib, a) }
        }
    }
    when {
        s.openSession != null -> PrimaryButton("Resume workout", onClick = { a.onResume(s.openSession.id) })
        s.doneToday != null ->
            AlertBox("Done for today. ${plan.dayName} is logged, and the next workout is ready for your next training day.", kind = AlertKind.GOOD)
        s.kind == TodayKind.AWAY_SESSION -> PrimaryButton("Start ${plan.dayName}", onClick = { a.onStart(SessionKind.AWAY) })
        else -> {
            PrimaryButton("Start workout", onClick = { a.onStart(if (s.kind == TodayKind.PRE_TRIP) SessionKind.PRE_TRIP else SessionKind.FULL) })
            if (s.kind == TodayKind.TRAIN) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Minimum session", onClick = { a.onStart(SessionKind.MINIMUM) }, modifier = Modifier.weight(1f))
                    SecondaryButton("Gym closed", onClick = { a.onStart(SessionKind.HOME) }, modifier = Modifier.weight(1f))
                }
                Body("Minimum: the first 3 exercises, 2 sets each. Gym closed: the 30-minute home version. Both count as done.", muted = true)
            }
        }
    }
    FooterLinks(s, a)
}

@Composable
private fun ExerciseRow(pe: PlannedExercise, lib: Map<String, Exercise>, a: TodayActions) {
    val ex = lib[pe.exerciseId] ?: return
    val swap = pe.pickSource != Pick.Source.FIRST_CHOICE.name && pe.note != null
    val meta =
        when {
            pe.injuryNote != null -> pe.injuryNote
            swap -> pe.note
            pe.isMakeup -> "${Groups.label(pe.makeupGroup.orEmpty())}: ${setsLine(pe)}"
            else -> setsLine(pe)
        }
    ListRow(
        name = ex.name + (pe.healthySide?.let { " ($it side)" } ?: ""),
        meta = listOfNotNull(meta, pe.caution, if (pe.outgrown) "Ready for a harder version" else null).joinToString(" · "),
        metaAccent = swap || pe.injuryNote != null || pe.caution != null,
        trailing = weightLabel(pe),
        trailingUp = pe.wentUp,
        onClick = { a.onExercise(pe.exerciseId, pe.slotId) },
    )
}

@Composable
private fun MakeupStatus(s: TodayState, lib: Map<String, Exercise>) {
    val m = s.makeup
    if (!m.active) return
    if (s.makeupPaused) {
        AlertBox("Make-up mode is paused with ${m.sessionsLeft} sessions left. It picks up again once you're well.")
        return
    }
    RtbCard(accent = Accent.GOLD) {
        DayTag("Make-up mode", Accent.GOLD)
        Spacer(Modifier.height(6.dp))
        Text("Make-up mode: ${m.sessionsLeft} sessions left", style = MaterialTheme.typography.headlineSmall, color = Rtb.colors.ink)
        val block = s.plan?.makeup.orEmpty()
        if (block.isNotEmpty()) {
            val names = block.mapNotNull { lib[it.exerciseId]?.name?.lowercase() }.distinct().joinToString(" and ")
            Body("Today: $names, ${block.first().sets} sets each, about ${Rules.MAKEUP_BLOCK_MIN} minutes at the end.")
        }
        Body(
            "Skip again before these are done and it can stretch to ${Rules.MAKEUP_MAX_SESSIONS} sessions. Then it's back to the normal plan.",
            muted = true,
        )
    }
}

@Composable
private fun NoWorkout(s: TodayState, a: TodayActions) {
    val (tag, title, body) =
        when (s.kind) {
            TodayKind.DAY_OFF -> Triple("Day off", "Day off", "Declared in time, so it's not a skip. The rotation waits for your next training day.")
            TodayKind.AWAY_TRAVEL -> Triple("Away", "Travel day", "Paused. Safe travels.")
            TodayKind.AWAY_REST -> Triple("Away", "Rest day", "No session today while you're away.")
            TodayKind.AWAY_PAUSED -> Triple("Away", "Paused", "The plan is paused while you're away. Nothing counts as a skip.")
            else -> Triple("Rest", "Rest day", s.restText + ".")
        }
    DayTag(tag, if (s.kind == TodayKind.REST || s.kind == TodayKind.DAY_OFF) Accent.REST else Accent.GOLD)
    ScreenTitle(title)
    RtbCard(accent = Accent.REST) { Body(body) }
    FooterLinks(s, a)
}

@Composable
private fun FooterLinks(s: TodayState, a: TodayActions) {
    Column {
        LinkButton("Something hurts", onClick = a.onSomethingHurts)
        LinkButton("Plan changes", onClick = a.onPlanChanges)
        if (s.kind == TodayKind.TRAIN && s.doneToday == null && s.openSession == null) {
            LinkButton("Not training today?", onClick = { a.onCheckin(s.date) })
        }
    }
}
