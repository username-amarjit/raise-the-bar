package com.raisethebar.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raisethebar.app.data.AppClock
import com.raisethebar.app.data.AwayDraft
import com.raisethebar.app.data.Backup
import com.raisethebar.app.data.BackupRejected
import com.raisethebar.app.data.BackupSummary
import com.raisethebar.app.data.BackupTables
import com.raisethebar.app.data.Meals
import com.raisethebar.app.data.Repo
import com.raisethebar.app.data.SetInput
import com.raisethebar.app.data.addFood
import com.raisethebar.app.data.declareDayOff
import com.raisethebar.app.data.evaluateDays
import com.raisethebar.app.data.exportBackup
import com.raisethebar.app.data.finishSession
import com.raisethebar.app.data.importBackup
import com.raisethebar.app.data.logMorningWeight
import com.raisethebar.app.data.logSet
import com.raisethebar.app.data.logUsualMeal
import com.raisethebar.app.data.morningCheck
import com.raisethebar.app.data.planForToday
import com.raisethebar.app.data.saveAway
import com.raisethebar.app.data.startInjury
import com.raisethebar.app.data.startSession
import com.raisethebar.app.data.summary
import com.raisethebar.app.db.BodyLogEntity
import com.raisethebar.app.ui.LocalRepo
import com.raisethebar.app.ui.settings.SettingsScreen
import com.raisethebar.app.ui.settings.describe
import com.raisethebar.app.ui.theme.RtbTheme
import com.raisethebar.engine.AwayEquipment
import com.raisethebar.engine.Outcome
import com.raisethebar.engine.SessionKind
import com.raisethebar.engine.Side
import com.raisethebar.engine.Triage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertFailsWith

/** Milestone 13: export, wipe, import gives back exactly the same data; bad files change nothing. */
@RunWith(AndroidJUnit4::class)
class BackupTest {
    @get:Rule
    val compose = createComposeRule()

    private val monday = LocalDate.of(2026, 10, 26)

    /**
     * Wednesday to the next Monday of real use, touching every table: a workout, a declared day off, a trip, two
     * skips and make-up mode, an injury with a morning rating, food and weigh-ins.
     */
    private fun usedRepo(): Repo {
        val wednesday = TestRepo.createSetUp()
        runBlocking {
            val plan = wednesday.planForToday(SessionKind.FULL)!!
            val id = wednesday.startSession(plan)
            wednesday.logSet(id, SetInput(plan.exercises.first().key, 20.0, 8, 4))
            wednesday.finishSession(id)
            wednesday.declareDayOff(LocalDate.of(2026, 10, 22), acceptAsSkip = false)
            wednesday.saveAway(AwayDraft(monday, monday.plusDays(1), AwayEquipment.NONE))
            wednesday.startInjury(
                Triage.Answers("shoulder", Side.LEFT, feel = Triage.FEEL_DULL, onset = Triage.ONSET_OTHER, easesWithWarmUp = false, painNow = 3),
            )
            wednesday.addFood(Meals.LUNCH, "roti", canteen = true)
            wednesday.logUsualMeal(Meals.DINNER, wednesday.db.usualMeal().all().first().id, canteen = false)
            wednesday.logMorningWeight(70.2)
        }
        val later = Repo(wednesday.db, AppClock(Clock.offset(wednesday.time.clock, Duration.ofDays(5))))
        runBlocking {
            later.evaluateDays()
            later.morningCheck(later.db.injury().unresolved().associate { it.id to (2 to false) })
            later.addFood(Meals.BREAKFAST, "toned_milk", canteen = false)
            later.logMorningWeight(70.6)
        }
        return later
    }

    private fun assertSameTables(a: BackupTables, b: BackupTables) {
        assertEquals("profile", a.profile, b.profile)
        assertEquals("schedule", a.schedule, b.schedule)
        assertEquals("plan", a.plan, b.plan)
        assertEquals("equipment", a.equipment, b.equipment)
        assertEquals("exercise", a.exercise, b.exercise)
        assertEquals("program_day", a.programDay, b.programDay)
        assertEquals("slot", a.slot, b.slot)
        assertEquals("estimate", a.estimate, b.estimate)
        assertEquals("session", a.session, b.session)
        assertEquals("set_log", a.setLog, b.setLog)
        assertEquals("day_log", a.dayLog, b.dayLog)
        assertEquals("absence", a.absence, b.absence)
        assertEquals("makeup", a.makeup, b.makeup)
        assertEquals("injury", a.injury, b.injury)
        assertEquals("pain_log", a.painLog, b.painLog)
        assertEquals("food", a.food, b.food)
        assertEquals("usual_meal", a.usualMeal, b.usualMeal)
        assertEquals("food_log", a.foodLog, b.foodLog)
        assertEquals("body_log", a.bodyLog, b.bodyLog)
        assertEquals("meta", a.meta, b.meta)
    }

