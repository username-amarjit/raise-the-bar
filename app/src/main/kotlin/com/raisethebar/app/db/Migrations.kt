package com.raisethebar.app.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Numbered migrations. Every schema change gets the next number and a test in MigrationTest, so upgrading the app keeps all data.
 * Version 1 is the checklist's table list; version 2 adds the columns and date indexes the app needs.
 */
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            listOf(
                "ALTER TABLE profile ADD COLUMN targets_basis_kg REAL",
                "ALTER TABLE profile ADD COLUMN hide_in_switcher INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE equipment ADD COLUMN position INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE equipment ADD COLUMN fixed_asked_on TEXT",
                "ALTER TABLE exercise ADD COLUMN primary_muscles TEXT NOT NULL DEFAULT '[]'",
                "ALTER TABLE exercise ADD COLUMN secondary_muscles TEXT NOT NULL DEFAULT '[]'",
                "ALTER TABLE exercise ADD COLUMN body_region TEXT NOT NULL DEFAULT 'upper'",
                "ALTER TABLE exercise ADD COLUMN assisted INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE program_day ADD COLUMN kind TEXT NOT NULL DEFAULT 'rotation'",
                "ALTER TABLE program_day ADD COLUMN day_type TEXT NOT NULL DEFAULT 'push'",
                "ALTER TABLE slot ADD COLUMN stop_left INTEGER",
                "ALTER TABLE slot ADD COLUMN note TEXT",
                "ALTER TABLE slot ADD COLUMN promoted_exercise_id TEXT",
                "ALTER TABLE estimate ADD COLUMN hold_offset_s INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE session ADD COLUMN plan_week INTEGER",
                "ALTER TABLE session ADD COLUMN rotation_index INTEGER",
                "ALTER TABLE session ADD COLUMN makeup_block TEXT",
                "ALTER TABLE session ADD COLUMN makeup_logged INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE session ADD COLUMN state TEXT",
                "ALTER TABLE session ADD COLUMN abandoned INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE set_log ADD COLUMN bw_kg REAL",
                "ALTER TABLE set_log ADD COLUMN target_reps INTEGER",
                "ALTER TABLE set_log ADD COLUMN target_left INTEGER",
                "ALTER TABLE day_log ADD COLUMN later_time TEXT",
                "ALTER TABLE absence ADD COLUMN counts_as_skip INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE injury ADD COLUMN muscle TEXT",
                "ALTER TABLE injury ADD COLUMN feel TEXT",
                "ALTER TABLE injury ADD COLUMN pain_now INTEGER",
                "ALTER TABLE injury ADD COLUMN phase_started TEXT",
                "ALTER TABLE injury ADD COLUMN load_fraction REAL",
                "ALTER TABLE injury ADD COLUMN usual_morning_pain INTEGER",
                "ALTER TABLE injury ADD COLUMN resolved_at TEXT",
                "ALTER TABLE pain_log ADD COLUMN exercise_id TEXT",
                "ALTER TABLE pain_log ADD COLUMN set_log_id INTEGER",
                "ALTER TABLE food ADD COLUMN short_name TEXT",
                "ALTER TABLE food ADD COLUMN position INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE usual_meal ADD COLUMN seeded INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE food_log ADD COLUMN name TEXT",
                "ALTER TABLE food_log ADD COLUMN logged_at TEXT",
                "CREATE INDEX IF NOT EXISTS `index_session_local_date` ON `session` (`local_date`)",
                "CREATE INDEX IF NOT EXISTS `index_set_log_exercise_id` ON `set_log` (`exercise_id`)",
                "CREATE INDEX IF NOT EXISTS `index_absence_start` ON `absence` (`start`)",
                "CREATE INDEX IF NOT EXISTS `index_absence_end` ON `absence` (`end`)",
                "CREATE INDEX IF NOT EXISTS `index_pain_log_local_date` ON `pain_log` (`local_date`)",
                "CREATE INDEX IF NOT EXISTS `index_food_log_local_date` ON `food_log` (`local_date`)",
            ).forEach(db::execSQL)
        }
    }

val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            listOf(
                "ALTER TABLE exercise ADD COLUMN load_kind TEXT NOT NULL DEFAULT 'none'",
                "ALTER TABLE exercise ADD COLUMN source_id TEXT",
                "ALTER TABLE equipment ADD COLUMN section TEXT NOT NULL DEFAULT ''",
                "ALTER TABLE equipment ADD COLUMN note TEXT",
            ).forEach(db::execSQL)
        }
    }

val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
