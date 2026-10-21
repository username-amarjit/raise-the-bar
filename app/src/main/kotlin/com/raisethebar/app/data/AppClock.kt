package com.raisethebar.app.data

import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/** The single source of "now" for the app; tests swap in a fixed clock. */
class AppClock(
    val clock: Clock,
) {
    val zone: ZoneId get() = clock.zone

    fun now(): OffsetDateTime = OffsetDateTime.now(clock)

    fun today(): LocalDate = LocalDate.now(clock)

    companion object {
        fun system() = AppClock(Clock.systemDefaultZone())
    }
}
