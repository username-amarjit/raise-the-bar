package com.raisethebar.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raisethebar.app.data.AppClock
import com.raisethebar.app.data.Repo
import com.raisethebar.app.data.buildPlan
import com.raisethebar.app.data.fixedPrompts
import com.raisethebar.app.data.markFixedAsked
import com.raisethebar.app.data.setEquipmentStatus
import com.raisethebar.app.ui.LocalRepo
import com.raisethebar.app.ui.gym.EquipmentScreen
import com.raisethebar.app.ui.theme.RtbTheme
import com.raisethebar.engine.EquipmentStatus
import com.raisethebar.engine.SessionKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Duration

/** Milestone 5: broken machines swap in backups and come back when fixed; busy, report it and the fixed-yet prompt. */
@RunWith(AndroidJUnit4::class)
class GymFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private fun Repo.legsASlot2(): Pair<String, String?> =
        runBlocking {
            val pe =
                buildPlan(program().day("legs_a"), SessionKind.FULL, time.today(), withMakeup = false).exercises.first {
                    it.slotId == "legs_a_2"
                }
            pe.exerciseId to pe.note
        }

    @Test
    fun legPressBrokenUsesHackSquatThenLungesAndFixedRestoresIt() {
        val repo = TestRepo.createSetUp()
        assertEquals("leg_press", repo.legsASlot2().first)
        runBlocking { repo.setEquipmentStatus("leg_press", EquipmentStatus.BROKEN) }
        assertEquals("hack_squat" to "Leg press broken: backup 1", repo.legsASlot2())
        runBlocking { repo.setEquipmentStatus("hack_squat", EquipmentStatus.GONE) }
        assertEquals("walking_lunges", repo.legsASlot2().first)
        runBlocking { repo.setEquipmentStatus("leg_press", EquipmentStatus.WORKING) }
        assertEquals("leg_press", repo.legsASlot2().first)
    }

    @Test
    fun busyLastsForTodayAndBrokenAsksEveryThreeDays() {
        val repo = TestRepo.createSetUp()
        runBlocking { repo.setEquipmentStatus("leg_press", EquipmentStatus.BUSY) }
        assertEquals("hack_squat", repo.legsASlot2().first)
        val tomorrow = Repo(repo.db, AppClock(Clock.offset(repo.time.clock, Duration.ofDays(1))))
        assertEquals("leg_press", tomorrow.legsASlot2().first)

        runBlocking { repo.setEquipmentStatus("leg_press", EquipmentStatus.BROKEN) }
        assertTrue(runBlocking { repo.fixedPrompts() }.isEmpty())
        val later = Repo(repo.db, AppClock(Clock.offset(repo.time.clock, Duration.ofDays(3))))
        assertEquals(listOf("leg_press"), runBlocking { later.fixedPrompts() }.map { it.id })
        runBlocking { later.markFixedAsked("leg_press") }
        assertTrue(runBlocking { later.fixedPrompts() }.isEmpty())
    }

    @Test
    fun equipmentScreenMarksBrokenAndShowsTheReportMessage() {
        val repo = TestRepo.createSetUp()
        compose.setContent { RtbTheme { CompositionLocalProvider(LocalRepo provides repo) { EquipmentScreen("leg_press", {}, {}) } } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Broken").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Broken").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("out of order since", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose
            .onNodeWithText(
                "Hi, the leg press in the office gym has been out of order since Wednesday 21 October. Could someone take a look? Thanks.",
            )
            .performScrollTo()
        assertEquals(EquipmentStatus.BROKEN, runBlocking { repo.db.equipment().get("leg_press") }?.status)
    }
}
