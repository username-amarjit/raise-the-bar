@file:OptIn(ExperimentalSerializationApi::class)

package com.raisethebar.app.data

import com.raisethebar.app.db.AbsenceEntity
import com.raisethebar.app.db.AppDatabase
import com.raisethebar.app.db.BodyLogEntity
import com.raisethebar.app.db.DayLogEntity
import com.raisethebar.app.db.EquipmentEntity
import com.raisethebar.app.db.EstimateEntity
import com.raisethebar.app.db.ExerciseEntity
import com.raisethebar.app.db.FoodEntity
import com.raisethebar.app.db.FoodLogEntity
import com.raisethebar.app.db.InjuryEntity
import com.raisethebar.app.db.MakeupEntity
import com.raisethebar.app.db.MetaEntity
import com.raisethebar.app.db.OffsetDateTimeSerializer
import com.raisethebar.app.db.PainLogEntity
import com.raisethebar.app.db.PlanEntity
import com.raisethebar.app.db.ProfileEntity
import com.raisethebar.app.db.ProgramDayEntity
import com.raisethebar.app.db.ScheduleEntity
import com.raisethebar.app.db.SessionEntity
import com.raisethebar.app.db.SetLogEntity
import com.raisethebar.app.db.SlotEntity
import com.raisethebar.app.db.UsualMealEntity
import com.raisethebar.config.Rules.Limits
import com.raisethebar.engine.Injuries
import com.raisethebar.engine.InputRejected
import com.raisethebar.engine.SeedData
import com.raisethebar.engine.Time
import com.raisethebar.engine.Validate
import com.raisethebar.engine.Weekdays
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.DateTimeException
import java.time.OffsetDateTime

/** The export file (milestone 13): every table exactly as it is in the database, plus the schema version. */
@Serializable
data class BackupFile(
    val app: String,
    val schemaVersion: Int,
    @Serializable(with = OffsetDateTimeSerializer::class) val exportedAt: OffsetDateTime,
    val tables: BackupTables,
)

/** One list per table, named as in the database. Every table must be present in the file. */
@Serializable
data class BackupTables(
    val profile: List<ProfileEntity>,
    val schedule: List<ScheduleEntity>,
    val plan: List<PlanEntity>,
    val equipment: List<EquipmentEntity>,
    val exercise: List<ExerciseEntity>,
    val programDay: List<ProgramDayEntity>,
    val slot: List<SlotEntity>,
    val estimate: List<EstimateEntity>,
    val session: List<SessionEntity>,
    val setLog: List<SetLogEntity>,
    val dayLog: List<DayLogEntity>,
    val absence: List<AbsenceEntity>,
    val makeup: List<MakeupEntity>,
    val injury: List<InjuryEntity>,
    val painLog: List<PainLogEntity>,
    val food: List<FoodEntity>,
    val usualMeal: List<UsualMealEntity>,
    val foodLog: List<FoodLogEntity>,
    val bodyLog: List<BodyLogEntity>,
    val meta: List<MetaEntity>,
)

/** Why a file can't be imported, in words for the user. Nothing has been changed when this is thrown. */
class BackupRejected(
    message: String,
) : Exception(message)

/** What a backup holds, shown before the user confirms the import. */
data class BackupSummary(
    val exportedAt: OffsetDateTime,
    val workouts: Int,
    val sets: Int,
    val foodEntries: Int,
    val weighIns: Int,
    val injuries: Int,
    val daysOffAndTrips: Int,
)

fun BackupFile.summary() =
    BackupSummary(
        exportedAt = exportedAt,
        workouts = tables.session.count { it.completedAt != null },
        sets = tables.setLog.count { !it.isWarmup },
        foodEntries = tables.foodLog.size,
        weighIns = tables.bodyLog.size,
        injuries = tables.injury.size,
        daysOffAndTrips = tables.absence.count { it.cancelledAt == null },
    )

object Backup {
    const val APP = "raise-the-bar"

    /** Far above years of training logs; anything bigger isn't a backup. */
    const val MAX_BYTES = 32 * 1024 * 1024

    const val NOT_A_BACKUP = "This file isn't a Raise the Bar backup. Nothing was changed."
    const val NEWER = "This backup comes from a newer version of the app. Update the app, then import it again. Nothing was changed."
    const val DAMAGED = "This backup is damaged or incomplete, so nothing was changed."

