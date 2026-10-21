package com.raisethebar.engine

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * The seed data bundled with the app (the JSON files in /data, packaged under rtb-data/ in the engine jar).
 * Read-only: user changes live in the database.
 */
@OptIn(ExperimentalSerializationApi::class)
object SeedData {
    val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            namingStrategy = JsonNamingStrategy.SnakeCase
        }

    fun text(name: String): String {
        val stream = SeedData::class.java.getResourceAsStream("/rtb-data/$name")
        requireNotNull(stream) { "Bundled data file $name is missing" }
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    val exercises: List<Exercise> by lazy { json.decodeFromString(text("exercises.json")) }

    val exercisesById: Map<String, Exercise> by lazy { exercises.associateBy { it.id } }

    val program: Program by lazy { json.decodeFromString(text("program.json")) }

    val equipment: List<Equipment> by lazy { json.decodeFromString(text("equipment.json")) }

    val foods: FoodSeedFile by lazy { json.decodeFromString(text("foods.json")) }

    val injuryRules: InjuryRules by lazy { json.decodeFromString(text("injury_rules.json")) }
}