    @Test
    fun exportWipeAndImportRestoresEverything() {
        val text = runBlocking { usedRepo().exportBackup() }
        assertTrue(text.contains("\"app\":\"raise-the-bar\"") && text.contains("\"schema_version\":3") && text.contains("\"set_log\":["))
        val original = Backup.decode(text)
        val t = original.tables
        assertTrue(listOf(t.session, t.setLog, t.dayLog, t.absence, t.makeup, t.injury, t.painLog, t.foodLog, t.bodyLog).all { it.isNotEmpty() })
        assertTrue(t.dayLog.any { it.outcome == Outcome.SKIP })

        // A fresh install on the new phone, set up differently, with its own weigh-in for the same Monday.
        val phone = TestRepo.createSetUp(monday)
        runBlocking { phone.logBodyWeight(monday, 80.0) }
        runBlocking { phone.importBackup(original) }
        assertSameTables(t, Backup.decode(runBlocking { phone.exportBackup() }).tables)
        assertEquals(listOf(BodyLogEntity(LocalDate.of(2026, 10, 21), 70.2), BodyLogEntity(monday, 70.6)), runBlocking { phone.db.bodyLog().all() })
        assertTrue(runBlocking { phone.isSetupDone() })
    }

    @Test
    fun badFilesAreRejectedBeforeAnythingIsWritten() {
        val repo = usedRepo()
        val good = runBlocking { repo.exportBackup() }
        val file = Backup.decode(good)

        fun rejected(text: String): String = assertFailsWith<BackupRejected> { Backup.decode(text) }.message!!

        fun edited(edit: (BackupTables) -> BackupTables) = rejected(Backup.encode(file.copy(tables = edit(file.tables))))

        assertEquals(Backup.NOT_A_BACKUP, rejected("not json at all"))
        assertEquals(Backup.NOT_A_BACKUP, rejected("""{"app":"another-app","schema_version":3}"""))
        assertEquals(Backup.NEWER, rejected(good.replace("\"schema_version\":3", "\"schema_version\":4")))
        assertEquals(Backup.DAMAGED, rejected(good.replace("\"body_log\":", "\"weights\":")))
        assertEquals(Backup.DAMAGED, rejected(good.replace("\"weight_kg\":70.6", "\"weight_kg\":\"heavy\"")))

        assertTrue(edited { it.copy(bodyLog = it.bodyLog + BodyLogEntity(LocalDate.of(2026, 1, 1), 900.0)) }.contains("body_log"))
        assertTrue(edited { it.copy(setLog = it.setLog.map { s -> s.copy(sessionId = 999) }) }.contains("session_id 999"))
        assertTrue(edited { it.copy(setLog = it.setLog.map { s -> s.copy(reps = 51) }) }.contains("reps = 51"))
        assertTrue(edited { it.copy(schedule = it.schedule.map { s -> s.copy(time = "25:99") }) }.contains("schedule"))
        assertTrue(
            edited {
                it.copy(meta = it.meta.map { m -> if (m.key == "last_evaluated_date") m.copy(value = "yesterday") else m })
            }.contains("meta"),
        )
        assertTrue(edited { it.copy(injury = it.injury.map { i -> i.copy(area = "elbow_of_doom") }) }.contains("area elbow_of_doom"))
        assertTrue(
            edited {
                it.copy(
                    exercise =
                        it.exercise.map { x ->
                            x.copy(images = listOf("../../databases/raise-the-bar.db"))
                        },
                )
            }.contains("image path"),
        )
        assertTrue(edited { it.copy(profile = emptyList()) }.contains("exactly one profile"))
        assertTrue(edited { it.copy(foodLog = it.foodLog + it.foodLog.first()) }.contains("appears more than once"))

        assertSameTables(file.tables, Backup.decode(runBlocking { repo.exportBackup() }).tables)
    }

    @Test
    fun theConfirmStepSaysWhatTheBackupHolds() {
        val file = Backup.decode(runBlocking { usedRepo().exportBackup() })
        val s = file.summary()
        assertEquals(listOf(1, 1, 3, 2, 1, 2), listOf(s.workouts, s.sets, s.foodEntries, s.weighIns, s.injuries, s.daysOffAndTrips))
        val at = OffsetDateTime.of(2026, 10, 26, 8, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30))
        assertEquals(
            "Exported Monday 26 October 2026 at 08:00. It holds 1 workout (12 sets), 1 food entry, 1 morning weight, 1 injury " +
                "and 2 days off or trips.\n\nImporting replaces everything on this phone. This can't be undone.",
            BackupSummary(at, 1, 12, 1, 1, 1, 2).describe(),
        )
    }

    @Test
    fun settingsOffersExportImportAndHidingInTheAppSwitcher() {
        val repo = TestRepo.createSetUp()
        compose.setContent { RtbTheme { CompositionLocalProvider(LocalRepo provides repo) { SettingsScreen(onBack = {}, onAbout = {}) } } }
        compose.onNodeWithText("Export your data").performScrollTo()
        compose.onNodeWithText("Import a backup").performScrollTo()
        compose.onNodeWithText("Hide in the app switcher").performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { repo.profile() }!!.hideInSwitcher }
    }
}
