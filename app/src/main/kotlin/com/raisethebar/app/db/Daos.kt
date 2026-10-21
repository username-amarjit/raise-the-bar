package com.raisethebar.app.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

// Every query uses bound parameters (Room generates prepared statements); no SQL is ever built from strings.

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Upsert suspend fun upsert(p: ProfileEntity)

    @Query("SELECT * FROM profile")
    suspend fun all(): List<ProfileEntity>

    @Insert suspend fun insertAll(rows: List<ProfileEntity>)

    @Query("DELETE FROM profile")
    suspend fun deleteAll()
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule ORDER BY weekday")
    suspend fun all(): List<ScheduleEntity>

    @Query("SELECT * FROM schedule ORDER BY weekday")
    fun observeAll(): Flow<List<ScheduleEntity>>

    @Upsert suspend fun upsertAll(rows: List<ScheduleEntity>)

    @Insert suspend fun insertAll(rows: List<ScheduleEntity>)

    @Query("DELETE FROM schedule")
    suspend fun deleteAll()
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM `plan` WHERE id = 1")
    suspend fun get(): PlanEntity?

    @Query("SELECT * FROM `plan` WHERE id = 1")
    fun observe(): Flow<PlanEntity?>

    @Upsert suspend fun upsert(p: PlanEntity)

    @Query("SELECT * FROM `plan`")
    suspend fun all(): List<PlanEntity>

    @Insert suspend fun insertAll(rows: List<PlanEntity>)

    @Query("DELETE FROM `plan`")
    suspend fun deleteAll()
}

@Dao
interface EquipmentDao {
    @Query("SELECT * FROM equipment ORDER BY position, name")
    suspend fun all(): List<EquipmentEntity>

    @Query("SELECT * FROM equipment ORDER BY position, name")
    fun observeAll(): Flow<List<EquipmentEntity>>

    @Query("SELECT * FROM equipment WHERE id = :id")
    suspend fun get(id: String): EquipmentEntity?

    @Upsert suspend fun upsert(e: EquipmentEntity)

    @Upsert suspend fun upsertAll(rows: List<EquipmentEntity>)

    @Insert suspend fun insertAll(rows: List<EquipmentEntity>)

    @Query("DELETE FROM equipment")
    suspend fun deleteAll()
}

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercise ORDER BY name")
    suspend fun all(): List<ExerciseEntity>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun get(id: String): ExerciseEntity?

    @Query("SELECT COUNT(*) FROM exercise")
    suspend fun count(): Int

    @Upsert suspend fun upsertAll(rows: List<ExerciseEntity>)

    @Insert suspend fun insertAll(rows: List<ExerciseEntity>)

    @Query("DELETE FROM exercise")
    suspend fun deleteAll()
}

@Dao
interface ProgramDao {
    @Query("SELECT * FROM program_day ORDER BY position")
    suspend fun days(): List<ProgramDayEntity>

    @Query("SELECT * FROM program_day WHERE id = :id")
    suspend fun day(id: String): ProgramDayEntity?

    @Query("SELECT * FROM slot ORDER BY program_day_id, position")
    suspend fun slots(): List<SlotEntity>

    @Query("SELECT * FROM slot ORDER BY program_day_id, position")
    fun observeSlots(): Flow<List<SlotEntity>>

    @Query("SELECT * FROM slot WHERE program_day_id = :dayId ORDER BY position")
    suspend fun slotsForDay(dayId: String): List<SlotEntity>

    @Query("SELECT * FROM slot WHERE id = :id")
    suspend fun slot(id: String): SlotEntity?

    @Query("UPDATE slot SET promoted_exercise_id = :exerciseId WHERE id = :slotId")
    suspend fun setPromoted(slotId: String, exerciseId: String?)

    @Upsert suspend fun upsertDays(rows: List<ProgramDayEntity>)

    @Upsert suspend fun upsertSlots(rows: List<SlotEntity>)

    @Insert suspend fun insertDays(rows: List<ProgramDayEntity>)

    @Insert suspend fun insertSlots(rows: List<SlotEntity>)

    @Query("DELETE FROM slot")
    suspend fun deleteAllSlots()

    @Query("DELETE FROM program_day")
    suspend fun deleteAllDays()
}

@Dao
interface EstimateDao {
    @Query("SELECT * FROM estimate WHERE exercise_id = :exerciseId")
    suspend fun get(exerciseId: String): EstimateEntity?

    @Query("SELECT * FROM estimate")
    suspend fun all(): List<EstimateEntity>

    @Query("SELECT * FROM estimate")
    fun observeAll(): Flow<List<EstimateEntity>>

    @Upsert suspend fun upsert(e: EstimateEntity)

    @Insert suspend fun insertAll(rows: List<EstimateEntity>)

    @Query("DELETE FROM estimate")
    suspend fun deleteAll()
}

@Dao
interface SessionDao {
    @Insert suspend fun insert(s: SessionEntity): Long

    @Update suspend fun update(s: SessionEntity)

    @Query("SELECT * FROM session WHERE id = :id")
    suspend fun get(id: Long): SessionEntity?

