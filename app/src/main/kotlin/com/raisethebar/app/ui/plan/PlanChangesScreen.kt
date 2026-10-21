package com.raisethebar.app.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raisethebar.app.data.AwayDraft
import com.raisethebar.app.data.AwayPreview
import com.raisethebar.app.data.Dates
import com.raisethebar.app.data.activeChanges
import com.raisethebar.app.data.checkDayOff
import com.raisethebar.app.data.dayOffCutoff
import com.raisethebar.app.data.declareDayOff
import com.raisethebar.app.data.previewAway
import com.raisethebar.app.data.saveAway
import com.raisethebar.app.data.undoChange
import com.raisethebar.app.ui.components.AlertBox
import com.raisethebar.app.ui.components.AlertKind
import com.raisethebar.app.ui.components.Body
import com.raisethebar.app.ui.components.Chip
import com.raisethebar.app.ui.components.ChipFlow
import com.raisethebar.app.ui.components.DateButton
import com.raisethebar.app.ui.components.IntStepperRow
import com.raisethebar.app.ui.components.ListRow
import com.raisethebar.app.ui.components.PrimaryButton
import com.raisethebar.app.ui.components.RtbCard
import com.raisethebar.app.ui.components.ScreenFrame
import com.raisethebar.app.ui.components.SecondaryButton
import com.raisethebar.app.ui.components.SectionLabel
import com.raisethebar.app.ui.components.SubHeading
import com.raisethebar.app.ui.rememberActions
import com.raisethebar.app.ui.rememberLoad
import com.raisethebar.config.Rules
import com.raisethebar.engine.AbsenceKind
import com.raisethebar.engine.AwayEquipment
import com.raisethebar.engine.DayStatus
import com.raisethebar.engine.Days
import com.raisethebar.engine.Skips
import com.raisethebar.engine.Time
import java.time.LocalDate

private val EQUIPMENT =
    listOf(
        AwayEquipment.NONE to "No time",
        AwayEquipment.BODYWEIGHT to "Bodyweight or backpack",
        AwayEquipment.DUMBBELLS to "Dumbbells",
        AwayEquipment.GYM to "A gym",
    )

/** Plan changes (milestone 10): one day off and away mode, with a preview and undo. */
@Composable
fun PlanChangesScreen(onBack: () -> Unit) {
    ScreenFrame(title = "Plan changes", onBack = onBack) {
        DayOff()
        Away()
        Changes()
    }
}

@Composable
private fun DayOff() {
    val actions = rememberActions()
    val info =
        rememberLoad {
            val today = time.today()
            val schedule = schedule()
            val absences = absences()
            val program = program()
            val days = (1L..7L).map { today.plusDays(it) }.filter { Days.status(it, schedule, absences, program) == DayStatus.Planned }
            Triple(days, dayOffCutoff(), Skips.daysOffInMonth(absences, java.time.YearMonth.from(today)))
        }
    var picked by remember { mutableStateOf<LocalDate?>(null) }
    var check by remember { mutableStateOf<Skips.DayOffCheck?>(null) }
    SubHeading("One day off")
    RtbCard {
        val (days, cutoff, _) = info ?: return@RtbCard
        Body("Declare it by ${Time.hhmm(cutoff)} the night before. Up to ${Rules.DAY_OFF_PER_MONTH} a month; the rotation just waits.", muted = true)
        ChipFlow {
            days.forEach { d ->
                Chip(Dates.short(d), selected = d == picked, onClick = {
                    picked = d
                    actions.run { check = checkDayOff(d) }
                })
            }
        }
        val d = picked ?: return@RtbCard
        when (check) {
            Skips.DayOffCheck.OK ->
                PrimaryButton("Take ${Dates.weekday(d)} off", onClick = {
                    actions.run {
                        declareDayOff(d, acceptAsSkip = false)
                        picked = null
                        actions.message("${Dates.day(d)} is a day off. No skip counted.")
                    }
                })
            Skips.DayOffCheck.TOO_LATE -> AlertBox("Too late: the cut-off was ${Time.hhmm(cutoff)} the night before.", kind = AlertKind.WARN)
            Skips.DayOffCheck.THIRD_THIS_MONTH -> {
                AlertBox("This would be your third day off this month, so it counts as an unexcused skip.", kind = AlertKind.WARN)
                SecondaryButton("Save it anyway, as a skip", onClick = {
                    actions.run {
                        declareDayOff(d, acceptAsSkip = true)
                        picked = null
                    }
                })
            }
            Skips.DayOffCheck.NOT_PLANNED -> Body("That isn't a planned training day.", muted = true)
            null -> Unit
        }
    }
}

