package com.raisethebar.app.ui.setup

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raisethebar.app.data.OldInjury
import com.raisethebar.app.data.completeSetup
import com.raisethebar.app.ui.components.PrimaryButton
import com.raisethebar.app.ui.components.ScreenFrame
import com.raisethebar.app.ui.rememberActions

private val STEPS = listOf("About you", "Your goal", "Your week", "My Gym", "Your rules")

/** First launch (milestone 4): about you, your goal, your week, My Gym and your rules. */
@Composable
fun SetupScreen(onDone: (hurting: List<OldInjury>) -> Unit) {
    val d: SetupDraft = viewModel()
    val actions = rememberActions()
    var step by rememberSaveable { mutableIntStateOf(0) }
    val askNotifications = rememberNotificationRequest { granted -> d.notifications = granted }
    val valid =
        when (step) {
            0 -> d.aboutValid()
            1 -> d.goalValid()
            2 -> d.weekValid()
            else -> true
        }
    ScreenFrame(
        title = STEPS[step],
        caption = "Setup, step ${step + 1} of ${STEPS.size}",
        onBack = if (step > 0) ({ step-- }) else null,
        bottom = {
            PrimaryButton(
                text = if (step == STEPS.lastIndex) "Finish setup" else "Next",
                enabled = valid,
                onClick = {
                    if (step == 2 && d.notifications) askNotifications()
                    if (step < STEPS.lastIndex) {
                        step++
                    } else {
                        actions.run { onDone(completeSetup(d.toInput())) }
                    }
                },
            )
        },
    ) {
        when (step) {
            0 -> AboutYouForm(d)
            1 -> GoalForm(d)
            2 -> WeekForm(d, onNotificationsWanted = askNotifications)
            3 -> GymForm(d.equipment)
            else -> RulesForm(d)
        }
    }
}

/** Asks for the notification permission on Android 13+; earlier versions don't need it. */
@Composable
fun rememberNotificationRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onResult(true)
        }
    }
}
