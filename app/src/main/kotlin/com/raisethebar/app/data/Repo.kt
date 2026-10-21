package com.raisethebar.app.data

import com.raisethebar.app.db.AbsenceEntity
import com.raisethebar.app.db.AppDatabase
import com.raisethebar.app.db.InjuryEntity
import com.raisethebar.app.db.MakeupEntity
import com.raisethebar.app.db.ProfileEntity
import com.raisethebar.engine.Absence
import com.raisethebar.engine.DayStatus
import com.raisethebar.engine.Days
import com.raisethebar.engine.Equipment
import com.raisethebar.engine.Estimate
import com.raisethebar.engine.Exercise
import com.raisethebar.engine.GymContext
import com.raisethebar.engine.InjuryRules
import com.raisethebar.engine.InjuryState
import com.raisethebar.engine.MakeupSettings
import com.raisethebar.engine.MakeupState
import com.raisethebar.engine.PlannerInput
import com.raisethebar.engine.PreInjury
import com.raisethebar.engine.Program
import com.raisethebar.engine.ScheduleDay
import com.raisethebar.engine.SeedData
import com.raisethebar.engine.Time
import com.raisethebar.engine.Weekdays
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.time.LocalDate

/** Schedules and cancels check-in notifications. The Android implementation uses AlarmManager. */
interface Notifier {
    fun scheduleCheckins(checkins: List<com.raisethebar.engine.Checkin>, previous: List<LocalDate>)

    fun cancelCheckin(date: LocalDate)

    companion object {
        val NONE =
            object : Notifier {
                override fun scheduleCheckins(checkins: List<com.raisethebar.engine.Checkin>, previous: List<LocalDate>) = Unit

                override fun cancelCheckin(date: LocalDate) = Unit
            }
    }
}

/**
 * The app's data layer on top of Room: reads for the screens, and transactional writes that tell the screens to
 * refresh through [changes]. Every training rule lives in the engine; this class only gathers inputs and stores results.
 */
