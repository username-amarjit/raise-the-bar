package com.raisethebar.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.raisethebar.app.data.AppClock
import com.raisethebar.app.data.Repo
import com.raisethebar.app.data.Seeder
import com.raisethebar.app.db.AppDatabase
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.LocalDate
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
}
