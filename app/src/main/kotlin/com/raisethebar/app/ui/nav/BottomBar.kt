package com.raisethebar.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raisethebar.app.ui.theme.Rtb

enum class Tab(
    val label: String,
) {
    TODAY("Today"),
    FOOD("Food"),
    PROGRESS("Progress"),
    GYM("My Gym"),
}

/** Bottom bar matching the wireframes: text labels; the active tab is bold, ink-coloured and underlined (not colour alone). */
@Composable
fun RtbBottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val c = Rtb.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(color = c.line)
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            Tab.entries.forEach { tab ->
                val selected = tab == current
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab) })
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .width(28.dp)
                            .height(3.dp)
                            .background(if (selected) c.ink else Color.Transparent),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) c.ink else c.muted,
                    )
                }
            }
        }
    }
}
