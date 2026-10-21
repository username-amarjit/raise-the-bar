package com.raisethebar.app.ui.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raisethebar.app.data.rebuildCheckins
import com.raisethebar.app.data.saveRules
import com.raisethebar.app.data.saveSchedule
import com.raisethebar.app.data.updateProfile
import com.raisethebar.app.ui.LocalRepo
import com.raisethebar.app.ui.components.PrimaryButton
import com.raisethebar.app.ui.components.ScreenFrame
import com.raisethebar.app.ui.rememberActions

object EditSection {
    const val ABOUT = "about"
    const val GOAL = "goal"
    const val WEEK = "week"
    const val RULES = "rules"

    fun title(s: String) =
        when (s) {
            ABOUT -> "About you"
            GOAL -> "Your goal"
            WEEK -> "Your week"
            else -> "Your rules"
        }
}

/** Settings: edit one setup section and save it. */
@Composable
fun EditScreen(section: String, onBack: () -> Unit) {
    val d: SetupDraft = viewModel(key = "edit-$section")
    val repo = LocalRepo.current
    val actions = rememberActions()
    LaunchedEffect(Unit) { if (!d.loaded) d.load(repo) }
    val askNotifications = rememberNotificationRequest { granted -> d.notifications = granted }
    val valid =
        d.loaded &&
            when (section) {
                EditSection.ABOUT -> d.aboutValid()
                EditSection.GOAL -> d.goalValid()
                EditSection.WEEK -> d.weekValid()
                else -> true
            }
    ScreenFrame(
        title = EditSection.title(section),
        onBack = onBack,
        caption = "Settings",
        bottom = {
            PrimaryButton("Save", enabled = valid, onClick = {
                actions.run(then = onBack) {
                    when (section) {
                        EditSection.ABOUT ->
                            updateProfile {
                                it.copy(
                                    weightKg = d.weightKg!!,
                                    heightCm = d.heightCm!!,
                                    age = d.ageYears!!,
                                    dietType = d.diet,
                                    experience = d.experience,
                                )
                            }
                        EditSection.GOAL ->
                            updateProfile {
                                it.copy(
                                    waistCm = d.waistCm,
                                    heightCm = d.heightCm!!,
                                    goal = d.goal,
                                    proteinTargetG = d.proteinG!!,
                                    kcalTarget = d.kcalTarget!!,
                                    targetsBasisKg = it.weightKg,
                                )
                            }
                        EditSection.WEEK -> {
                            saveSchedule(d.schedule.toList())
                            updateProfile { it.copy(notificationsOn = d.notifications) }
                            rebuildCheckins()
                        }
                        else -> saveRules(d.cutoff, d.makeup)
                    }
                }
            })
        },
    ) {
        if (!d.loaded) return@ScreenFrame
        when (section) {
            EditSection.ABOUT -> AboutYouForm(d, showInjuries = false)
            EditSection.GOAL -> GoalForm(d)
            EditSection.WEEK -> WeekForm(d, onNotificationsWanted = askNotifications)
            else -> RulesForm(d)
        }
    }
}
