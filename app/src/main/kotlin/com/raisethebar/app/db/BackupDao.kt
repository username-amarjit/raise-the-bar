package com.raisethebar.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Whole-table reads, deletes and inserts for export and import (milestone 13). Callers run them in one transaction. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM profile ORDER BY id")
    suspend fun profile(): List<ProfileEntity>

    @Query("SELECT * FROM schedule ORDER BY weekday")
    suspend fun schedule(): List<ScheduleEntity>

    @Query("SELECT * FROM plan ORDER BY id")
    suspend fun plan(): List<PlanEntity>

    @Query("SELECT * FROM equipment ORDER BY id")
    suspend fun equipment(): List<EquipmentEntity>

    @Query("SELECT * FROM exercise ORDER BY id")
    suspend fun exercise(): List<ExerciseEntity>

    @Query("SELECT * FROM program_day ORDER BY id")
    suspend fun programDay(): List<ProgramDayEntity>

    @Query("SELECT * FROM slot ORDER BY id")
    suspend fun slot(): List<SlotEntity>

    @Query("SELECT * FROM estimate ORDER BY exercise_id")
    suspend fun estimate(): List<EstimateEntity>

    @Query("SELECT * FROM session ORDER BY id")
    suspend fun session(): List<SessionEntity>

    @Query("SELECT * FROM set_log ORDER BY id")
    suspend fun setLog(): List<SetLogEntity>

    @Query("SELECT * FROM day_log ORDER BY local_date")
    suspend fun dayLog(): List<DayLogEntity>

    @Query("SELECT * FROM absence ORDER BY id")
    suspend fun absence(): List<AbsenceEntity>

    @Query("SELECT * FROM makeup ORDER BY id")
    suspend fun makeup(): List<MakeupEntity>

    @Query("SELECT * FROM injury ORDER BY id")
    suspend fun injury(): List<InjuryEntity>

    @Query("SELECT * FROM pain_log ORDER BY id")
    suspend fun painLog(): List<PainLogEntity>

    @Query("SELECT * FROM food ORDER BY id")
    suspend fun food(): List<FoodEntity>

    @Query("SELECT * FROM usual_meal ORDER BY id")
    suspend fun usualMeal(): List<UsualMealEntity>

    @Query("SELECT * FROM food_log ORDER BY id")
    suspend fun foodLog(): List<FoodLogEntity>

    @Query("SELECT * FROM body_log ORDER BY local_date")
    suspend fun bodyLog(): List<BodyLogEntity>

    @Query("SELECT * FROM meta ORDER BY `key`")
    suspend fun meta(): List<MetaEntity>

    // Deletes, children before parents.

    @Query("DELETE FROM set_log")
    suspend fun clearSetLog()

    @Query("DELETE FROM pain_log")
    suspend fun clearPainLog()

    @Query("DELETE FROM slot")
    suspend fun clearSlot()

    @Query("DELETE FROM estimate")
    suspend fun clearEstimate()

    @Query("DELETE FROM session")
    suspend fun clearSession()

    @Query("DELETE FROM injury")
    suspend fun clearInjury()

    @Query("DELETE FROM program_day")
    suspend fun clearProgramDay()

    @Query("DELETE FROM exercise")
    suspend fun clearExercise()

    @Query("DELETE FROM profile")
    suspend fun clearProfile()

    @Query("DELETE FROM schedule")
    suspend fun clearSchedule()

    @Query("DELETE FROM plan")
    suspend fun clearPlan()

    @Query("DELETE FROM equipment")
    suspend fun clearEquipment()

    @Query("DELETE FROM day_log")
    suspend fun clearDayLog()

    @Query("DELETE FROM absence")
    suspend fun clearAbsence()

    @Query("DELETE FROM makeup")
    suspend fun clearMakeup()

    @Query("DELETE FROM food_log")
    suspend fun clearFoodLog()

    @Query("DELETE FROM usual_meal")
    suspend fun clearUsualMeal()

    @Query("DELETE FROM food")
    suspend fun clearFood()

    @Query("DELETE FROM body_log")
    suspend fun clearBodyLog()

    @Query("DELETE FROM meta")
    suspend fun clearMeta()

    // Inserts; parents go in before children.

    @Insert
    suspend fun insertProfile(rows: List<ProfileEntity>)

    @Insert
    suspend fun insertSchedule(rows: List<ScheduleEntity>)

    @Insert
    suspend fun insertPlan(rows: List<PlanEntity>)

    @Insert
    suspend fun insertEquipment(rows: List<EquipmentEntity>)

    @Insert
    suspend fun insertExercise(rows: List<ExerciseEntity>)

    @Insert
    suspend fun insertProgramDay(rows: List<ProgramDayEntity>)

    @Insert
    suspend fun insertSlot(rows: List<SlotEntity>)

    @Insert
    suspend fun insertEstimate(rows: List<EstimateEntity>)

    @Insert
    suspend fun insertSession(rows: List<SessionEntity>)

    @Insert
    suspend fun insertSetLog(rows: List<SetLogEntity>)

    @Insert
    suspend fun insertDayLog(rows: List<DayLogEntity>)

    @Insert
    suspend fun insertAbsence(rows: List<AbsenceEntity>)

    @Insert
    suspend fun insertMakeup(rows: List<MakeupEntity>)

    @Insert
    suspend fun insertInjury(rows: List<InjuryEntity>)

    @Insert
    suspend fun insertPainLog(rows: List<PainLogEntity>)

    @Insert
    suspend fun insertFood(rows: List<FoodEntity>)

    @Insert
    suspend fun insertUsualMeal(rows: List<UsualMealEntity>)

    @Insert
    suspend fun insertFoodLog(rows: List<FoodLogEntity>)

    @Insert
    suspend fun insertBodyLog(rows: List<BodyLogEntity>)

    @Insert
    suspend fun insertMeta(rows: List<MetaEntity>)
}
