@file:UseSerializers(LocalDateSerializer::class, OffsetDateTimeSerializer::class)

package com.raisethebar.app.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.raisethebar.engine.LocalDateSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate
import java.time.OffsetDateTime

@Serializable
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    @ColumnInfo(name = "height_cm") val heightCm: Double,
    @ColumnInfo(name = "waist_cm") val waistCm: Double?,
    val age: Int,
    @ColumnInfo(name = "diet_type") val dietType: String,
    val experience: String,
    val goal: String,
    @ColumnInfo(name = "protein_target_g") val proteinTargetG: Int,
    @ColumnInfo(name = "kcal_target") val kcalTarget: Int,
    @ColumnInfo(name = "day_off_cutoff") val dayOffCutoff: String,
    @ColumnInfo(name = "makeup_settings") val makeupSettings: String,
    @ColumnInfo(name = "notifications_on") val notificationsOn: Boolean,
    // v2: the body weight the current targets were computed from, and the app-switcher privacy toggle.
    @ColumnInfo(name = "targets_basis_kg") val targetsBasisKg: Double? = null,
    @ColumnInfo(name = "hide_in_switcher", defaultValue = "0") val hideInSwitcher: Boolean = false,
)

@Serializable
@Entity(tableName = "schedule")
data class ScheduleEntity(
    @PrimaryKey val weekday: Int,
    @ColumnInfo(name = "is_training_day") val isTrainingDay: Boolean,
    val time: String,
)

@Serializable
@Entity(tableName = "plan")
data class PlanEntity(
    @PrimaryKey val id: Int = 1,
    val template: String,
    @ColumnInfo(name = "plan_start") val planStart: LocalDate,
    @ColumnInfo(name = "rotation_index") val rotationIndex: Int,
    val previous: String?,
)

@Serializable
@Entity(tableName = "equipment")
data class EquipmentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val status: String,
    @ColumnInfo(name = "status_date") val statusDate: LocalDate?,
    @ColumnInfo(name = "broken_since") val brokenSince: LocalDate?,
    @ColumnInfo(name = "bar_kg") val barKg: Double?,
    @ColumnInfo(name = "plate_pairs_kg") val platePairsKg: List<Double>,
    @ColumnInfo(name = "dumbbell_pairs_kg") val dumbbellPairsKg: List<Double>,
    @ColumnInfo(name = "stack_min") val stackMin: Double?,
    @ColumnInfo(name = "stack_max") val stackMax: Double?,
    @ColumnInfo(name = "stack_step") val stackStep: Double?,
    @ColumnInfo(name = "missing_kg") val missingKg: List<Double>,
    // v2: display order, and the last date the app asked "Is it fixed yet?".
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
    @ColumnInfo(name = "fixed_asked_on") val fixedAskedOn: LocalDate? = null,
    // v3: the checklist group it is shown under, and a short note.
    @ColumnInfo(defaultValue = "") val section: String = "",
    val note: String? = null,
)

@Serializable
@Entity(tableName = "exercise")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val aliases: List<String>,
    @ColumnInfo(name = "counts_toward") val countsToward: List<String>,
    @ColumnInfo(name = "equipment_ids") val equipmentIds: List<String>,
    @ColumnInfo(name = "big_or_small") val bigOrSmall: String,
    val stresses: List<String>,
    val cues: List<String>,
    val instructions: List<String>,
    val images: List<String>,
    val unilateral: Boolean,
    @ColumnInfo(name = "one_sided_version_id") val oneSidedVersionId: String?,
    @ColumnInfo(name = "per_side") val perSide: Boolean,
    val timed: Boolean,
    @ColumnInfo(name = "bodyweight_based") val bodyweightBased: Boolean,
    @ColumnInfo(name = "step_kg") val stepKg: Double?,
    val source: String,
    val licence: String,
    // v2: muscle chips for the exercise page, body region for weight steps, and assisted bodyweight flag.
    @ColumnInfo(name = "primary_muscles", defaultValue = "[]") val primaryMuscles: List<String> = emptyList(),
    @ColumnInfo(name = "secondary_muscles", defaultValue = "[]") val secondaryMuscles: List<String> = emptyList(),
    @ColumnInfo(name = "body_region", defaultValue = "upper") val bodyRegion: String = "upper",
    @ColumnInfo(defaultValue = "0") val assisted: Boolean = false,
    // v3: how the exercise is loaded, and the free-exercise-db record it came from.
    @ColumnInfo(name = "load_kind", defaultValue = "none") val loadKind: String = "none",
    @ColumnInfo(name = "source_id") val sourceId: String? = null,
)

