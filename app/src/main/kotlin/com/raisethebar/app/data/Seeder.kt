package com.raisethebar.app.data

import androidx.room.withTransaction
import com.raisethebar.app.db.AppDatabase
import com.raisethebar.app.db.MetaEntity
import com.raisethebar.engine.SeedData

/**
 * Loads the bundled library into the database on first launch, and again whenever the bundled data changes
 * (for example after an app update). The user's own data (gym, promoted first choices, logs) is never overwritten.
 */
class Seeder(
    private val db: AppDatabase,
    private val time: AppClock,
) {
    suspend fun seedIfNeeded() {
        val version = seedVersion()
        if (db.meta().get(KEY_SEED_VERSION) == version) return
        db.withTransaction {
            db.exercise().upsertAll(SeedData.exercises.map { it.toEntity() })

            val promoted = db.program().slots().associate { it.id to it.promotedExerciseId }
            val lib = SeedData.exercisesById
            db.program().upsertDays(SeedData.program.days.mapIndexed { i, d -> d.toEntity(i) })
            db.program().upsertSlots(
                SeedData.program.days.flatMap { d ->
                    d.slots.map { s -> s.toEntity(d.id, lib[s.options.first()]?.isBig == true, promoted[s.id]) }
                },
            )

            if (db.equipment().all().isEmpty()) {
                db.equipment().upsertAll(SeedData.equipment.mapIndexed { i, e -> e.toEntity(i) })
            }
            seedFood(db)

            if (db.meta().get(Meta.INSTALL_DATE) == null) db.meta().put(MetaEntity(Meta.INSTALL_DATE, time.today().toString()))
            db.meta().put(MetaEntity(Meta.SCHEMA_VERSION, AppDatabase.VERSION.toString()))
            db.meta().put(MetaEntity(KEY_SEED_VERSION, version))
        }
    }

    companion object {
        const val KEY_SEED_VERSION = "seed_version"

        /** Changes whenever any bundled data file changes, so updated data reaches existing installs. */
        fun seedVersion(): String =
            listOf("exercises.json", "program.json", "equipment.json", "foods.json")
                .joinToString("-") { name -> runCatching { SeedData.text(name).hashCode().toString(16) }.getOrDefault("0") }
    }
}

/** Keys used in the meta table. */
object Meta {
    const val SCHEMA_VERSION = "schema_version"
    const val INSTALL_DATE = "install_date"
    const val LAST_EVALUATED_DATE = "last_evaluated_date"
    const val SETUP_DONE = "setup_done"
    const val CHECKINS_SCHEDULED = "checkins_scheduled"
    const val MORNING_CHECK_DATE = "morning_check_date"
    const val FIXED_ASKED = "fixed_asked"
}
