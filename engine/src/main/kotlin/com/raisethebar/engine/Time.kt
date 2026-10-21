package com.raisethebar.engine

import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Dates are local `YYYY-MM-DD` strings, times are ISO timestamps with offset, and a day ends at local midnight.
 */
object Time {
    private val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun date(d: LocalDate): String = d.toString()

    fun parseDate(s: String): LocalDate = LocalDate.parse(s)

    fun timestamp(t: OffsetDateTime): String = t.format(TIMESTAMP)

    fun parseTimestamp(s: String): OffsetDateTime = OffsetDateTime.parse(s)

    fun hhmm(t: LocalTime): String = t.format(HHMM)

    fun parseHhmm(s: String): LocalTime = LocalTime.parse(s, HHMM)

    /** The instant a local day ends: the following local midnight. */
    fun endOfDay(d: LocalDate, zone: ZoneId): ZonedDateTime = d.plusDays(1).atStartOfDay(zone)

    /** The local date a timestamp falls on, in the given zone. */
    fun localDateOf(t: OffsetDateTime, zone: ZoneId): LocalDate = t.atZoneSameInstant(zone).toLocalDate()
}