    @Query("SELECT * FROM session WHERE id = :id")
    fun observe(id: Long): Flow<SessionEntity?>

    /** The unfinished session, if any; it resumes exactly where it was. */
    @Query("SELECT * FROM session WHERE completed_at IS NULL AND abandoned = 0 ORDER BY started_at DESC LIMIT 1")
    suspend fun open(): SessionEntity?

    @Query("SELECT * FROM session WHERE completed_at IS NULL AND abandoned = 0 ORDER BY started_at DESC LIMIT 1")
    fun observeOpen(): Flow<SessionEntity?>

    @Query("SELECT * FROM session WHERE local_date = :date ORDER BY started_at")
    suspend fun forDate(date: LocalDate): List<SessionEntity>

    @Query("SELECT * FROM session WHERE local_date BETWEEN :from AND :to ORDER BY local_date, started_at")
    suspend fun between(from: LocalDate, to: LocalDate): List<SessionEntity>

    @Query("SELECT * FROM session WHERE completed_at IS NOT NULL ORDER BY local_date DESC, started_at DESC LIMIT 1")
    suspend fun lastCompleted(): SessionEntity?

    @Query("SELECT * FROM session WHERE completed_at IS NOT NULL ORDER BY local_date DESC, started_at DESC")
    fun observeCompleted(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM session ORDER BY id")
    suspend fun all(): List<SessionEntity>

    @Insert suspend fun insertAll(rows: List<SessionEntity>)

    @Query("DELETE FROM session")
    suspend fun deleteAll()
}

@Dao
interface SetLogDao {
    @Insert suspend fun insert(s: SetLogEntity): Long

    @Update suspend fun update(s: SetLogEntity)

    @Delete suspend fun delete(s: SetLogEntity)

    @Query("SELECT * FROM set_log WHERE session_id = :sessionId ORDER BY logged_at, id")
    suspend fun forSession(sessionId: Long): List<SetLogEntity>

    @Query("SELECT * FROM set_log WHERE session_id = :sessionId ORDER BY logged_at, id")
    fun observeForSession(sessionId: Long): Flow<List<SetLogEntity>>

    /** Sets from completed sessions in a date range, for weekly set counts. */
    @Query(
        """
        SELECT set_log.* FROM set_log JOIN session ON session.id = set_log.session_id
        WHERE session.completed_at IS NOT NULL AND session.local_date BETWEEN :from AND :to
        ORDER BY set_log.logged_at
        """,
    )
    suspend fun completedBetween(from: LocalDate, to: LocalDate): List<SetLogEntity>

    @Query("SELECT * FROM set_log ORDER BY id")
    suspend fun all(): List<SetLogEntity>

    @Insert suspend fun insertAll(rows: List<SetLogEntity>)

    @Query("DELETE FROM set_log")
    suspend fun deleteAll()
}

@Dao
interface DayLogDao {
    @Query("SELECT * FROM day_log WHERE local_date = :date")
    suspend fun get(date: LocalDate): DayLogEntity?

    @Query("SELECT * FROM day_log WHERE local_date = :date")
    fun observe(date: LocalDate): Flow<DayLogEntity?>

    @Upsert suspend fun upsert(d: DayLogEntity)

    @Query("SELECT * FROM day_log WHERE local_date BETWEEN :from AND :to ORDER BY local_date")
    suspend fun between(from: LocalDate, to: LocalDate): List<DayLogEntity>

    @Query("SELECT * FROM day_log ORDER BY local_date")
    suspend fun all(): List<DayLogEntity>

    @Query("SELECT * FROM day_log ORDER BY local_date")
    fun observeAll(): Flow<List<DayLogEntity>>

    @Insert suspend fun insertAll(rows: List<DayLogEntity>)

    @Query("DELETE FROM day_log")
    suspend fun deleteAll()
}

@Dao
interface AbsenceDao {
    @Insert suspend fun insert(a: AbsenceEntity): Long

    @Update suspend fun update(a: AbsenceEntity)

    @Query("SELECT * FROM absence WHERE id = :id")
    suspend fun get(id: Long): AbsenceEntity?

    @Query("SELECT * FROM absence ORDER BY start")
    suspend fun all(): List<AbsenceEntity>

    @Query("SELECT * FROM absence ORDER BY start")
    fun observeAll(): Flow<List<AbsenceEntity>>

    @Insert suspend fun insertAll(rows: List<AbsenceEntity>)

    @Query("DELETE FROM absence")
    suspend fun deleteAll()
}

@Dao
interface MakeupDao {
    @Query("SELECT * FROM makeup WHERE id = 1")
    suspend fun get(): MakeupEntity?

    @Query("SELECT * FROM makeup WHERE id = 1")
    fun observe(): Flow<MakeupEntity?>

    @Upsert suspend fun upsert(m: MakeupEntity)

    @Query("SELECT * FROM makeup")
    suspend fun all(): List<MakeupEntity>

    @Insert suspend fun insertAll(rows: List<MakeupEntity>)

    @Query("DELETE FROM makeup")
    suspend fun deleteAll()
}

@Dao
interface InjuryDao {
    @Insert suspend fun insert(i: InjuryEntity): Long

