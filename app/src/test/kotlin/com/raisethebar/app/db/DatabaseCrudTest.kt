package com.raisethebar.app.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Creates, reads and updates every table. */
@RunWith(AndroidJUnit4::class)
class DatabaseCrudTest {
    private lateinit var db: AppDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun close() = db.close()

    @Test
    fun profile() =
        runTest {
            db.profile().upsert(Samples.profile())
            assertEquals(70.0, db.profile().get()!!.weightKg, 0.0)
            db.profile().upsert(db.profile().get()!!.copy(weightKg = 71.5, hideInSwitcher = true))
            assertEquals(71.5, db.profile().get()!!.weightKg, 0.0)
            assertTrue(db.profile().get()!!.hideInSwitcher)
        }

    @Test
    fun schedule() =
        runTest {
            db.schedule().upsertAll(Samples.schedule())
            assertEquals(7, db.schedule().all().size)
            db.schedule().upsertAll(listOf(Samples.schedule()[6].copy(isTrainingDay = true, time = "09:00")))
            assertEquals("09:00", db.schedule().all()[6].time)
        }

    @Test
    fun plan() =
        runTest {
            db.plan().upsert(Samples.plan())
            db.plan().upsert(db.plan().get()!!.copy(rotationIndex = 3, previous = "{}"))
            assertEquals(3, db.plan().get()!!.rotationIndex)
            assertEquals(Samples.day, db.plan().get()!!.planStart)
        }

    @Test
    fun equipment() =
        runTest {
            db.equipment().upsert(Samples.equipment())
            db.equipment().upsert(db.equipment().get("dumbbells")!!.copy(status = "broken", brokenSince = Samples.day, missingKg = listOf(7.5)))
            val e = db.equipment().get("dumbbells")!!
            assertEquals("broken", e.status)
            assertEquals(listOf(7.5), e.missingKg)
            assertEquals(listOf(2.5, 5.0, 7.5, 10.0), e.dumbbellPairsKg)
        }

    @Test
    fun exercise() =
        runTest {
            db.exercise().upsertAll(listOf(Samples.exercise()))
            db.exercise().upsertAll(listOf(db.exercise().get("barbell_bench_press")!!.copy(primaryMuscles = listOf("chest"))))
            val e = db.exercise().get("barbell_bench_press")!!
            assertEquals(listOf("chest"), e.primaryMuscles)
            assertEquals(3, e.cues.size)
            assertEquals(1, db.exercise().count())
        }

    @Test
    fun programDayAndSlot() =
        runTest {
            db.program().upsertDays(listOf(Samples.programDay()))
            db.program().upsertSlots(listOf(Samples.slot()))
            db.program().setPromoted("push_a_1", "smith_bench_press")
            val s = db.program().slot("push_a_1")!!
            assertEquals("smith_bench_press", s.promotedExerciseId)
            assertEquals(listOf("barbell_bench_press", "smith_bench_press"), s.options)
            db.program().upsertDays(listOf(db.program().day("push_a")!!.copy(focus = "Chest")))
            assertEquals("Chest", db.program().day("push_a")!!.focus)
            assertEquals(1, db.program().slotsForDay("push_a").size)
        }

    @Test
    fun estimate() =
        runTest {
            db.exercise().upsertAll(listOf(Samples.exercise()))
            db.estimate().upsert(Samples.estimate())
            db.estimate().upsert(db.estimate().get("barbell_bench_press")!!.copy(e1rmKg = 61.5, badDays = 1))
            val e = db.estimate().get("barbell_bench_press")!!
            assertEquals(61.5, e.e1rmKg!!, 0.0)
            assertEquals(1, e.badDays)
        }

    @Test
    fun sessionAndSetLog() =
        runTest {
            val id = db.session().insert(Samples.session())
            assertNotNull(db.session().open())
            val setId = db.setLog().insert(Samples.setLog(id))
            val set = db.setLog().forSession(id).single()
            assertEquals(setId, set.id)
            db.setLog().update(set.copy(reps = 9))
            assertEquals(9, db.setLog().forSession(id).single().reps)
            db.session().update(db.session().get(id)!!.copy(completedAt = Samples.at.plusHours(1)))
            assertNull(db.session().open())
            assertEquals(id, db.session().lastCompleted()!!.id)
            assertEquals(1, db.setLog().completedBetween(Samples.day, Samples.day).size)
        }

    @Test
    fun dayLog() =
        runTest {
            db.dayLog().upsert(Samples.dayLog())
            db.dayLog().upsert(db.dayLog().get(Samples.day)!!.copy(reason = "sick", reasonAt = Samples.at, outcome = "excused"))
            assertEquals("excused", db.dayLog().get(Samples.day)!!.outcome)
        }

    @Test
    fun absence() =
        runTest {
            val id = db.absence().insert(Samples.absence())
            db.absence().update(db.absence().get(id)!!.copy(cancelledAt = Samples.at))
            assertNotNull(db.absence().get(id)!!.cancelledAt)
            assertEquals(1, db.absence().all().size)
        }

    @Test
    fun makeup() =
        runTest {
            db.makeup().upsert(Samples.makeup())
            db.makeup().upsert(db.makeup().get()!!.copy(sessionsLeft = 4, skipsCounted = 2, triggeredOn = Samples.day))
            assertEquals(4, db.makeup().get()!!.sessionsLeft)
        }

    @Test
    fun injuryAndPainLog() =
        runTest {
            val id = db.injury().insert(Samples.injury())
            db.painLog().insert(Samples.painLog(id))
            db.injury().update(db.injury().get(id)!!.copy(phase = 2, greenStreak = 1))
            assertEquals(2, db.injury().get(id)!!.phase)
            assertEquals(1, db.injury().unresolved().size)
            assertEquals(3, db.painLog().forInjury(id).single().value)
        }

    @Test
    fun foodUsualMealFoodLog() =
        runTest {
            db.food().upsertAll(listOf(Samples.food()))
            db.food().upsertAll(listOf(db.food().get("roti")!!.copy(shortName = "Roti")))
            assertEquals("Roti", db.food().get("roti")!!.shortName)
            val mealId = db.usualMeal().insert(Samples.usualMeal())
            db.usualMeal().update(db.usualMeal().get(mealId)!!.copy(proteinG = 26.0))
            assertEquals(26.0, db.usualMeal().get(mealId)!!.proteinG, 0.0)
            val logId = db.foodLog().insert(Samples.foodLog())
            db.foodLog().update(db.foodLog().forDate(Samples.day).single().copy(qty = 3.0))
            assertEquals(logId, db.foodLog().forDate(Samples.day).single().id)
            assertEquals(3.0, db.foodLog().forDate(Samples.day).single().qty, 0.0)
        }

    @Test
    fun bodyLog() =
        runTest {
            db.bodyLog().upsert(Samples.bodyLog())
            db.bodyLog().upsert(Samples.bodyLog().copy(weightKg = 70.4))
            assertEquals(70.4, db.bodyLog().latest()!!.weightKg, 0.0)
            assertEquals(1, db.bodyLog().all().size)
        }

    @Test
    fun meta() =
        runTest {
            db.meta().put(Samples.meta())
            db.meta().put(MetaEntity("install_date", "2026-10-22"))
            assertEquals("2026-10-22", db.meta().get("install_date"))
        }
}
