package com.raisethebar.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raisethebar.app.ui.LocalRepo
import com.raisethebar.app.ui.exercise.ExerciseScreen
import com.raisethebar.app.ui.settings.AboutScreen
import com.raisethebar.app.ui.theme.RtbTheme
import com.raisethebar.engine.SeedData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Milestone 2: the bundled photos exist (so the page works offline), and the exercise page shows what it should. */
@RunWith(AndroidJUnit4::class)
class ExerciseLibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun everyListedPhotoIsBundledAndTheTotalIsUnder15Mb() {
        val assets = File("src/main/assets")
        val paths = SeedData.exercises.flatMap { it.images }
        assertTrue(paths.size > 150)
        val missing = paths.filterNot { File(assets, it).isFile }
        assertTrue("missing photos: $missing", missing.isEmpty())
        val total = paths.distinct().sumOf { File(assets, it).length() }
        assertTrue("photos take $total bytes", total < 15L * 1024 * 1024)
    }

    @Test
    fun benchPressPageShowsCuesMusclesAndBackupsInOrderAndCanPromoteOne() {
        val repo = TestRepo.create()
        compose.setContent {
            RtbTheme { CompositionLocalProvider(LocalRepo provides repo) { ExerciseScreen("barbell_bench_press", "push_a_1", {}, {}) } }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Barbell bench press").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Push A, slot 1 · Saved offline").assertIsDisplayed()
        listOf("Shoulder blades squeezed and down", "Bar to mid-chest, elbows at about 45°", "Feet planted, hips on the bench").forEach {
            compose.onNodeWithText("•  $it").performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("Counts toward: Chest.").performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithText("Smith machine bench press, then dumbbell bench press, then chest press machine")
            .performScrollTo()
            .assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Watch video", substring = true).fetchSemanticsNodes().isEmpty())

        compose.onNodeWithText("Make smith machine bench press my first choice").performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { repo.db.program().slot("push_a_1")?.promotedExerciseId } != null }
        assertEquals("smith_bench_press", runBlocking { repo.db.program().slot("push_a_1")?.promotedExerciseId })
    }

    @Test
    fun aboutCreditsFreeExerciseDb() {
        compose.setContent { RtbTheme { AboutScreen(onBack = {}) } }
        compose.onNodeWithText("free-exercise-db", substring = true).performScrollTo().assertIsDisplayed()
    }
}