@Composable
private fun Away() {
    val actions = rememberActions()
    val today = rememberLoad { time.today() } ?: return
    var start by remember(today) { mutableStateOf(today.plusDays(1)) }
    var end by remember(today) { mutableStateOf(today.plusDays(4)) }
    var equipment by remember { mutableStateOf(AwayEquipment.DUMBBELLS) }
    var minutes by remember { mutableStateOf(Rules.AWAY_MINUTES_DEFAULT) }
    var preview by remember { mutableStateOf<AwayPreview?>(null) }
    val draft = AwayDraft(start, if (end.isBefore(start)) start else end, equipment, minutes)
    SubHeading("Away mode")
    RtbCard {
        val p = preview
        if (p == null) {
            SectionLabel("Dates")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateButton(start, { start = it }, from = today, to = today.plusDays(60), modifier = Modifier.weight(1f))
                DateButton(draft.end, { end = it }, from = start, to = start.plusDays(60), modifier = Modifier.weight(1f))
            }
            SectionLabel("What you'll have there")
            ChipFlow { EQUIPMENT.forEach { (id, label) -> Chip(label, selected = equipment == id, onClick = { equipment = id }) } }
            if (equipment == AwayEquipment.BODYWEIGHT || equipment == AwayEquipment.DUMBBELLS) {
                IntStepperRow("Minutes a day", minutes, Rules.AWAY_MINUTES_RANGE, {
                    minutes = it
                }, detail = "${Rules.AWAY_MINUTES_RANGE.first} to ${Rules.AWAY_MINUTES_RANGE.last}")
            }
            PrimaryButton("Preview", onClick = { actions.run { preview = previewAway(draft) } })
        } else {
            SectionLabel("Away: ${Dates.short(draft.start)} to ${Dates.short(draft.end)}")
            p.lines.forEach { line -> ListRow(name = Dates.weekday(line.date) + " " + Dates.short(line.date).substringAfter(' '), meta = line.text) }
            AlertBox(
                if (p.countsSkips) {
                    "A gym there keeps the normal plan, so skips count as usual."
                } else {
                    "No skips counted, so make-up mode won't start."
                },
                kind = if (p.countsSkips) AlertKind.WARN else AlertKind.GOOD,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Edit", onClick = { preview = null }, modifier = Modifier.weight(1f))
                PrimaryButton("Confirm", onClick = {
                    actions.run {
                        saveAway(draft)
                        preview = null
                        actions.message("Away mode saved. You can undo it below.")
                    }
                }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Changes() {
    val actions = rememberActions()
    val changes = rememberLoad { activeChanges() to time.today() } ?: return
    val (list, today) = changes
    if (list.isEmpty()) return
    SubHeading("Your changes")
    list.forEach { a ->
        val what =
            if (a.kind ==
                AbsenceKind.DAY_OFF
            ) {
                "Day off"
            } else {
                "Away, ${EQUIPMENT.firstOrNull { it.first == a.equipmentThere }?.second?.lowercase()}"
            }
        val dates = if (a.start == a.end) Dates.short(a.start) else "${Dates.short(a.start)} to ${Dates.short(a.end)}"
        RtbCard {
            ListRow(name = what + (if (a.countsAsSkip) " (counts as a skip)" else ""), meta = dates)
            val how =
                if (today.isBefore(a.start)) {
                    "Undo restores the plan exactly."
                } else {
                    "Undo ends it today; training resumes tomorrow."
                }
            Body(how, muted = true)
            SecondaryButton("Undo", onClick = { actions.run { undoChange(a.id) } })
        }
    }
}
