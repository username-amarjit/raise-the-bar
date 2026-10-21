package com.raisethebar.app.data

import androidx.room.withTransaction
import com.raisethebar.app.db.AbsenceEntity
import com.raisethebar.app.db.AppDatabase
import com.raisethebar.app.db.BodyLogEntity
import com.raisethebar.app.db.MetaEntity
import com.raisethebar.app.db.PainLogEntity
import com.raisethebar.app.db.ProfileEntity
import com.raisethebar.app.db.SetLogEntity
import com.raisethebar.engine.InputRejected
import com.raisethebar.engine.Validate
import java.time.LocalDate

/**
 * The database layer the screens call. Every write checks its inputs against the limits in section 5 first and then
 * runs in one transaction, so a rejected input writes nothing.
 */
open class Store(
    val db: AppDatabase,
    val time: AppClock,
) {
    suspend fun <T> tx(block: suspend () -> T): T = db.withTransaction(block)

    suspend fun meta(key: String): String? = db.meta().get(key)

    suspend fun putMeta(key: String, value: String) = tx { db.meta().put(MetaEntity(key, value)) }

    suspend fun saveProfile(p: ProfileEntity) =
        tx {
            validateProfile(p)
            db.profile().upsert(p)
        }

    suspend fun logSet(s: SetLogEntity): Long =
        tx {
            validateSet(s)
            db.setLog().insert(s)
        }

    suspend fun updateSet(s: SetLogEntity) =
        tx {
            validateSet(s)
            db.setLog().update(s)
        }

    suspend fun logBodyWeight(date: LocalDate, kg: Double) =
        tx {
            Validate.bodyWeightKg(kg)
            db.bodyLog().upsert(BodyLogEntity(date, kg))
        }

    suspend fun logPain(p: PainLogEntity): Long =
        tx {
            Validate.pain(p.value)
            choice("pain kind", p.kind, PAIN_KINDS)
            db.painLog().insert(p)
        }

    suspend fun addAbsence(a: AbsenceEntity): Long =
        tx {
            validateAbsence(a)
            db.absence().insert(a)
        }

    companion object {
        val DIET_TYPES = setOf("veg", "egg", "non_veg")
        val GOALS = setOf("lean_bulk", "recomp")
        val PAIN_KINDS = setOf("set", "morning")
        val ABSENCE_KINDS = setOf("day_off", "away")
        val EQUIPMENT_THERE = setOf("none", "bodyweight", "dumbbells", "gym")

        fun validateProfile(p: ProfileEntity) {
            Validate.bodyWeightKg(p.weightKg)
            Validate.heightCm(p.heightCm)
            p.waistCm?.let(Validate::waistCm)
            Validate.age(p.age)
            choice("diet_type", p.dietType, DIET_TYPES)
            choice("goal", p.goal, GOALS)
            if (p.proteinTargetG !in 1..1000) throw InputRejected("protein_target_g", p.proteinTargetG, "1–1000")
            if (p.kcalTarget !in 500..10000) throw InputRejected("kcal_target", p.kcalTarget, "500–10000")
        }

        fun validateSet(s: SetLogEntity) {
            s.weightKg?.let(Validate::exerciseWeightKg)
            s.reps?.let(Validate::reps)
            s.repsLeft?.let(Validate::repsLeft)
            s.seconds?.let(Validate::holdSeconds)
            s.pain?.let(Validate::pain)
            if (s.setNo < 1) throw InputRejected("set_no", s.setNo, "1 or more")
        }

        fun validateAbsence(a: AbsenceEntity) {
            choice("absence kind", a.kind, ABSENCE_KINDS)
            a.equipmentThere?.let { choice("equipment_there", it, EQUIPMENT_THERE) }
            a.minutesPerDay?.let(Validate::minutes)
            if (a.end.isBefore(a.start)) throw InputRejected("end", a.end, "on or after ${a.start}")
        }

        private fun choice(field: String, v: String, allowed: Set<String>) {
            if (v !in allowed) throw InputRejected(field, v, allowed.joinToString(", "))
        }
    }
}
