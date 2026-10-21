package com.raisethebar.app.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Builds a database exactly as schema version 1 shipped it (from the exported Room schema), fills every table,
 * then opens it with the current app version so Room runs every numbered migration and validates the result.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @Before
    fun clean() {
        context.deleteDatabase(name)
    }

    @Test
    fun migrationsAreNumberedWithoutGaps() {
        val steps = ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals((1 until AppDatabase.VERSION).map { it to it + 1 }, steps)
    }

    @Test
    fun upgradingFromVersion1KeepsAllData() =
        runTest {
            createAt(1).use { v1 -> fillVersion1(v1) }

            val db =
                Room
                    .databaseBuilder(context, AppDatabase::class.java, name)
                    .addMigrations(*ALL_MIGRATIONS)
                    .allowMainThreadQueries()
                    .build()
            try {
                val p = db.profile().get()!!
                assertEquals(70.0, p.weightKg, 0.0)
                assertFalse(p.hideInSwitcher)
                assertNull(p.targetsBasisKg)
                assertEquals(7, db.schedule().all().size)
                assertEquals(2, db.plan().get()!!.rotationIndex)
                assertEquals(listOf(2.5, 5.0), db.equipment().get("dumbbells")!!.dumbbellPairsKg)
                val ex = db.exercise().get("barbell_bench_press")!!
                assertEquals(listOf("chest"), ex.countsToward)
                assertEquals(emptyList<String>(), ex.primaryMuscles)
                assertEquals("upper", ex.bodyRegion)
                assertEquals("none", ex.loadKind)
                assertNull(ex.sourceId)
                assertEquals("", db.equipment().get("dumbbells")!!.section)
                assertEquals("rotation", db.program().day("push_a")!!.kind)
                assertEquals(listOf("barbell_bench_press", "smith_bench_press"), db.program().slot("push_a_1")!!.options)
                assertEquals(60.0, db.estimate().get("barbell_bench_press")!!.e1rmKg!!, 0.0)
                assertEquals(0, db.estimate().get("barbell_bench_press")!!.holdOffsetS)
                val session = db.session().get(1)!!
                assertEquals(Samples.day, session.localDate)
                assertFalse(session.abandoned)
                val set = db.setLog().forSession(1).single()
                assertEquals(9, set.reps)
                assertNull(set.bwKg)
                assertEquals("sick", db.dayLog().get(Samples.day)!!.reason)
                assertFalse(db.absence().all().single().countsAsSkip)
                assertEquals(4, db.makeup().get()!!.sessionsLeft)
                assertEquals("shoulder", db.injury().get(1)!!.area)
                assertEquals(3, db.painLog().forInjury(1).single().value)
                assertEquals(100.0, db.food().get("roti")!!.kcal, 0.0)
                assertEquals("Shake", db.usualMeal().all().single().name)
                assertTrue(db.foodLog().forDate(Samples.day).single().canteen)
                assertEquals(70.2, db.bodyLog().latest()!!.weightKg, 0.0)
                assertEquals("2026-10-21", db.meta().get("install_date"))
            } finally {
                db.close()
            }
        }

    private fun schema(version: Int): JsonObject {
        val file = File("schemas/com.raisethebar.app.db.AppDatabase/$version.json")
        return Json.parseToJsonElement(file.readText()).jsonObject["database"]!!.jsonObject
    }

    private fun createAt(version: Int): SQLiteDatabase {
        val s = schema(version)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        s["entities"]!!.jsonArray.forEach { e ->
            val o = e.jsonObject
            val table = o["tableName"]!!.jsonPrimitive.content
            db.execSQL(o["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            o["indices"]?.jsonArray?.forEach { i ->
                db.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        s["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
        db.version = version
        return db
    }

    private fun insert(db: SQLiteDatabase, table: String, row: Map<String, Any?>) {
        val cols = row.keys.joinToString(", ") { "`$it`" }
        val marks = row.keys.joinToString(", ") { "?" }
        db.execSQL("INSERT INTO `$table` ($cols) VALUES ($marks)", row.values.toTypedArray())
    }

    private fun fillVersion1(db: SQLiteDatabase) {
        val day = "2026-10-21"
        val at = "2026-10-21T07:30:00.000+05:30"
        insert(
            db,
            "profile",
            mapOf(
                "id" to 1,
                "weight_kg" to 70.0,
                "height_cm" to 175.0,
                "waist_cm" to 85.0,
                "age" to 30,
                "diet_type" to "veg",
                "experience" to "beginner",
                "goal" to "lean_bulk",
                "protein_target_g" to 120,
                "kcal_target" to 2500,
                "day_off_cutoff" to "22:00",
                "makeup_settings" to "{}",
                "notifications_on" to 1,
            ),
        )
        (0..6).forEach { insert(db, "schedule", mapOf("weekday" to it, "is_training_day" to if (it < 6) 1 else 0, "time" to "07:30")) }
        insert(db, "plan", mapOf("id" to 1, "template" to "ppl6", "plan_start" to day, "rotation_index" to 2, "previous" to null))
        insert(
            db,
            "equipment",
            mapOf(
                "id" to "dumbbells",
                "name" to "Dumbbells",
                "kind" to "dumbbell",
                "status" to "working",
                "status_date" to null,
                "broken_since" to null,
                "bar_kg" to null,
                "plate_pairs_kg" to "[]",
                "dumbbell_pairs_kg" to "[2.5,5.0]",
                "stack_min" to null,
                "stack_max" to null,
                "stack_step" to null,
                "missing_kg" to "[]",
            ),
        )
        insert(
            db,
            "exercise",
            mapOf(
                "id" to "barbell_bench_press",
                "name" to "Barbell bench press",
                "aliases" to "[]",
                "counts_toward" to "[\"chest\"]",
                "equipment_ids" to "[\"barbell\"]",
                "big_or_small" to "big",
                "stresses" to "[]",
                "cues" to "[]",
                "instructions" to "[]",
                "images" to "[]",
                "unilateral" to 0,
                "one_sided_version_id" to null,
                "per_side" to 0,
                "timed" to 0,
                "bodyweight_based" to 0,
                "step_kg" to 2.5,
                "source" to "free-exercise-db",
                "licence" to "Unlicense",
            ),
        )
        insert(db, "program_day", mapOf("id" to "push_a", "name" to "Push A", "focus" to "Chest focus", "position" to 0))
        insert(
            db,
            "slot",
            mapOf(
                "id" to "push_a_1",
                "program_day_id" to "push_a",
                "position" to 1,
                "sets" to 3,
                "rep_low" to 6,
                "rep_high" to 8,
                "sec_low" to null,
                "sec_high" to null,
                "rest_s" to 150,
                "options" to "[\"barbell_bench_press\",\"smith_bench_press\"]",
            ),
        )
        insert(
            db,
            "estimate",
            mapOf(
                "exercise_id" to "barbell_bench_press",
                "e1rm_kg" to 60.0,
                "current_weight_kg" to 45.0,
                "bad_days" to 0,
                "sessions" to 1,
                "calibrated" to 1,
                "updated_at" to at,
            ),
        )
        insert(
            db,
            "session",
            mapOf(
                "id" to 1,
                "local_date" to day,
                "program_day_id" to "push_a",
                "kind" to "full",
                "block_week" to null,
                "started_at" to at,
                "completed_at" to at,
            ),
        )
        insert(
            db,
            "set_log",
            mapOf(
                "id" to 1,
                "session_id" to 1,
                "exercise_id" to "barbell_bench_press",
                "slot_id" to "push_a_1",
                "set_no" to 1,
                "weight_kg" to 45.0,
                "reps" to 9,
                "reps_left" to 3,
                "seconds" to null,
                "pain" to null,
                "sharp" to 0,
                "is_warmup" to 0,
                "is_ramp" to 0,
                "is_makeup" to 0,
                "in_injury_mode" to 0,
                "logged_at" to at,
            ),
        )
        insert(db, "day_log", mapOf("local_date" to day, "planned" to 1, "reason" to "sick", "reason_at" to at, "outcome" to "excused"))
        insert(
            db,
            "absence",
            mapOf(
                "id" to 1,
                "kind" to "away",
                "start" to "2026-10-22",
                "end" to "2026-10-25",
                "equipment_there" to "dumbbells",
                "minutes_per_day" to 30,
                "created_at" to at,
                "cancelled_at" to null,
            ),
        )
        insert(
            db,
            "makeup",
            mapOf("id" to 1, "triggered_on" to day, "skips_counted" to 2, "sessions_left" to 4, "paused" to 0, "updated_at" to at),
        )
        insert(
            db,
            "injury",
            mapOf(
                "id" to 1,
                "area" to "shoulder",
                "side" to "left",
                "status" to "active",
                "phase" to 1,
                "started" to day,
                "red_flag" to 0,
                "checked_by_doctor" to 0,
                "pre_injury" to "{}",
                "green_streak" to 0,
            ),
        )
        insert(db, "pain_log", mapOf("id" to 1, "injury_id" to 1, "local_date" to day, "kind" to "morning", "value" to 3, "sharp" to 0))
        insert(
            db,
            "food",
            mapOf(
                "id" to "roti",
                "name" to "Roti",
                "unit" to "1",
                "kcal" to 100.0,
                "protein_g" to 3.0,
                "diet_type" to "veg",
                "cooked" to 0,
                "category" to "staple",
            ),
        )
        insert(db, "usual_meal", mapOf("id" to 1, "name" to "Shake", "items" to "[]", "kcal" to 225.0, "protein_g" to 25.0))
        insert(
            db,
            "food_log",
            mapOf(
                "id" to 1,
                "local_date" to day,
                "meal" to "lunch",
                "food_id" to "roti",
                "usual_meal_id" to null,
                "qty" to 2.0,
                "kcal" to 200.0,
                "protein_g" to 6.0,
                "canteen" to 1,
                "source" to "tap",
            ),
        )
        insert(db, "body_log", mapOf("local_date" to day, "weight_kg" to 70.2))
        insert(db, "meta", mapOf("key" to "install_date", "value" to day))
    }
}