    @Update suspend fun update(i: InjuryEntity)

    @Query("SELECT * FROM injury WHERE id = :id")
    suspend fun get(id: Long): InjuryEntity?

    @Query("SELECT * FROM injury WHERE status != 'resolved' ORDER BY started")
    suspend fun unresolved(): List<InjuryEntity>

    @Query("SELECT * FROM injury WHERE status != 'resolved' ORDER BY started")
    fun observeUnresolved(): Flow<List<InjuryEntity>>

    @Query("SELECT * FROM injury ORDER BY id")
    suspend fun all(): List<InjuryEntity>

    @Insert suspend fun insertAll(rows: List<InjuryEntity>)

    @Query("DELETE FROM injury")
    suspend fun deleteAll()
}

@Dao
interface PainLogDao {
    @Insert suspend fun insert(p: PainLogEntity): Long

    @Query("SELECT * FROM pain_log WHERE injury_id = :injuryId ORDER BY local_date, id")
    suspend fun forInjury(injuryId: Long): List<PainLogEntity>

    @Query("SELECT * FROM pain_log WHERE injury_id = :injuryId AND local_date = :date ORDER BY id")
    suspend fun forInjuryOn(injuryId: Long, date: LocalDate): List<PainLogEntity>

    @Query("SELECT * FROM pain_log ORDER BY id")
    suspend fun all(): List<PainLogEntity>

    @Insert suspend fun insertAll(rows: List<PainLogEntity>)

    @Query("DELETE FROM pain_log")
    suspend fun deleteAll()
}

@Dao
interface FoodDao {
    @Query("SELECT * FROM food ORDER BY position, name")
    suspend fun all(): List<FoodEntity>

    @Query("SELECT * FROM food ORDER BY position, name")
    fun observeAll(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun get(id: String): FoodEntity?

    @Upsert suspend fun upsertAll(rows: List<FoodEntity>)

    @Insert suspend fun insertAll(rows: List<FoodEntity>)

    @Query("DELETE FROM food")
    suspend fun deleteAll()
}

@Dao
interface UsualMealDao {
    @Insert suspend fun insert(m: UsualMealEntity): Long

    @Update suspend fun update(m: UsualMealEntity)

    @Delete suspend fun delete(m: UsualMealEntity)

    @Query("SELECT * FROM usual_meal WHERE id = :id")
    suspend fun get(id: Long): UsualMealEntity?

    @Query("SELECT * FROM usual_meal ORDER BY id")
    suspend fun all(): List<UsualMealEntity>

    @Query("SELECT * FROM usual_meal ORDER BY id")
    fun observeAll(): Flow<List<UsualMealEntity>>

    @Insert suspend fun insertAll(rows: List<UsualMealEntity>)

    @Query("DELETE FROM usual_meal")
    suspend fun deleteAll()
}

@Dao
interface FoodLogDao {
    @Insert suspend fun insert(f: FoodLogEntity): Long

    @Update suspend fun update(f: FoodLogEntity)

    @Delete suspend fun delete(f: FoodLogEntity)

    @Query("SELECT * FROM food_log WHERE local_date = :date ORDER BY id")
    suspend fun forDate(date: LocalDate): List<FoodLogEntity>

    @Query("SELECT * FROM food_log WHERE local_date = :date ORDER BY id")
    fun observeForDate(date: LocalDate): Flow<List<FoodLogEntity>>

    @Query("SELECT * FROM food_log ORDER BY id")
    suspend fun all(): List<FoodLogEntity>

    @Insert suspend fun insertAll(rows: List<FoodLogEntity>)

    @Query("DELETE FROM food_log")
    suspend fun deleteAll()
}

@Dao
interface BodyLogDao {
    @Upsert suspend fun upsert(b: BodyLogEntity)

    @Query("SELECT * FROM body_log WHERE local_date = :date")
    suspend fun get(date: LocalDate): BodyLogEntity?

    @Query("SELECT * FROM body_log WHERE local_date BETWEEN :from AND :to ORDER BY local_date")
    suspend fun between(from: LocalDate, to: LocalDate): List<BodyLogEntity>

    @Query("SELECT * FROM body_log ORDER BY local_date DESC LIMIT 1")
    suspend fun latest(): BodyLogEntity?

    @Query("SELECT * FROM body_log ORDER BY local_date")
    suspend fun all(): List<BodyLogEntity>

    @Query("SELECT * FROM body_log ORDER BY local_date")
    fun observeAll(): Flow<List<BodyLogEntity>>

    @Insert suspend fun insertAll(rows: List<BodyLogEntity>)

    @Query("DELETE FROM body_log")
    suspend fun deleteAll()
}

@Dao
interface MetaDao {
    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM meta WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(m: MetaEntity)

    @Query("DELETE FROM meta WHERE `key` = :key")
    suspend fun remove(key: String)

    @Query("SELECT * FROM meta ORDER BY `key`")
    suspend fun all(): List<MetaEntity>

    @Insert suspend fun insertAll(rows: List<MetaEntity>)

    @Query("DELETE FROM meta")
    suspend fun deleteAll()
}
