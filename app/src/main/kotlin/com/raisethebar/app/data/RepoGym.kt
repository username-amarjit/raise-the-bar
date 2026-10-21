package com.raisethebar.app.data

import com.raisethebar.engine.Calendar
import com.raisethebar.engine.Equipment
import com.raisethebar.engine.EquipmentStatus
import com.raisethebar.engine.Estimates
import com.raisethebar.engine.Exercise
import com.raisethebar.engine.Gym
import com.raisethebar.engine.Injuries
import com.raisethebar.engine.InjuryRules
import com.raisethebar.engine.InjuryStatus
import com.raisethebar.engine.InputRejected
import com.raisethebar.engine.ProgramDay
import com.raisethebar.engine.Selection
import com.raisethebar.engine.Slot
import com.raisethebar.engine.Validate

suspend fun Repo.setEquipmentStatus(id: String, status: String) =
    write {
        val e = db.equipment().get(id) ?: return@write
        val today = time.today()
        db.equipment().upsert(
            e.copy(
                status = status,
                statusDate = today,
                brokenSince = if (status == EquipmentStatus.BROKEN) (e.brokenSince ?: today) else null,
                fixedAskedOn = if (status == EquipmentStatus.BROKEN) e.fixedAskedOn else null,
            ),
        )
    }

/** Saves edited weights for one item after checking every value against the exercise weight limits. */
suspend fun Repo.saveEquipment(item: Equipment) =
    write {
        val values =
            listOfNotNull(item.barKg, item.stackMin, item.stackMax, item.stackStep) + item.platePairsKg + item.dumbbellPairsKg + item.missingKg
        values.forEach { v -> if (v != 0.0) Validate.exerciseWeightKg(v) }
        val min = item.stackMin
        val max = item.stackMax
        if (min != null && max != null && max < min) throw InputRejected("stack_max", max, "at least $min")
        val current = db.equipment().get(item.id) ?: return@write
        db.equipment().upsert(item.toEntity(current.position).copy(fixedAskedOn = current.fixedAskedOn))
    }

/** Broken items to ask "Is it fixed yet?" about today (every 3 days). */
suspend fun Repo.fixedPrompts(): List<Equipment> {
    val today = time.today()
    return db.equipment().all().filter { Gym.shouldAskFixed(it.toModel(), it.fixedAskedOn, today) }.map { it.toModel() }
}

suspend fun Repo.markFixedAsked(id: String) =
    write {
        val e = db.equipment().get(id) ?: return@write
        db.equipment().upsert(e.copy(fixedAskedOn = time.today()))
    }

/** The user can promote any backup to first choice for a slot; null restores the plan's order. */
suspend fun Repo.promote(slotId: String, exerciseId: String?) = write { db.program().setPromoted(slotId, exerciseId) }

data class ExercisePage(
    val exercise: Exercise,
    val day: ProgramDay?,
    val slot: Slot?,
    val ranked: List<Exercise>,
    val outgrown: Boolean,
    val caution: String?,
    val blockReason: String?,
    val rules: InjuryRules,
    /** Why each ranked option can't be used today (null when it can). */
    val optionBlocks: Map<String, String?> = emptyMap(),
)

suspend fun Repo.exercisePage(exerciseId: String, slotId: String?): ExercisePage? {
    val lib = exercises()
    val ex = lib[exerciseId] ?: return null
    val program = program()
    val slot = slotId?.let { program.slot(it) }
    val day = slotId?.let { id -> program.days.firstOrNull { d -> d.slots.any { it.id == id } } }
    val today = time.today()
    val ctx = ctx(today)
    val est = estimates()[exerciseId]
    val loads = Gym.loadsFor(ex, ctx.gym, bodyKg())
    val repLow = slot?.repLow
    val repHigh = slot?.repHigh
    val outgrown =
        if (est != null && slot != null && repLow != null && repHigh != null && !loads.isEmpty) {
            val left = Calendar.targetLeft(Calendar.phaseOn(planStart(), today), ex.isBig)
            Estimates.nextWeight(est, repLow, repHigh, left, slot.sets, loads)?.outgrown == true
        } else {
            false
        }
    val caution =
        ctx.injuries
            .firstOrNull { (it.status == InjuryStatus.WATCH || it.isActive) && Injuries.usesArea(ex, it, rules) }
            ?.let { "Careful with your ${Injuries.areaName(it, rules).lowercase()}: stop if it hurts" }
    val ranked = slot?.ranked?.mapNotNull { lib[it] }.orEmpty()
    return ExercisePage(
        exercise = ex,
        day = day,
        slot = slot,
        ranked = ranked,
        outgrown = outgrown,
        caution = caution,
        blockReason = Selection.blockingEquipment(ex, ctx),
        rules = rules,
        optionBlocks =
            ranked.associate { o ->
                o.id to
                    (
                        Selection.blockingEquipment(o, ctx)
                            ?: Selection.blockingInjury(o, ctx)?.let { "Out while your ${Injuries.areaName(it, rules).lowercase()} heals" }
                    )
            },
    )
}
