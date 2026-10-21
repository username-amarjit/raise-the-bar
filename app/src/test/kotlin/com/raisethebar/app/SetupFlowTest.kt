package com.raisethebar.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raisethebar.app.ui.LocalRepo
import com.raisethebar.app.ui.nav.RaiseTheBarRoot
import com.raisethebar.app.ui.theme.RtbTheme
import com.raisethebar.engine.Goal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Milestone 4: a fresh install finishes setup, and Today shows calibration week 1, Push A. */
@RunWith(AndroidJUnit4::class)
class SetupFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private fun waitForText(text: String, substring: Boolean = false) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun type(label: String, value: String) = compose.onNode(hasText(label) and hasSetTextAction()).performScrollTo().performTextInput(value)

    private fun next(label: String = "Next") = compose.onNodeWithText(label).performClick()

    @Test
    fun freshInstallFinishesSetupAndTodayShowsCalibrationWeek1PushA() {
        val repo = TestRepo.create()
        compose.setContent { RtbTheme { CompositionLocalProvider(LocalRepo provides repo) { RaiseTheBarRoot() } } }

        waitForText("Setup, step 1 of 5")
        type("Body weight", "70")
        type("Height", "175")
        type("Age", "30")
        next()

        waitForText("Setup, step 2 of 5")
        type("Waist, at the belly button", "85")
        waitForText("lean bulk is suggested", substring = true)
        compose.onNode(hasText("Protein target") and hasText("120")).performScrollTo()
        next()

        waitForText("Setup, step 3 of 5")
        compose.onNodeWithText("Check-in notifications").performScrollTo().performClick()
        next()
        waitForText("Setup, step 4 of 5")
        next()
        waitForText("Setup, step 5 of 5")
        next("Finish setup")

        waitForText("Calibration, week 1", substring = true)
        compose.onNodeWithText("Push A").performScrollTo()
        compose.onNodeWithText("Chest focus").performScrollTo()
        compose.onNodeWithText("Barbell bench press").performScrollTo()

        val profile = runBlocking { repo.profile() }!!
        assertEquals(120, profile.proteinTargetG)
        assertEquals(2500, profile.kcalTarget)
        assertEquals(Goal.LEAN_BULK, profile.goal)
        assertEquals(false, profile.notificationsOn)
        assertTrue(runBlocking { repo.isSetupDone() })
        assertEquals(7, runBlocking { repo.schedule() }.size)
    }
}
