package com.raisethebar.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import com.raisethebar.app.ui.components.DayTag
import com.raisethebar.app.ui.components.PrimaryButton
import com.raisethebar.app.ui.components.RtbCard
import com.raisethebar.app.ui.components.ScreenTitle
import com.raisethebar.app.ui.components.TopCaption
import com.raisethebar.app.ui.theme.Accent
import com.raisethebar.app.ui.theme.Rtb
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Milestone 0 placeholder for the Today screen. */
@Composable
fun TodayScreen() {
    val locale = LocalLocale.current.platformLocale
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TopCaption(left = date, right = "Phase 1")
        DayTag(text = "Push A", accent = Accent.PUSH)
        ScreenTitle("Today")
        RtbCard(accent = Accent.PUSH) {
            Text(
                "Your workout will appear here once setup is done.",
                style = MaterialTheme.typography.bodyLarge,
                color = Rtb.colors.ink2,
            )
        }
        PrimaryButton(text = "Start workout", onClick = {}, enabled = false)
    }
}
