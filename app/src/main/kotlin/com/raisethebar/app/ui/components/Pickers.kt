package com.raisethebar.app.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.raisethebar.app.data.Dates
import com.raisethebar.engine.Time
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** A button showing a time (24-hour) that opens a time picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeButton(time: LocalTime, onChange: (LocalTime) -> Unit, label: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    SecondaryButton(text = Time.hhmm(time), onClick = { open = true }, enabled = enabled, modifier = modifier.heightIn(min = MinTap))
    if (open) {
        val state = rememberTimePickerState(time.hour, time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    onChange(LocalTime.of(state.hour, state.minute))
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}

/** A button showing a date that opens a date picker limited to [from]..[to]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateButton(date: LocalDate, onChange: (LocalDate) -> Unit, from: LocalDate, to: LocalDate, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    SecondaryButton(text = Dates.short(date), onClick = { open = true }, modifier = modifier)
    if (open) {
        val utc = { d: LocalDate -> d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis = utc(date),
                selectableDates =
                    object : androidx.compose.material3.SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                            val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                            return !d.isBefore(from) && !d.isAfter(to)
                        }
                    },
            )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

/** A row with a label and small −/+ buttons for a whole number inside [range]. */
@Composable
fun IntStepperRow(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit, detail: String? = null, unit: String = "") {
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(label, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
            if (detail != null) Body(detail, muted = true)
        }
        QtyControl(
            qty = "$value",
            onMinus = { if (value > range.first) onChange(value - 1) },
            onPlus = { if (value < range.last) onChange(value + 1) },
            description = if (unit.isEmpty()) label else "$label in $unit",
        )
    }
}
