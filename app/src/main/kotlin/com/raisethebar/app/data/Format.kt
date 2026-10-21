package com.raisethebar.app.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 47.5 -> "47.5", 45.0 -> "45", 63.333 -> "63.33". */
fun formatKg(kg: Double): String {
    val r = Math.round(kg * 100) / 100.0
    return if (r == Math.floor(r)) r.toLong().toString() else r.toString().trimEnd('0').trimEnd('.')
}

object Dates {
    private val DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)
    private val SHORT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)

    fun day(d: LocalDate): String = d.format(DAY)

    fun short(d: LocalDate): String = d.format(SHORT)

    fun weekday(d: LocalDate): String = d.format(WEEKDAY)
}
