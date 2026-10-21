package com.raisethebar.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.raisethebar.app.data.AppClock
import com.raisethebar.app.data.Repo
import com.raisethebar.app.data.Seeder
import com.raisethebar.app.data.SetupInput
import com.raisethebar.app.data.completeSetup
import com.raisethebar.app.db.AppDatabase
import com.raisethebar.engine.Goal
import com.raisethebar.engine.MakeupSettings
import com.raisethebar.engine.SeedData
import com.raisethebar.engine.Weekdays
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A Repo on a seeded in-memory database with a fixed India-time clock, for screen tests. */
object TestRepo {
    val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    fun create(date: LocalDate = LocalDate.of(2026, 10, 21), hour: Int = 8): Repo {
        val db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val repo = Repo(db, AppClock(Clock.fixed(date.atTime(hour, 0).atZone(zone).toInstant(), zone)))
        runBlocking { Seeder(db, repo.time).seedIfNeeded() }
        return repo
    }

    /** A Repo where first-launch setup is already done (70 kg, Monday to Saturday at 07:30, default gym). */
    fun createSetUp(date: LocalDate = LocalDate.of(2026, 10, 21), hour: Int = 8): Repo {
        val repo = create(date, hour)
        runBlocking {
            repo.completeSetup(
                SetupInput(
                    weightKg = 70.0,
                    heightCm = 175.0,
                    waistCm = 85.0,
                    age = 30,
                    dietType = "veg",
                    experience = "new",
                    goal = Goal.LEAN_BULK,
                    proteinTargetG = 120,
                    kcalTarget = 2500,
                    schedule = Weekdays.defaults(),
                    equipment = SeedData.equipment,
                    dayOffCutoff = LocalTime.of(22, 0),
                    makeup = MakeupSettings(),
                    notificationsOn = false,
                    oldInjuries = emptyList(),
                ),
            )
        }
        return repo
    }
}
