package com.raisethebar.app.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raisethebar.app.ui.components.RtbCard
import com.raisethebar.app.ui.components.ScreenTitle
import com.raisethebar.app.ui.theme.Rtb

/** Stand-in for a screen whose milestone hasn't been built yet. */
@Composable
fun PendingScreen(title: String, body: String) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle(title)
        RtbCard { Text(body, style = MaterialTheme.typography.bodyMedium, color = Rtb.colors.ink2) }
    }
}
