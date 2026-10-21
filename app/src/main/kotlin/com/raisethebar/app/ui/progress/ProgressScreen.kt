package com.raisethebar.app.ui.progress

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.raisethebar.app.data.Dates
import com.raisethebar.app.data.SkipScreen
import com.raisethebar.app.data.formatKg
import com.raisethebar.app.data.skipScreen
import com.raisethebar.app.ui.components.AlertBox
import com.raisethebar.app.ui.components.AlertKind
import com.raisethebar.app.ui.components.Body
import com.raisethebar.app.ui.components.ListRow
import com.raisethebar.app.ui.components.RtbCard
import com.raisethebar.app.ui.components.ScreenFrame
import com.raisethebar.app.ui.components.SubHeading
import com.raisethebar.app.ui.rememberLoad
import com.raisethebar.app.ui.theme.Rtb
import com.raisethebar.engine.Injuries
import com.raisethebar.engine.InjuryRules
import com.raisethebar.engine.InjuryState
import com.raisethebar.engine.Nutrition
import com.raisethebar.engine.Reason
import com.raisethebar.engine.SessionKind
import kotlin.math.roundToInt

private data class HistoryRow(
    val date: java.time.LocalDate,
    val name: String,
    val kind: String,
    val sets: Int,
)

private data class ProgressData(
    val screen: SkipScreen,
    val history: List<HistoryRow>,
    val avg: Double?,
    val change: Double?,
    val injuries: List<InjuryState>,
    val rules: InjuryRules,
)

/** Progress, Phase 1 version: the skip screen, session history, the weight average and active injuries. No charts. */
@Composable
fun ProgressScreen(onInjury: () -> Unit = {}) {
    val data =
        rememberLoad {
            val program = program()
            val history =
                db.session().all().filter { it.completedAt != null }.sortedByDescending { it.startedAt }.take(HISTORY_ROWS).map { s ->
                    HistoryRow(
                        s.localDate,
                        program.dayOrNull(s.programDayId)?.name ?: s.programDayId,
                        s.kind,
                        db.setLog().forSession(s.id).count {
                            !it.isWarmup
                        },
                    )
                }
            val weights = db.bodyLog().all().associate { it.localDate to it.weightKg }
            val today = time.today()
            ProgressData(skipScreen(), history, Nutrition.weightAverage(weights, today), Nutrition.weightChange(weights, today), injuries(), rules)
        }
    ScreenFrame(title = "Progress", onBack = null) {
        val (screen, history, avg, change, injuries, rules) = data ?: return@ScreenFrame

        SubHeading("Skips and adherence")
        val s = screen.stats
        RtbCard {
            Stat("Sessions done this month", "${s.doneThisMonth}")
            Stat("Adherence this month", s.adherence?.let { "${(it * 100).roundToInt()}%" } ?: "–")
            Stat("Skips in the last 7 days", "${s.skipsLast7}")
            Stat("Make-up sessions left", "${screen.makeup.sessionsLeft}")
            Body("Adherence is sessions done ÷ planned days; excused days still count as planned.", muted = true)
        }
        if (screen.banner) AlertBox("Four skips this week. Would a different training time work better?", kind = AlertKind.WARN)
        if (s.skips.isEmpty()) {
            Body("No skips. Nice.", muted = true)
        } else {
            s.skips.forEach { (date, reason) -> ListRow(name = Dates.day(date), meta = Reason.label(reason)) }
        }

        SubHeading("Body weight")
        Body(
            when {
                avg == null -> "Log at least 3 morning weigh-ins in 7 days to see the average."
                change == null -> "7-day average ${formatKg(Math.round(avg * 10) / 10.0)} kg."
                else -> "7-day average ${formatKg(Math.round(avg * 10) / 10.0)} kg, ${if (change >= 0) "up" else "down"} ${formatKg(
                    Math.round(kotlin.math.abs(change) * 10) / 10.0,
                )} kg on the week before."
            },
        )

        SubHeading("Injuries")
        if (injuries.none { it.isActive }) Body("Nothing active.", muted = true)
        injuries.filter { it.isActive }.forEach { i ->
            ListRow(
                name = Injuries.areaName(i, rules),
                meta = if (i.locked) "Locked until you've been checked" else "Phase ${i.phase} of 4",
                metaAccent = true,
                onClick = onInjury,
            )
        }

        SubHeading("Session history")
        if (history.isEmpty()) Body("No sessions yet.", muted = true)
        history.forEach { h ->
            val kind =
                when (h.kind) {
                    SessionKind.MINIMUM -> "minimum"
                    SessionKind.HOME -> "home version"
                    SessionKind.AWAY -> "away"
                    SessionKind.PRE_TRIP -> "pre-trip"
                    SessionKind.DELOAD -> "deload"
                    else -> "full"
                }
            ListRow(name = "${Dates.short(h.date)} · ${h.name}", meta = "${h.sets} sets, $kind")
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Rtb.colors.ink2, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleLarge, color = Rtb.colors.ink)
    }
}

private const val HISTORY_ROWS = 60
