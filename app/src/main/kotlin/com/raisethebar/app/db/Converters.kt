package com.raisethebar.app.db

import androidx.room.TypeConverter
import com.raisethebar.engine.Time
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.OffsetDateTime

/** Dates are stored as local YYYY-MM-DD text, timestamps as ISO text with offset, lists as JSON text. */
class Converters {
    private val json = Json { ignoreUnknownKeys = true }
    private val strings = ListSerializer(String.serializer())
    private val doubles = ListSerializer(Double.serializer())

    @TypeConverter
    fun dateToText(d: LocalDate?): String? = d?.let(Time::date)

    @TypeConverter
    fun textToDate(s: String?): LocalDate? = s?.let(Time::parseDate)

    @TypeConverter
    fun timestampToText(t: OffsetDateTime?): String? = t?.let(Time::timestamp)

    @TypeConverter
    fun textToTimestamp(s: String?): OffsetDateTime? = s?.let(Time::parseTimestamp)

    @TypeConverter
    fun stringsToText(v: List<String>?): String? = v?.let { json.encodeToString(strings, it) }

    @TypeConverter
    fun textToStrings(s: String?): List<String>? = s?.let { json.decodeFromString(strings, it) }

    @TypeConverter
    fun doublesToText(v: List<Double>?): String? = v?.let { json.encodeToString(doubles, it) }

    @TypeConverter
    fun textToDoubles(s: String?): List<Double>? = s?.let { json.decodeFromString(doubles, it) }
}
