package com.raisethebar.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raisethebar.app.ui.theme.Accent
import com.raisethebar.app.ui.theme.Rtb
import com.raisethebar.app.ui.theme.accent
import com.raisethebar.app.ui.theme.accentSoft

/** Minimum tap target: 48 dp, above the 44 pt the checklist asks for. */
val MinTap: Dp = 48.dp

/** A surface card with an optional coloured top border for the day type. */
@Composable
fun RtbCard(modifier: Modifier = Modifier, accent: Accent? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Rtb.colors
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(BorderStroke(1.dp, c.line), shape),
    ) {
        if (accent != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(c.accent(accent)),
            )
        }
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** The filled day tag ("Push A", "Make-up mode"). */
@Composable
fun DayTag(text: String, accent: Accent, modifier: Modifier = Modifier) {
    val c = Rtb.colors
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = c.onAccent,
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .background(c.accent(accent))
                .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

/** Small grey label above a group ("Reps left in the tank?"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = Rtb.colors.muted,
        modifier = modifier.semantics { heading() },
    )
}

/** Big screen heading in Barlow Condensed. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge,
        color = Rtb.colors.ink,
        modifier = modifier.semantics { heading() },
    )
}

/** The small grey row at the top of each screen: left and right captions. */
@Composable
fun TopCaption(left: String, right: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(left, style = MaterialTheme.typography.labelMedium, color = Rtb.colors.muted, modifier = Modifier.weight(1f))
        Text(right, style = MaterialTheme.typography.labelMedium, color = Rtb.colors.muted, textAlign = TextAlign.End)
    }
}

/** The ink "Start workout" style button. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Rtb.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(12.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = c.ink,
                contentColor = c.bg,
                disabledContainerColor = c.surface2,
                disabledContentColor = c.muted,
            ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A quieter outlined button for secondary actions. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Rtb.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = MinTap),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, c.line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.ink, disabledContentColor = c.muted),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** A blue text link with a full-size tap target. */
@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = MinTap)) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = Rtb.colors.pull)
    }
}

enum class AlertKind { INFO, WARN, DANGER, GOOD }

/** Tinted message box: gold for changes, red for stop, green for earned or good news. */
@Composable
fun AlertBox(text: String, modifier: Modifier = Modifier, kind: AlertKind = AlertKind.INFO, title: String? = null) {
    val c = Rtb.colors
    val bg: Color =
        when (kind) {
            AlertKind.INFO -> c.surface2
            AlertKind.WARN -> c.accentSoft(Accent.GOLD)
            AlertKind.DANGER -> c.accentSoft(Accent.PUSH)
            AlertKind.GOOD -> c.accentSoft(Accent.LEGS)
        }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, color = c.ink)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.ink)
    }
}
