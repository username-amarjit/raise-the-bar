package com.raisethebar.app.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raisethebar.app.data.Store
import com.raisethebar.engine.InputRejected
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Inputs are checked against the section 5 limits before anything is written. */
@RunWith(AndroidJUnit4::class)
class StoreValidationTest {
    private lateinit var db: AppDatabase
    private lateinit var store: Store

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        store = Store(db, Samples.clock())
    }

    @After
    fun close() = db.close()

    private suspend fun rejects(block: suspend () -> Unit): InputRejected {
        try {
            block()
        } catch (e: InputRejected) {
            return e
        }
        throw AssertionError("expected the input to be rejected")
    }

    @Test
    fun rejectsOutOfRangeSetsAndWritesNothing() =
        runTest {
            val sessionId = db.session().insert(Samples.session())
            val good = Samples.setLog(sessionId)
            assertEquals("reps", rejects { store.logSet(good.copy(reps = 51)) }.field)
            assertEquals("reps_left", rejects { store.logSet(good.copy(repsLeft = 5)) }.field)
            assertEquals("weight_kg", rejects { store.logSet(good.copy(weightKg = 0.4)) }.field)
            assertEquals("weight_kg", rejects { store.logSet(good.copy(weightKg = 500.5)) }.field)
            assertEquals("seconds", rejects { store.logSet(good.copy(seconds = 4)) }.field)
            assertEquals("pain", rejects { store.logSet(good.copy(pain = 11)) }.field)
            assertTrue(db.setLog().forSession(sessionId).isEmpty())
            store.logSet(good.copy(reps = 50, repsLeft = 4, weightKg = 500.0))
            assertEquals(1, db.setLog().forSession(sessionId).size)
        }

    @Test
    fun rejectsOutOfRangeProfile() =
        runTest {
            val p = Samples.profile()
            assertEquals("body_weight_kg", rejects { store.saveProfile(p.copy(weightKg = 29.9)) }.field)
            assertEquals("body_weight_kg", rejects { store.saveProfile(p.copy(weightKg = 250.1)) }.field)
            assertEquals("height_cm", rejects { store.saveProfile(p.copy(heightCm = 119.0)) }.field)
            assertEquals("height_cm", rejects { store.saveProfile(p.copy(heightCm = 231.0)) }.field)
            assertEquals("waist_cm", rejects { store.saveProfile(p.copy(waistCm = 49.0)) }.field)
            assertEquals("waist_cm", rejects { store.saveProfile(p.copy(waistCm = 201.0)) }.field)
            assertEquals("age", rejects { store.saveProfile(p.copy(age = 15)) }.field)
            assertEquals("age", rejects { store.saveProfile(p.copy(age = 91)) }.field)
            assertEquals("diet_type", rejects { store.saveProfile(p.copy(dietType = "vegan")) }.field)
            assertNull(db.profile().get())
            store.saveProfile(p)
            assertEquals(70.0, db.profile().get()!!.weightKg, 0.0)
        }

    @Test
    fun rejectsOutOfRangeBodyWeightPainAndMinutes() =
        runTest {
            assertEquals("body_weight_kg", rejects { store.logBodyWeight(Samples.day, 251.0) }.field)
            assertTrue(db.bodyLog().all().isEmpty())
            val injuryId = db.injury().insert(Samples.injury())
            assertEquals("pain", rejects { store.logPain(Samples.painLog(injuryId).copy(value = -1)) }.field)
            assertTrue(db.painLog().all().isEmpty())
            assertEquals("minutes", rejects { store.addAbsence(Samples.absence().copy(minutesPerDay = 9)) }.field)
            assertEquals("minutes", rejects { store.addAbsence(Samples.absence().copy(minutesPerDay = 121)) }.field)
            assertEquals("end", rejects { store.addAbsence(Samples.absence().copy(end = Samples.day)) }.field)
            assertTrue(db.absence().all().isEmpty())
        }
}