class Repo(
    db: AppDatabase,
    time: AppClock,
    val notifier: Notifier = Notifier.NONE,
) : Store(db, time) {
    private val changeCount = MutableStateFlow(0L)
    val changes: StateFlow<Long> = changeCount.asStateFlow()

    fun changed() = changeCount.update { it + 1 }

    /** Runs [block] in one transaction, then refreshes the screens. */
    suspend fun <T> write(block: suspend () -> T): T {
        val result = tx(block)
        changed()
        return result
    }

    val rules: InjuryRules get() = SeedData.injuryRules

    @Volatile private var exerciseCache: Map<String, Exercise>? = null

    suspend fun exercises(): Map<String, Exercise> {
        exerciseCache?.let { return it }
        val fromDb = db.exercise().all().associate { it.id to it.toModel() }
        return (fromDb.ifEmpty { SeedData.exercisesById }).also { exerciseCache = it }
    }

    fun clearCaches() {
        exerciseCache = null
    }

    suspend fun program(): Program = SeedData.program.withPromotions(db.program().slots().associate { it.id to it.promotedExerciseId })

    suspend fun gym(): Map<String, Equipment> = db.equipment().all().associate { it.id to it.toModel() }

    suspend fun profile(): ProfileEntity? = db.profile().get()

    suspend fun isSetupDone(): Boolean = db.meta().get(Meta.SETUP_DONE) == "1" && db.profile().get() != null && db.plan().get() != null

    suspend fun schedule(): List<ScheduleDay> =
        db.schedule().all().map { ScheduleDay(it.weekday, it.isTrainingDay, Time.parseHhmm(it.time)) }.ifEmpty { Weekdays.defaults() }

    suspend fun absences(): List<Absence> = db.absence().all().map { it.toModel() }

    suspend fun injuries(): List<InjuryState> = db.injury().unresolved().map { it.toModel() }

    suspend fun estimates(): Map<String, Estimate> =
        db.estimate().all().filter { it.e1rmKg != null }.associate {
            it.exerciseId to Estimate(it.e1rmKg!!, it.currentWeightKg, it.badDays, it.sessions)
        }

    suspend fun holdOffsets(): Map<String, Int> = db.estimate().all().filter { it.holdOffsetS != 0 }.associate { it.exerciseId to it.holdOffsetS }

    /** Body weight for effective loads: the latest weigh-in, else the setup weight. */
    suspend fun bodyKg(): Double? = db.bodyLog().latest()?.weightKg ?: profile()?.weightKg

    suspend fun makeupSettings(): MakeupSettings =
        profile()?.makeupSettings?.let { runCatching { SeedData.json.decodeFromString(MakeupSettings.serializer(), it) }.getOrNull() }?.clamped()
            ?: MakeupSettings()

    suspend fun makeupState(): MakeupState = db.makeup().get()?.toModel() ?: MakeupState()

    suspend fun saveMakeup(state: MakeupState) =
        db.makeup().upsert(MakeupEntity(1, state.triggeredOn, state.skipsCounted, state.sessionsLeft, state.paused, time.now()))

    suspend fun dayStatus(date: LocalDate): DayStatus = Days.status(date, schedule(), absences(), program())

    suspend fun planStart(): LocalDate = db.plan().get()?.planStart ?: time.today()

    suspend fun ctx(date: LocalDate, skipToday: Set<String> = emptySet()): GymContext =
        GymContext(date, exercises(), gym(), injuries(), rules, skipToday)

    suspend fun plannerInput(date: LocalDate, skipToday: Set<String> = emptySet()): PlannerInput =
        PlannerInput(
            date = date,
            planStart = planStart(),
            program = program(),
            ctx = ctx(date, skipToday),
            estimates = estimates(),
            holdOffsets = holdOffsets(),
            bodyKg = bodyKg(),
            preInjury = preInjury(),
        )

    suspend fun preInjury(): Map<Long, Map<String, PreInjury>> = db.injury().unresolved().associate { it.id to decodePreInjury(it.preInjury) }

    companion object {
        val preInjurySerializer = MapSerializer(String.serializer(), PreInjury.serializer())

        fun decodePreInjury(json: String): Map<String, PreInjury> =
            runCatching {
                SeedData.json.decodeFromString(preInjurySerializer, json)
            }.getOrDefault(emptyMap())

        fun encodePreInjury(map: Map<String, PreInjury>): String = SeedData.json.encodeToString(preInjurySerializer, map)
    }
}

fun AbsenceEntity.toModel() =
    Absence(
        id = id,
        kind = kind,
        start = start,
        end = end,
        equipmentThere = equipmentThere,
        minutesPerDay = minutesPerDay,
        createdAt = createdAt,
        cancelledAt = cancelledAt,
        countsAsSkip = countsAsSkip,
    )

fun InjuryEntity.toModel() =
    InjuryState(
        id = id,
        area = area,
        side = side,
        status = status,
        phase = phase,
        started = started,
        phaseStarted = phaseStarted,
        redFlag = redFlag,
        checkedByDoctor = checkedByDoctor,
        greenStreak = greenStreak,
        loadFraction = loadFraction,
        muscle = muscle,
        usualMorningPain = usualMorningPain,
    )

/** Copies the engine's view back onto the row, keeping the triage details. */
fun InjuryEntity.withState(s: InjuryState) =
    copy(
        status = s.status,
        phase = s.phase,
        phaseStarted = s.phaseStarted,
        redFlag = s.redFlag,
        checkedByDoctor = s.checkedByDoctor,
        greenStreak = s.greenStreak,
        loadFraction = s.loadFraction,
        usualMorningPain = s.usualMorningPain,
    )

fun MakeupEntity.toModel() = MakeupState(triggeredOn, skipsCounted, sessionsLeft, paused)
