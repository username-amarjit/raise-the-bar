package com.raisethebar.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    version = AppDatabase.VERSION,
    exportSchema = true,
    entities = [
        ProfileEntity::class, ScheduleEntity::class, PlanEntity::class, EquipmentEntity::class, ExerciseEntity::class,
        ProgramDayEntity::class, SlotEntity::class, EstimateEntity::class, SessionEntity::class, SetLogEntity::class,
        DayLogEntity::class, AbsenceEntity::class, MakeupEntity::class, InjuryEntity::class, PainLogEntity::class,
        FoodEntity::class, UsualMealEntity::class, FoodLogEntity::class, BodyLogEntity::class, MetaEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profile(): ProfileDao

    abstract fun schedule(): ScheduleDao

    abstract fun plan(): PlanDao

    abstract fun equipment(): EquipmentDao

    abstract fun exercise(): ExerciseDao

    abstract fun program(): ProgramDao

    abstract fun estimate(): EstimateDao

    abstract fun session(): SessionDao

    abstract fun setLog(): SetLogDao

    abstract fun dayLog(): DayLogDao

    abstract fun absence(): AbsenceDao

    abstract fun makeup(): MakeupDao

    abstract fun injury(): InjuryDao

    abstract fun painLog(): PainLogDao

    abstract fun food(): FoodDao

    abstract fun usualMeal(): UsualMealDao

    abstract fun foodLog(): FoodLogDao

    abstract fun bodyLog(): BodyLogDao

    abstract fun meta(): MetaDao

    companion object {
        const val VERSION = 2
        const val NAME = "raise-the-bar.db"

        fun build(context: Context): AppDatabase =
            Room
                .databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
