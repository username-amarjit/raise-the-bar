package com.raisethebar.app.ui.settings

import androidx.compose.runtime.Composable
import com.raisethebar.app.BuildConfig
import com.raisethebar.app.ui.components.Body
import com.raisethebar.app.ui.components.ListRow
import com.raisethebar.app.ui.components.RtbCard
import com.raisethebar.app.ui.components.ScreenFrame
import com.raisethebar.app.ui.components.SectionLabel
import com.raisethebar.app.ui.components.SubHeading
import com.raisethebar.app.ui.setup.EditSection
import com.raisethebar.engine.SeedData

@Composable
fun SettingsScreen(onBack: () -> Unit, onAbout: () -> Unit, onEdit: (String) -> Unit = {}, onGym: () -> Unit = {}) {
    ScreenFrame(title = "Settings", onBack = onBack) {
        SectionLabel("You and your plan")
        ListRow(name = "About you", meta = "Weight, height, age, diet and experience", onClick = { onEdit(EditSection.ABOUT) })
        ListRow(name = "Your goal", meta = "Lean bulk or recomp, protein and calories", onClick = { onEdit(EditSection.GOAL) })
        ListRow(name = "Your week", meta = "Training days, times and check-in notifications", onClick = { onEdit(EditSection.WEEK) })
        ListRow(name = "Your rules", meta = "Day-off cut-off and make-up mode", onClick = { onEdit(EditSection.RULES) })
        ListRow(name = "My Gym", meta = "Equipment, weights and what's broken", onClick = onGym)
        SectionLabel("The app")
        ListRow(name = "About", meta = "Credits and the general-guidance note", onClick = onAbout)
    }
}

/** Settings → About: credits (free-exercise-db, fonts) and the general-guidance note. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    ScreenFrame(title = "About", onBack = onBack, caption = "Raise the Bar ${BuildConfig.VERSION_NAME}") {
        Body(
            "A personal strength app for six days a week of Push, Pull and Legs. It works fully offline: no account, " +
                "no analytics and no network requests. Your data stays on this phone; export it from Settings to keep a backup.",
        )
        SubHeading("Credits")
        RtbCard {
            SectionLabel("Exercise library")
            Body(
                "Exercise photos and instructions come from free-exercise-db by yuhonas " +
                    "(github.com/yuhonas/free-exercise-db), released into the public domain under the Unlicense. " +
                    "Form cues, backups and injury tags were added for this app.",
            )
        }
        RtbCard {
            SectionLabel("Fonts")
            Body("Barlow and Barlow Condensed by Jeremy Tribby, under the SIL Open Font License 1.1. Bundled with the app.")
        }
        RtbCard {
            SectionLabel("Food values")
            Body("Rough averages for Indian portions from the plan's guide. Close enough is the point: the weekly weight trend corrects them.")
        }
        SubHeading("Please note")
        RtbCard {
            Body(SeedData.injuryRules.note)
            Body("Chest pain, dizziness or unusual breathlessness while training: stop and get medical help.")
        }
    }
}