@Serializable
@Entity(tableName = "program_day")
data class ProgramDayEntity(
    @PrimaryKey val id: String,
    val name: String,
    val focus: String,
    val position: Int,
    // v2: rotation, home, away or pre_trip; and push, pull, legs or full.
    @ColumnInfo(defaultValue = "rotation") val kind: String = "rotation",
    @ColumnInfo(name = "day_type", defaultValue = "push") val dayType: String = "push",
)

@Serializable
@Entity(
    tableName = "slot",
    foreignKeys = [
        ForeignKey(entity = ProgramDayEntity::class, parentColumns = ["id"], childColumns = ["program_day_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("program_day_id")],
)
data class SlotEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "program_day_id") val programDayId: String,
    val position: Int,
    val sets: Int,
    @ColumnInfo(name = "rep_low") val repLow: Int?,
    @ColumnInfo(name = "rep_high") val repHigh: Int?,
    @ColumnInfo(name = "sec_low") val secLow: Int?,
    @ColumnInfo(name = "sec_high") val secHigh: Int?,
    @ColumnInfo(name = "rest_s") val restS: Int,
    val options: List<String>,
    // v2: a fixed stop-with override ("3 × stop with 2 left"), a display note, and the user's promoted first choice.
    @ColumnInfo(name = "stop_left") val stopLeft: Int? = null,
    val note: String? = null,
    @ColumnInfo(name = "promoted_exercise_id") val promotedExerciseId: String? = null,
)

@Serializable
@Entity(
    tableName = "estimate",
    foreignKeys = [
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exercise_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class EstimateEntity(
    @PrimaryKey @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "e1rm_kg") val e1rmKg: Double?,
    @ColumnInfo(name = "current_weight_kg") val currentWeightKg: Double?,
    @ColumnInfo(name = "bad_days") val badDays: Int,
    val sessions: Int,
    val calibrated: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: OffsetDateTime,
    // v2: timed holds progress by adding seconds to both ends of the range.
    @ColumnInfo(name = "hold_offset_s", defaultValue = "0") val holdOffsetS: Int = 0,
)

@Serializable
@Entity(tableName = "session", indices = [Index("local_date")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "local_date") val localDate: LocalDate,
    @ColumnInfo(name = "program_day_id") val programDayId: String,
    val kind: String,
    @ColumnInfo(name = "block_week") val blockWeek: Int?,
    @ColumnInfo(name = "started_at") val startedAt: OffsetDateTime,
    @ColumnInfo(name = "completed_at") val completedAt: OffsetDateTime?,
    // v2: calendar week, rotation position, the make-up block, and the resume state of an unfinished session.
    @ColumnInfo(name = "plan_week") val planWeek: Int? = null,
    @ColumnInfo(name = "rotation_index") val rotationIndex: Int? = null,
    @ColumnInfo(name = "makeup_block") val makeupBlock: String? = null,
    @ColumnInfo(name = "makeup_logged", defaultValue = "0") val makeupLogged: Boolean = false,
    val state: String? = null,
    @ColumnInfo(defaultValue = "0") val abandoned: Boolean = false,
)

@Serializable
@Entity(
    tableName = "set_log",
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("session_id"), Index("exercise_id")],
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "slot_id") val slotId: String?,
    @ColumnInfo(name = "set_no") val setNo: Int,
    @ColumnInfo(name = "weight_kg") val weightKg: Double?,
    val reps: Int?,
    @ColumnInfo(name = "reps_left") val repsLeft: Int?,
    val seconds: Int?,
    val pain: Int?,
    val sharp: Boolean,
    @ColumnInfo(name = "is_warmup") val isWarmup: Boolean,
    @ColumnInfo(name = "is_ramp") val isRamp: Boolean,
    @ColumnInfo(name = "is_makeup") val isMakeup: Boolean,
    @ColumnInfo(name = "in_injury_mode") val inInjuryMode: Boolean,
    @ColumnInfo(name = "logged_at") val loggedAt: OffsetDateTime,
    // v2: body weight used for effective load, and the target the set was logged against.
    @ColumnInfo(name = "bw_kg") val bwKg: Double? = null,
    @ColumnInfo(name = "target_reps") val targetReps: Int? = null,
    @ColumnInfo(name = "target_left") val targetLeft: Int? = null,
)

@Serializable
@Entity(tableName = "day_log")
data class DayLogEntity(
    @PrimaryKey @ColumnInfo(name = "local_date") val localDate: LocalDate,
    val planned: Boolean,
    val reason: String?,
    @ColumnInfo(name = "reason_at") val reasonAt: OffsetDateTime?,
    val outcome: String?,
    // v2: "Going later" moves the check-in to this time.
    @ColumnInfo(name = "later_time") val laterTime: String? = null,
)

