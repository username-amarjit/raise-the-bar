package com.raisethebar.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raisethebar.app.ui.theme.Accent
import com.raisethebar.app.ui.theme.Rtb
import com.raisethebar.app.ui.theme.accent
import com.raisethebar.app.ui.theme.accentSoft
import com.raisethebar.engine.Injuries
import com.raisethebar.engine.PainLight

/** A full screen with a back link and a title, scrolling content, and an optional bottom area. */
@Composable
fun ScreenFrame(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    caption: String? = null,
    actions: @Composable () -> Unit = {},
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Rtb.colors
    Column(modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = MinTap)) {
                    Text("‹ Back", style = MaterialTheme.typography.titleSmall, color = c.pull)
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Spacer(Modifier.weight(1f))
            actions()
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (caption != null) Text(caption, style = MaterialTheme.typography.labelMedium, color = c.muted)
            ScreenTitle(title)
            content()
            Spacer(Modifier.size(24.dp))
        }
        if (bottom != null) {
            Column(
                Modifier.fillMaxWidth().background(c.bg).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = bottom,
            )
        }
    }
}

/** A rounded chip. Selected chips are filled ink and marked with a tick, so colour is never the only signal. */
@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, accent: Accent? = null, onClick: (() -> Unit)? = null) {
    val c = Rtb.colors
    val bg =
        when {
            selected && accent != null -> c.accent(accent)
            selected -> c.ink
            accent != null -> c.accentSoft(accent)
            else -> c.surface2
        }
    val fg = if (selected) (if (accent != null) c.onAccent else c.bg) else c.ink
    val shape = RoundedCornerShape(999.dp)
    var m =
        modifier
            .clip(shape)
            .background(bg)
    if (onClick != null) {
        m = m.heightIn(min = MinTap).selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
    }
    Box(m.padding(horizontal = 14.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(
            text = if (selected && onClick != null) "✓ $text" else text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = fg,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        content()
    }
}

/** The big −/+ stepper from the set logger wireframe. */
@Composable
fun Stepper(
    value: String,
    unit: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
    label: String = unit,
) {
    val c = Rtb.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface2)
            .padding(6.dp)
            .semantics(mergeDescendants = false) { contentDescription = "$label $value $unit" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", "Less $label", minusEnabled, onMinus)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.displaySmall, color = c.ink)
            Text(unit, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        StepButton("+", "More $label", plusEnabled, onPlus)
    }
}

@Composable
private fun StepButton(glyph: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Rtb.colors
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) c.surface else c.surface2)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = MaterialTheme.typography.headlineMedium, color = if (enabled) c.ink else c.muted)
    }
}

/** A small inline −/+ quantity control for food portions. */
@Composable
fun QtyControl(qty: String, onMinus: () -> Unit, onPlus: () -> Unit, description: String) {
    val c = Rtb.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        SmallStep("−", "Less $description", onMinus)
        Text(
            qty,
            style = MaterialTheme.typography.titleLarge,
            color = c.ink,
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.Center,
        )
        SmallStep("+", "More $description", onPlus)
    }
}

@Composable
private fun SmallStep(glyph: String, description: String, onClick: () -> Unit) {
    val c = Rtb.colors
    Box(
        Modifier
            .size(MinTap)
            .clip(RoundedCornerShape(8.dp))
            .background(c.surface2)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = MaterialTheme.typography.titleLarge, color = c.ink)
    }
}

/**
 * Segmented buttons, for example the reps-left choice 0, 1, 2, 3, 4+.
 * The selected segment is filled, bold and outlined, and announced as selected.
 */
@Composable
fun Segmented(options: List<String>, selected: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, description: String = "") {
    val c = Rtb.colors
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) c.pull else c.surface)
                    .border(BorderStroke(if (on) 3.dp else 1.dp, if (on) c.ink else c.line), RoundedCornerShape(10.dp))
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(i) })
                    .semantics { stateDescription = if (on) "selected" else "not selected" }
                    .semantics { contentDescription = "$description $label" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold,
                    color = if (on) c.onAccent else c.ink,
                )
            }
        }
    }
}

fun PainLight.label(): String =
    when (this) {
        PainLight.GREEN -> "green: carry on"
        PainLight.AMBER -> "amber: hold"
        PainLight.RED -> "red: stop it"
    }

/** The 0–10 pain scale, tinted green (0–2), amber (3–5) and red (6–10), with the light named in words. */
@Composable
fun PainScale(value: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, sharp: Boolean = false) {
    val c = Rtb.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (0..10).forEach { v ->
                val light = Injuries.painLight(v, false)
                val tint =
                    when (light) {
                        PainLight.GREEN -> c.legsSoft
                        PainLight.AMBER -> c.goldSoft
                        PainLight.RED -> c.pushSoft
                    }
                val on = v == value
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = MinTap)
                        .clip(RoundedCornerShape(4.dp))
                        .background(tint)
                        .border(BorderStroke(if (on) 3.dp else 0.dp, if (on) c.ink else Color.Transparent), RoundedCornerShape(4.dp))
                        .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(v) })
                        .semantics { contentDescription = "Pain $v, ${light.label()}" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$v",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold,
                        color = c.ink,
                    )
                }
            }
        }
        if (value != null) {
            Text("Pain $value: ${Injuries.painLight(value, sharp).label()}", style = MaterialTheme.typography.bodySmall, color = c.ink2)
        }
    }
}

/** A list row in the style of the Today wireframe: name and meta on the left, a big number on the right. */
@Composable
fun ListRow(
    name: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    metaAccent: Boolean = false,
    trailing: String? = null,
    trailingUp: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val c = Rtb.colors
    var m = modifier.fillMaxWidth()
    if (onClick != null) m = m.clickable(role = Role.Button, onClick = onClick)
    Column(m) {
        Row(Modifier.fillMaxWidth().heightIn(min = MinTap).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, color = c.ink)
                if (meta != null) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (metaAccent) c.gold else c.muted,
                        fontWeight = if (metaAccent) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
            if (trailing != null) {
                Text(
                    if (trailingUp) "$trailing ↑" else trailing,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (trailingUp) c.legs else c.ink,
                    modifier = Modifier.semantics { if (trailingUp) contentDescription = "$trailing, weight up" },
                )
            }
        }
        HorizontalDivider(color = c.line)
    }
}

/** A labelled switch row. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, detail: String? = null) {
    val c = Rtb.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = MinTap)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = c.ink)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = c.pull, checkedThumbColor = c.onAccent),
        )
    }
}

/** A number input. Returns raw text; the caller validates it against the section 5 limits. */
@Composable
fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    error: String? = null,
    decimal: Boolean = true,
) {
    val c = Rtb.colors
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimal && it == '.') }.take(6)) },
        label = { Text(label) },
        suffix = suffix?.let { s -> { Text(s) } },
        isError = error != null,
        supportingText = error?.let { e -> { Text(e) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.pull,
                unfocusedBorderColor = c.line,
                focusedLabelColor = c.pull,
            ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** A plain text input. */
@Composable
fun TextInput(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Rtb.colors
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(60)) },
        label = { Text(label) },
        singleLine = true,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.pull,
                unfocusedBorderColor = c.line,
                focusedLabelColor = c.pull,
            ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** A small heading inside a screen. */
@Composable
fun SubHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        color = Rtb.colors.ink,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

/** Body text in the secondary ink colour. */
@Composable
fun Body(text: String, modifier: Modifier = Modifier, muted: Boolean = false) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = if (muted) Rtb.colors.muted else Rtb.colors.ink2, modifier = modifier)
}
