package com.raisethebar.engine

import com.raisethebar.config.Rules
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidateTest {
    @Test
    fun `accepts the edges of every section 5 limit`() {
        Validate.exerciseWeightKg(0.5)
        Validate.exerciseWeightKg(500.0)
        Validate.reps(0)
        Validate.reps(50)
        Validate.repsLeft(0)
        Validate.repsLeft(Rules.REPS_LEFT_MAX)
        Validate.holdSeconds(5)
        Validate.holdSeconds(300)
        Validate.pain(0)
        Validate.pain(10)
        Validate.bodyWeightKg(30.0)
        Validate.bodyWeightKg(250.0)
        Validate.heightCm(120.0)
        Validate.heightCm(230.0)
        Validate.waistCm(50.0)
        Validate.waistCm(200.0)
        Validate.age(16)
        Validate.age(90)
        Validate.minutes(10)
        Validate.minutes(120)
    }

    @Test
    fun `rejects anything outside`() {
        assertFailsWith<InputRejected> { Validate.exerciseWeightKg(0.49) }
        assertFailsWith<InputRejected> { Validate.exerciseWeightKg(Double.NaN) }
        assertFailsWith<InputRejected> { Validate.reps(51) }
        assertFailsWith<InputRejected> { Validate.repsLeft(5) }
        assertFailsWith<InputRejected> { Validate.holdSeconds(301) }
        assertFailsWith<InputRejected> { Validate.pain(-1) }
        assertFailsWith<InputRejected> { Validate.bodyWeightKg(29.0) }
        assertFailsWith<InputRejected> { Validate.heightCm(231.0) }
        assertFailsWith<InputRejected> { Validate.waistCm(49.0) }
        assertFailsWith<InputRejected> { Validate.age(91) }
        assertFailsWith<InputRejected> { Validate.minutes(9) }
        assertFalse(Validate.isMinutes(121))
        assertTrue(Validate.isAge(30))
    }
}

class TimeTest {
    private val india = ZoneId.of("Asia/Kolkata")

    @Test
    fun `dates are local YYYY-MM-DD strings`() {
        assertEquals("2026-10-21", Time.date(LocalDate.of(2026, 10, 21)))
        assertEquals(LocalDate.of(2026, 2, 28), Time.parseDate("2026-02-28"))
    }

    @Test
    fun `timestamps are ISO with offset and round-trip`() {
        val t = OffsetDateTime.of(2026, 10, 21, 0, 10, 0, 0, ZoneOffset.ofHoursMinutes(5, 30))
        assertEquals("2026-10-21T00:10:00.000+05:30", Time.timestamp(t))
        assertEquals(t, Time.parseTimestamp(Time.timestamp(t)))
    }

    @Test
    fun `a day ends at local midnight, including month ends`() {
        assertEquals("2026-11-01T00:00+05:30[Asia/Kolkata]", Time.endOfDay(LocalDate.of(2026, 10, 31), india).toString())
        val justBefore = OffsetDateTime.of(2026, 10, 31, 23, 59, 59, 0, ZoneOffset.ofHoursMinutes(5, 30))
        assertEquals(LocalDate.of(2026, 10, 31), Time.localDateOf(justBefore, india))
        assertEquals(LocalDate.of(2026, 11, 1), Time.localDateOf(justBefore.plusSeconds(1), india))
    }
}