    val json: Json =
        Json {
            namingStrategy = JsonNamingStrategy.SnakeCase
            encodeDefaults = true
            explicitNulls = true
            ignoreUnknownKeys = false
            allowSpecialFloatingPointValues = false
        }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /**
     * Reads a backup and checks it before anything is written: the app marker and schema version, the structure
     * (every table and field, no unknown keys), then value ranges and references between tables. The file is only
     * ever parsed as data; nothing in it is executed. Throws [BackupRejected].
     */
    fun decode(text: String): BackupFile {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject
        val app = (root?.get("app") as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (root == null || app != APP) throw BackupRejected(NOT_A_BACKUP)
        val version = (root["schema_version"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: throw BackupRejected(DAMAGED)
        if (version > AppDatabase.VERSION) throw BackupRejected(NEWER)
        if (version < 1) throw BackupRejected(DAMAGED)
        val file =
            try {
                json.decodeFromJsonElement(BackupFile.serializer(), root)
            } catch (e: IllegalArgumentException) {
                throw BackupRejected(DAMAGED)
            } catch (e: DateTimeException) {
                throw BackupRejected(DAMAGED)
            }
        BackupCheck(file.tables).run()
        return file
    }
}

/** Every table, read in one transaction so the file is a consistent snapshot. */
suspend fun Repo.exportBackup(): String {
    val file =
        tx {
            val b = db.backup()
            val tables =
                BackupTables(
                    profile = b.profile(),
                    schedule = b.schedule(),
                    plan = b.plan(),
                    equipment = b.equipment(),
                    exercise = b.exercise(),
                    programDay = b.programDay(),
                    slot = b.slot(),
                    estimate = b.estimate(),
                    session = b.session(),
                    setLog = b.setLog(),
                    dayLog = b.dayLog(),
                    absence = b.absence(),
                    makeup = b.makeup(),
                    injury = b.injury(),
                    painLog = b.painLog(),
                    food = b.food(),
                    usualMeal = b.usualMeal(),
                    foodLog = b.foodLog(),
                    bodyLog = b.bodyLog(),
                    meta = b.meta(),
                )
            BackupFile(Backup.APP, AppDatabase.VERSION, time.now(), tables)
        }
    return Backup.encode(file)
}

/**
 * Replaces all data with a checked backup in one transaction: either everything is replaced or nothing is. The
 * bundled library is then applied again, as after an app update, and the day timeout and check-ins catch up.
 */
suspend fun Repo.importBackup(file: BackupFile) {
    val t = file.tables
    tx {
        val b = db.backup()
        b.clearSetLog()
        b.clearPainLog()
        b.clearSlot()
        b.clearEstimate()
        b.clearSession()
        b.clearInjury()
        b.clearProgramDay()
        b.clearExercise()
        b.clearProfile()
        b.clearSchedule()
        b.clearPlan()
        b.clearEquipment()
        b.clearDayLog()
        b.clearAbsence()
        b.clearMakeup()
        b.clearFoodLog()
        b.clearUsualMeal()
        b.clearFood()
        b.clearBodyLog()
        b.clearMeta()

        b.insertProfile(t.profile)
        b.insertSchedule(t.schedule)
        b.insertPlan(t.plan)
        b.insertEquipment(t.equipment)
        b.insertExercise(t.exercise)
        b.insertProgramDay(t.programDay)
        b.insertSlot(t.slot)
        b.insertEstimate(t.estimate)
        b.insertSession(t.session)
        b.insertSetLog(t.setLog)
        b.insertDayLog(t.dayLog)
        b.insertAbsence(t.absence)
        b.insertMakeup(t.makeup)
        b.insertInjury(t.injury)
        b.insertPainLog(t.painLog)
        b.insertFood(t.food)
        b.insertUsualMeal(t.usualMeal)
        b.insertFoodLog(t.foodLog)
        b.insertBodyLog(t.bodyLog)
        // Without the seed marker, the seeder below re-applies the bundled library to the imported rows.
        b.insertMeta(t.meta.filter { it.key != Seeder.KEY_SEED_VERSION && it.key != Meta.SETUP_DONE })
        db.meta().put(MetaEntity(Meta.SETUP_DONE, "1"))
    }
    notifier.cancelRestEnd()
    Seeder(db, time).seedIfNeeded()
    clearCaches()
    evaluateDays()
    changed()
}

/** Value ranges and references, table by table. Legitimate exports always pass: every check matches a write rule. */
private class BackupCheck(
    private val t: BackupTables,
) {
    private var where = ""

    private fun fail(what: String): Nothing =
        throw BackupRejected("This backup has a value the app can't accept ($where: $what), so nothing was changed.")

    private fun need(ok: Boolean, what: () -> String) {
        if (!ok) fail(what())
    }

    private fun table(name: String, check: () -> Unit) {
        where = name
        check()
    }

    private fun <T> rows(name: String, list: List<T>, check: (T) -> Unit) {
        list.forEachIndexed { i, row ->
            where = "$name, row ${i + 1}"
            try {
                check(row)
            } catch (e: InputRejected) {
                fail("${e.field} = ${e.value}, allowed ${e.allowed}")
            } catch (e: DateTimeException) {
                fail("a date or time can't be read")
            }
        }
    }

    private fun <K> unique(name: String, keys: List<K>) {
        val repeated = keys.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.key ?: return
        where = name
        fail("$repeated appears more than once")
    }

    private fun id(v: Long) = need(v >= 1) { "id = $v" }

    private fun amount(v: Double?, range: ClosedFloatingPointRange<Double>, field: String) =
        need(v == null || (v.isFinite() && v in range)) {
            "$field = $v"
        }

    private fun count(v: Int?, range: IntRange, field: String) = need(v == null || v in range) { "$field = $v" }

    fun run() {
        table("profile") { need(t.profile.size == 1) { "a backup has exactly one profile" } }
        rows("profile", t.profile) { p ->
            need(p.id == 1) { "id = ${p.id}" }
            Store.validateProfile(p)
            p.targetsBasisKg?.let(Validate::bodyWeightKg)
            Time.parseHhmm(p.dayOffCutoff)
        }
        table("plan") { need(t.plan.size == 1) { "a backup has exactly one plan" } }
        rows("plan", t.plan) { p ->
            need(p.id == 1) { "id = ${p.id}" }
            count(p.rotationIndex, SeedData.program.rotation.indices, "rotation_index")
        }
        unique("schedule", t.schedule.map { it.weekday })
        rows("schedule", t.schedule) { s ->
            count(s.weekday, WEEKDAYS, "weekday")
            Time.parseHhmm(s.time)
        }

        unique("equipment", t.equipment.map { it.id })
        rows("equipment", t.equipment) { e ->
            (listOfNotNull(e.barKg, e.stackMin, e.stackMax, e.stackStep) + e.platePairsKg + e.dumbbellPairsKg + e.missingKg)
                .forEach { amount(it, GYM_KG, "weight_kg") }
        }
        unique("exercise", t.exercise.map { it.id })
        rows("exercise", t.exercise) { x ->
            need(x.images.all(::isBundledImage)) { "image path" }
            amount(x.stepKg, GYM_KG, "step_kg")
        }
        val exercises = t.exercise.map { it.id }.toSet()
        unique("program_day", t.programDay.map { it.id })
        val days = t.programDay.map { it.id }.toSet()
        unique("slot", t.slot.map { it.id })
        rows("slot", t.slot) { s ->
            need(s.programDayId in days) { "program_day_id ${s.programDayId} isn't in the backup" }
            need(s.promotedExerciseId == null || s.promotedExerciseId in exercises) { "promoted_exercise_id ${s.promotedExerciseId}" }
            need(s.options.isNotEmpty()) { "a slot needs at least one exercise" }
            count(s.sets, SLOT_SETS, "sets")
            count(s.restS, REST_S, "rest_s")
        }
        unique("estimate", t.estimate.map { it.exerciseId })
        rows("estimate", t.estimate) { e ->
            need(e.exerciseId in exercises) { "exercise_id ${e.exerciseId} isn't in the backup" }
            amount(e.e1rmKg, E1RM_KG, "e1rm_kg")
            amount(e.currentWeightKg, -GYM_KG.endInclusive..GYM_KG.endInclusive, "current_weight_kg")
            count(e.badDays, COUNTS, "bad_days")
            count(e.sessions, COUNTS, "sessions")
            count(e.holdOffsetS, 0..Limits.HOLD_S.last, "hold_offset_s")
        }

        unique("session", t.session.map { it.id })
        rows("session", t.session) { s ->
            id(s.id)
            need(s.state == null || decodeState(s.state) != null) { "the saved workout can't be read" }
        }
        val sessions = t.session.map { it.id }.toSet()
        unique("set_log", t.setLog.map { it.id })
        rows("set_log", t.setLog) { s ->
            id(s.id)
            need(s.sessionId in sessions) { "session_id ${s.sessionId} isn't in the backup" }
            need(s.exerciseId in exercises) { "exercise_id ${s.exerciseId} isn't in the backup" }
            Store.validateSet(s)
            s.bwKg?.let(Validate::bodyWeightKg)
            count(s.targetReps, TARGETS, "target_reps")
            count(s.targetLeft, TARGETS, "target_left")
        }
        unique("day_log", t.dayLog.map { it.localDate })
        rows("day_log", t.dayLog) { d -> d.laterTime?.let(Time::parseHhmm) }
        unique("absence", t.absence.map { it.id })
        rows("absence", t.absence) { a ->
            id(a.id)
            Store.validateAbsence(a)
        }
        table("makeup") { need(t.makeup.size <= 1) { "more than one make-up row" } }
        rows("makeup", t.makeup) { m ->
            need(m.id == 1) { "id = ${m.id}" }
            count(m.skipsCounted, COUNTS, "skips_counted")
            count(m.sessionsLeft, COUNTS, "sessions_left")
        }

        val areas = SeedData.injuryRules.areas.map { it.id }.toSet() + Injuries.MUSCLE
        unique("injury", t.injury.map { it.id })
        rows("injury", t.injury) { i ->
            id(i.id)
            need(i.area in areas) { "area ${i.area}" }
            count(i.phase, PHASES, "phase")
            count(i.greenStreak, COUNTS, "green_streak")
            i.painNow?.let(Validate::pain)
            i.usualMorningPain?.let(Validate::pain)
            amount(i.loadFraction, LOAD_FRACTION, "load_fraction")
        }
        val injuries = t.injury.map { it.id }.toSet()
        unique("pain_log", t.painLog.map { it.id })
        rows("pain_log", t.painLog) { p ->
            id(p.id)
            need(p.injuryId in injuries) { "injury_id ${p.injuryId} isn't in the backup" }
            need(p.kind in Store.PAIN_KINDS) { "kind ${p.kind}" }
            Validate.pain(p.value)
        }

        unique("food", t.food.map { it.id })
        rows("food", t.food) { f ->
            need(f.dietType in Store.DIET_TYPES) { "diet_type ${f.dietType}" }
            amount(f.kcal, 0.0..MAX_MEAL_KCAL, "kcal")
            amount(f.proteinG, 0.0..MAX_MEAL_PROTEIN_G, "protein_g")
        }
        val foods = t.food.map { it.id }.toSet()
        unique("usual_meal", t.usualMeal.map { it.id })
        rows("usual_meal", t.usualMeal) { m ->
            id(m.id)
            amount(m.kcal, LOGGED_KCAL, "kcal")
            amount(m.proteinG, LOGGED_PROTEIN_G, "protein_g")
        }
        unique("food_log", t.foodLog.map { it.id })
        rows("food_log", t.foodLog) { l ->
            id(l.id)
            need(l.meal in Meals.ALL) { "meal ${l.meal}" }
            need(l.qty.isFinite() && l.qty > 0 && l.qty <= MAX_QTY) { "qty = ${l.qty}" }
            need(l.foodId == null || l.foodId in foods) { "food_id ${l.foodId} isn't in the backup" }
            amount(l.kcal, LOGGED_KCAL, "kcal")
            amount(l.proteinG, LOGGED_PROTEIN_G, "protein_g")
        }
        unique("body_log", t.bodyLog.map { it.localDate })
        rows("body_log", t.bodyLog) { b -> Validate.bodyWeightKg(b.weightKg) }

        unique("meta", t.meta.map { it.key })
        rows("meta", t.meta) { m ->
            need(m.key.isNotBlank()) { "an empty key" }
            if (m.key in DATE_KEYS) Time.parseDate(m.value)
        }
    }

    private companion object {
        val WEEKDAYS = Weekdays.NAMES.indices
        val GYM_KG = 0.0..Limits.EXERCISE_WEIGHT_KG.endInclusive
        val E1RM_KG = 0.0..2 * Limits.EXERCISE_WEIGHT_KG.endInclusive
        val SLOT_SETS = 1..20
        val REST_S = 0..3600
        val COUNTS = 0..100_000
        val TARGETS = 0..1000
        val PHASES = 1..4
        val LOAD_FRACTION = 0.0..2.0
        val LOGGED_KCAL = 0.0..MAX_MEAL_KCAL * MAX_QTY
        val LOGGED_PROTEIN_G = 0.0..MAX_MEAL_PROTEIN_G * MAX_QTY
        val DATE_KEYS = setOf(Meta.LAST_EVALUATED_DATE, Meta.INSTALL_DATE, Meta.MORNING_CHECK_DATE)
        val IMAGE_PATH = Regex("[A-Za-z0-9_\\-]+(/[A-Za-z0-9_\\-]+)*\\.(webp|png|jpg)")

        /** Exercise photos are bundled assets: a plain relative path, never one that climbs out of the folder. */
        fun isBundledImage(path: String) = IMAGE_PATH.matches(path)
    }
}
