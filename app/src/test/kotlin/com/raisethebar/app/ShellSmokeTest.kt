package com.raisethebar.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raisethebar.app.ui.LocalRepo
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
    fun opensInLightModeWithTheFourTabs() {
        val repo = TestRepo.createSetUp()
        compose.setContent { RtbTheme(darkTheme = false) { CompositionLocalProvider(LocalRepo provides repo) { RaiseTheBarRoot() } } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("My Gym").fetchSemanticsNodes().isNotEmpty() }
        listOf("Food", "Progress", "My Gym").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun opensInDarkModeAndSwitchesTabs() {
        val repo = TestRepo.createSetUp()
        compose.setContent { RtbTheme(darkTheme = true) { CompositionLocalProvider(LocalRepo provides repo) { RaiseTheBarRoot() } } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Food").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Food").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("of 120 g protein").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("of 120 g protein").assertIsDisplayed()
    }
}
