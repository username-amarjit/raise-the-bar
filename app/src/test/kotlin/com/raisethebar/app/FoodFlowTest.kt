package com.raisethebar.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raisethebar.app.data.AppClock
import com.raisethebar.app.data.Meals
import com.raisethebar.app.data.Repo
import com.raisethebar.app.data.addFood
import com.raisethebar.app.data.foodDay
import com.raisethebar.app.data.logMorningWeight
import com.raisethebar.app.data.logUsualMeal
import com.raisethebar.app.data.saveMealAsUsual
import com.raisethebar.app.data.setFoodQty
import com.raisethebar.app.data.updateProfile
import com.raisethebar.app.ui.LocalRepo
import com.raisethebar.app.ui.food.FoodScreen
import com.raisethebar.app.ui.theme.RtbTheme
import com.raisethebar.engine.Diet
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Duration

/** Milestone 12 (section 13.7) on the real data layer. */
@RunWith(AndroidJUnit4::class)
class FoodFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private fun Repo.daysAgo(n: Long) = Repo(db, AppClock(Clock.offset(time.clock, Duration.ofDays(-n))))

    @Test
    fun officeThaliIs770Kcal25gAnd860WithCanteenOn() {
        val repo = TestRepo.createSetUp()
        val thali = runBlocking { repo.db.usualMeal().all() }.first { it.name == "Office thali" }
        runBlocking { repo.logUsualMeal(Meals.LUNCH, thali.id, canteen = false) }
        runBlocking { repo.logUsualMeal(Meals.DINNER, thali.id, canteen = true) }
        val logs = runBlocking { repo.foodDay() }!!.logs
        assertEquals(770.0 to 25.0, logs.first { it.meal == Meals.LUNCH }.let { it.kcal to it.proteinG })
        assertEquals(860.0 to 25.0, logs.first { it.meal == Meals.DINNER }.let { it.kcal to it.proteinG })
    }

    @Test
    fun quickAddStepsAndSavingAUsualMeal() {
        val repo = TestRepo.createSetUp()
        runBlocking {
            repo.addFood(Meals.LUNCH, "roti", false)
            repo.addFood(Meals.LUNCH, "roti", false)
            repo.addFood(Meals.LUNCH, "dal_tadka", true)
        }
        val day = runBlocking { repo.foodDay() }!!
        val roti = day.logs.single { it.foodId == "roti" }
        assertEquals(2.0, roti.qty, 1e-9)
        assertEquals(6.0, roti.proteinG, 1e-9)
        assertEquals(395.0, day.totals.kcal, 1e-9) // 200 + 150 + 45 canteen oil
        val mealId = runBlocking { repo.saveMealAsUsual("My lunch", Meals.LUNCH) }!!
        val saved = runBlocking { repo.db.usualMeal().get(mealId) }!!
        assertEquals(395.0 to 13.0, saved.kcal to saved.proteinG)
        runBlocking { repo.setFoodQty(roti.id, 0.0) }
        assertFalse(runBlocking { repo.foodDay() }!!.logs.any { it.foodId == "roti" })
    }

    @Test
    fun vegHidesEggAndChickenAndEggShowsEggs() {
        val repo = TestRepo.createSetUp()
        val veg = runBlocking { repo.foodDay() }!!.foods.map { it.id }
        assertFalse(veg.any { it in setOf("whole_egg", "egg_whites", "chicken_breast", "chicken_curry") })
        assertTrue("paneer" in veg)
        runBlocking { repo.updateProfile { it.copy(dietType = Diet.EGG) } }
        val egg = runBlocking { repo.foodDay() }!!.foods.map { it.id }
        assertTrue("whole_egg" in egg)
        assertFalse("chicken_curry" in egg)
    }

    @Test
    fun weighInsAverageOnlyFromThreeAndTargetsFollowA2KgChange() {
        val repo = TestRepo.createSetUp()
        runBlocking {
            repo.daysAgo(5).logMorningWeight(70.4)
            repo.daysAgo(2).logMorningWeight(70.6)
        }
        assertNull(runBlocking { repo.foodDay() }!!.average)
        runBlocking { repo.logMorningWeight(70.2) }
        assertEquals(70.4, runBlocking { repo.foodDay() }!!.average!!, 1e-9)
        assertEquals(120, runBlocking { repo.profile() }!!.proteinTargetG)

        // No 7-day average yet, so the first 75 kg weigh-in is the body weight; later ones match the new basis.
        val later = Repo(repo.db, AppClock(Clock.offset(repo.time.clock, Duration.ofDays(10))))
        assertEquals(130 to 2700, runBlocking { later.daysAgo(2).logMorningWeight(75.0) })
        assertNull(runBlocking { later.daysAgo(1).logMorningWeight(75.0) })
        assertNull(runBlocking { later.logMorningWeight(75.0) })
        val p = runBlocking { repo.profile() }!!
        assertEquals(130 to 2700, p.proteinTargetG to p.kcalTarget)
    }

    @Test
    fun theFoodScreenAddsARotiFromAChip() {
        val repo = TestRepo.createSetUp()
        compose.setContent { RtbTheme { CompositionLocalProvider(LocalRepo provides repo) { FoodScreen() } } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("of 120 g protein").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("0 g").performScrollTo()
        compose.onNodeWithText("Roti").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("3 g").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, runBlocking { repo.foodDay() }!!.logs.size)
    }
}
