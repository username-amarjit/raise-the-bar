package com.raisethebar.app.data

import com.raisethebar.app.db.InjuryEntity
import com.raisethebar.app.db.MetaEntity
import com.raisethebar.app.db.PlanEntity
import com.raisethebar.app.db.ProfileEntity
import com.raisethebar.app.db.ScheduleEntity
import com.raisethebar.engine.Equipment
import com.raisethebar.engine.InjuryStatus
import com.raisethebar.engine.MakeupSettings
import com.raisethebar.engine.MakeupState
import com.raisethebar.engine.Nutrition
import com.raisethebar.engine.ScheduleDay
import com.raisethebar.engine.SeedData
import com.raisethebar.engine.Time
import java.time.LocalTime

data class OldInjury(
    val area: String,
    val side: String,
    val muscle: String?,
    val hurtsNow: Boolean,
)

/** Everything the five setup screens collect. */
data class SetupInput(
    val weightKg: Double,
    val heightCm: Double,
    val waistCm: Double?,
    val age: Int,
    val dietType: String,
    val experience: String,
    val goal: String,
    val proteinTargetG: Int,
    val kcalTarget: Int,
    val schedule: List<ScheduleDay>,
    val equipment: List<Equipment>,
    val dayOffCutoff: LocalTime,
    val makeup: MakeupSettings,
    val notificationsOn: Boolean,
    val oldInjuries: List<OldInjury>,
)

/**
 * Finishes first-launch setup. Old injuries that don't hurt now are saved as `watch`; the ones that do are
 * returned so the app can open injury triage for each after setup.
 */
suspend fun Repo.completeSetup(input: SetupInput): List<OldInjury> =
    write {
        val profile =
            ProfileEntity(
                weightKg = input.weightKg,
                heightCm = input.heightCm,
                waistCm = input.waistCm,
                age = input.age,
                dietType = input.dietType,
                experience = input.experience,
                goal = input.goal,
                proteinTargetG = input.proteinTargetG,
                kcalTarget = input.kcalTarget,
                dayOffCutoff = Time.hhmm(input.dayOffCutoff),
                makeupSettings = SeedData.json.encodeToString(MakeupSettings.serializer(), input.makeup.clamped()),
                notificationsOn = input.notificationsOn,
                targetsBasisKg = input.weightKg,
            )
        Store.validateProfile(profile)
        db.profile().upsert(profile)
        writeSchedule(input.schedule)
        writeEquipment(input.equipment)
        val today = time.today()
        if (db.plan().get() == null) db.plan().upsert(PlanEntity(template = "ppl6", planStart = today, rotationIndex = 0, previous = null))
        if (db.makeup().get() == null) saveMakeup(MakeupState())
        input.oldInjuries.filter { !it.hurtsNow }.forEach { old ->
            db.injury().insert(
                InjuryEntity(
                    area = old.area,
                    side = old.side,
                    status = InjuryStatus.WATCH,
                    phase = 1,
                    started = today,
                    redFlag = false,
                    checkedByDoctor = false,
                    preInjury = "{}",
                    greenStreak = 0,
                    muscle = old.muscle,
                ),
            )
        }
        db.meta().put(MetaEntity(Meta.SETUP_DONE, "1"))
        db.meta().put(MetaEntity(Meta.LAST_EVALUATED_DATE, today.minusDays(1).toString()))
        input.oldInjuries.filter { it.hurtsNow }
    }

private suspend fun Repo.writeSchedule(days: List<ScheduleDay>) {
    db.schedule().upsertAll(days.map { ScheduleEntity(it.weekday, it.training, Time.hhmm(it.time)) })
}

private suspend fun Repo.writeEquipment(items: List<Equipment>) {
    val positions = db.equipment().all().associate { it.id to it.position }
    db.equipment().upsertAll(items.mapIndexed { i, e -> e.toEntity(positions[e.id] ?: i) })
}

suspend fun Repo.saveSchedule(days: List<ScheduleDay>) = write { writeSchedule(days) }

suspend fun Repo.updateProfile(edit: (ProfileEntity) -> ProfileEntity) =
    write {
        val current = db.profile().get() ?: return@write
        val next = edit(current)
        Store.validateProfile(next)
        db.profile().upsert(next)
    }

suspend fun Repo.saveRules(cutoff: LocalTime, makeup: MakeupSettings) =
    updateProfile {
        it.copy(dayOffCutoff = Time.hhmm(cutoff), makeupSettings = SeedData.json.encodeToString(MakeupSettings.serializer(), makeup.clamped()))
    }

/** Protein target and calorie guess as in section 5, from the given body weight and goal. */
fun suggestedTargets(weightKg: Double, goal: String): Pair<Int, Int> = Nutrition.proteinTarget(weightKg) to Nutrition.kcalGuess(weightKg, goal)
