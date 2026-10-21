package com.raisethebar.app.ui.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.raisethebar.app.data.Dates
import com.raisethebar.app.data.recordReason
import com.raisethebar.app.ui.components.AlertBox
import com.raisethebar.app.ui.components.AlertKind
import com.raisethebar.app.ui.components.Body
import com.raisethebar.app.ui.components.PrimaryButton
import com.raisethebar.app.ui.components.RtbCard
import com.raisethebar.app.ui.components.ScreenFrame
import com.raisethebar.app.ui.components.SecondaryButton
import com.raisethebar.app.ui.components.TimeButton
import com.raisethebar.app.ui.rememberActions
import com.raisethebar.app.ui.rememberLoad
import com.raisethebar.app.ui.theme.Rtb
import com.raisethebar.engine.DayStatus
import com.raisethebar.engine.Reason
import com.raisethebar.engine.SeedData
import com.raisethebar.engine.SessionKind
import com.raisethebar.engine.Time
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * The check-in sheet (milestone 8): going later, minimum session, gym closed, sick, injured or skipping today.
 * Reasons only count before that day's midnight.
 */
@Composable
fun CheckinScreen(date: String, onBack: () -> Unit, onStart: (kind: String) -> Unit, onInjured: (date: String) -> Unit) {
    val day = LocalDate.parse(date)
    val actions = rememberActions()
    val info = rememberLoad(date) { Triple(time.today(), dayStatus(day), db.dayLog().get(day)?.reason) }
    var later by remember { mutableStateOf<LocalTime?>(null) }
    var sick by remember { mutableStateOf(false) }
    val record = { reason: String, time: LocalTime?, then: () -> Unit ->
        actions.run {
            if (recordReason(day, reason, time)) then() else actions.message("That day is over: reasons have to come before midnight.")
        }
    }
    ScreenFrame(title = "What's going on today?", caption = "Check-in · ${Dates.day(day)}", onBack = onBack) {
        val (today, status, reason) = info ?: return@ScreenFrame
        if (day != today) {
            AlertBox(
                "That day is over. Reasons have to be given before midnight, so a day with nothing logged counted as a skip.",
                kind = AlertKind.WARN,
            )
            return@ScreenFrame
        }
        if (status != DayStatus.Planned) {
            Body("Today isn't a planned training day, so there's nothing to check in.")
            return@ScreenFrame
        }
        reason?.let { AlertBox("Recorded so far: ${Reason.label(it).lowercase()}.") }
        Body("No workout has started yet. Tell the app what's happening; there's no penalty for a real reason.", muted = true)
        Option("Going later", "Pick a new time and the check-in moves with you.") {
            val t = later
            if (t == null) {
                SecondaryButton("Pick a new time", onClick = {
                    later =
                        LocalTime.now(actions.repo.time.clock).plusHours(1).truncatedTo(ChronoUnit.HOURS)
                })
            } else {
                TimeButton(t, { later = it }, label = "New training time")
                PrimaryButton("Save: going at ${Time.hhmm(t)}", onClick = { record(Reason.GOING_LATER, t, onBack) })
            }
        }
        Option("Minimum session", "The first 3 exercises, 2 sets each. It counts as done.") {
            SecondaryButton("Start the minimum session", onClick = { onStart(SessionKind.MINIMUM) })
        }
        Option("Gym closed", "Do the 30-minute home version instead. It counts as done.") {
            SecondaryButton("Start the home version", onClick = { record(Reason.GYM_CLOSED, null) { onStart(SessionKind.HOME) } })
        }
        Option("Sick", "Excused, and make-up mode pauses.") {
            if (!sick) {
                SecondaryButton("I'm sick", onClick = { sick = true })
            } else {
                AlertBox(SeedData.injuryRules.sickRule, kind = AlertKind.INFO)
                PrimaryButton("Rest today: excuse it", onClick = { record(Reason.SICK, null, onBack) })
                SecondaryButton("Train lighter: minimum session", onClick = { onStart(SessionKind.MINIMUM) })
            }
        }
        Option("Injured", "Opens injury mode. Today is excused.") {
            SecondaryButton("Something hurts", onClick = { record(Reason.INJURED, null) { onInjured(date) } })
        }
        Option("Skipping today", "No reason needed, but it counts as a skip at midnight.") {
            SecondaryButton("Skip today", onClick = { record(Reason.SKIPPING, null, onBack) })
        }
    }
}

@Composable
private fun Option(title: String, detail: String, content: @Composable () -> Unit) {
    RtbCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Rtb.colors.ink)
            Body(detail, muted = true)
            content()
        }
    }
}
