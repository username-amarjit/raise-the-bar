package com.raisethebar.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raisethebar.app.ui.nav.RaiseTheBarRoot
import com.raisethebar.app.ui.theme.RtbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShellSmokeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun opensTodayInLightMode() {
        compose.setContent { RtbTheme(darkTheme = false) { RaiseTheBarRoot() } }
        compose.onNodeWithText("Push A").assertIsDisplayed()
        compose.onNodeWithText("My Gym").assertIsDisplayed()
    }

    @Test
    fun opensTodayInDarkModeAndSwitchesTabs() {
        compose.setContent { RtbTheme(darkTheme = true) { RaiseTheBarRoot() } }
        compose.onNodeWithText("Push A").assertIsDisplayed()
        compose.onNodeWithText("Food").performClick()
        compose.onNodeWithText("Protein", substring = true).assertIsDisplayed()
    }
}