@Serializable
@Entity(tableName = "absence", indices = [Index("start"), Index("end")])
data class AbsenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val start: LocalDate,
    val end: LocalDate,
    @ColumnInfo(name = "equipment_there") val equipmentThere: String?,
    @ColumnInfo(name = "minutes_per_day") val minutesPerDay: Int?,
    @ColumnInfo(name = "created_at") val createdAt: OffsetDateTime,
    @ColumnInfo(name = "cancelled_at") val cancelledAt: OffsetDateTime?,
    // v2: a declared day off beyond the monthly allowance counts as an unexcused skip.
    @ColumnInfo(name = "counts_as_skip", defaultValue = "0") val countsAsSkip: Boolean = false,
)

@Serializable
@Entity(tableName = "makeup")
data class MakeupEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "triggered_on") val triggeredOn: LocalDate?,
    @ColumnInfo(name = "skips_counted") val skipsCounted: Int,
    @ColumnInfo(name = "sessions_left") val sessionsLeft: Int,
    val paused: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: OffsetDateTime,
)

@Serializable
@Entity(tableName = "injury")
data class InjuryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val area: String,
    val side: String,
    val status: String,
    val phase: Int,
    val started: LocalDate,
    @ColumnInfo(name = "red_flag") val redFlag: Boolean,
    @ColumnInfo(name = "checked_by_doctor") val checkedByDoctor: Boolean,
    @ColumnInfo(name = "pre_injury") val preInjury: String,
    @ColumnInfo(name = "green_streak") val greenStreak: Int,
    // v2: triage details and phase progress.
    val muscle: String? = null,
    val feel: String? = null,
    @ColumnInfo(name = "pain_now") val painNow: Int? = null,
    @ColumnInfo(name = "phase_started") val phaseStarted: LocalDate? = null,
    @ColumnInfo(name = "load_fraction") val loadFraction: Double? = null,
    @ColumnInfo(name = "usual_morning_pain") val usualMorningPain: Int? = null,
    @ColumnInfo(name = "resolved_at") val resolvedAt: OffsetDateTime? = null,
)

@Serializable
@Entity(
    tableName = "pain_log",
    foreignKeys = [
        ForeignKey(entity = InjuryEntity::class, parentColumns = ["id"], childColumns = ["injury_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("injury_id"), Index("local_date")],
)
data class PainLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "injury_id") val injuryId: Long,
    @ColumnInfo(name = "local_date") val localDate: LocalDate,
    val kind: String,
    val value: Int,
    val sharp: Boolean,
    // v2: which exercise and set the rating came from.
    @ColumnInfo(name = "exercise_id") val exerciseId: String? = null,
    @ColumnInfo(name = "set_log_id") val setLogId: Long? = null,
)

@Serializable
@Entity(tableName = "food")
data class FoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val unit: String,
    val kcal: Double,
    @ColumnInfo(name = "protein_g") val proteinG: Double,
    @ColumnInfo(name = "diet_type") val dietType: String,
    val cooked: Boolean,
    val category: String,
    // v2: the quick-add chip label and its order.
    @ColumnInfo(name = "short_name") val shortName: String? = null,
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
)

@Serializable
@Entity(tableName = "usual_meal")
data class UsualMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val items: String,
    val kcal: Double,
    @ColumnInfo(name = "protein_g") val proteinG: Double,
    // v2: the three example meals are seeded; the user can delete them.
    @ColumnInfo(defaultValue = "0") val seeded: Boolean = false,
)

@Serializable
@Entity(tableName = "food_log", indices = [Index("local_date")])
data class FoodLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "local_date") val localDate: LocalDate,
    val meal: String,
    @ColumnInfo(name = "food_id") val foodId: String?,
    @ColumnInfo(name = "usual_meal_id") val usualMealId: Long?,
    val qty: Double,
    val kcal: Double,
    @ColumnInfo(name = "protein_g") val proteinG: Double,
    val canteen: Boolean,
    val source: String,
    // v2: a name snapshot, so the log survives deleting a usual meal.
    val name: String? = null,
    @ColumnInfo(name = "logged_at") val loggedAt: OffsetDateTime? = null,
)

@Serializable
@Entity(tableName = "body_log")
data class BodyLogEntity(
    @PrimaryKey @ColumnInfo(name = "local_date") val localDate: LocalDate,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
)

/** Key/value store for schema_version, last_evaluated_date, install_date and similar markers. */
@Serializable
@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
