@file:UseSerializers(LocalDateSerializer::class)

package com.raisethebar.engine

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

/** The muscle groups the app tracks (section 7). Shoulder presses train the front delts, which aren't tracked. */
object Groups {
    const val CHEST = "chest"
    const val BACK = "back"
    const val DELTS = "delts"
    const val BICEPS = "biceps"
    const val TRICEPS = "triceps"
    const val QUADS = "quads"
    const val HAMS_GLUTES = "hams_glutes"
    const val CALVES = "calves"
    const val CORE = "core"

    val ALL = listOf(CHEST, BACK, DELTS, BICEPS, TRICEPS, QUADS, HAMS_GLUTES, CALVES, CORE)

    val LABEL =
        mapOf(
            CHEST to "Chest",
            BACK to "Back",
            DELTS to "Side and rear delts",
            BICEPS to "Biceps",
            TRICEPS to "Triceps",
            QUADS to "Quads",
            HAMS_GLUTES to "Hamstrings and glutes",
            CALVES to "Calves",
            CORE to "Core",
        )

    fun label(id: String): String = LABEL[id] ?: id
}

/** How an exercise is loaded, which decides its available weights and steps. */
object LoadKind {
    const val BARBELL = "barbell"
    const val DUMBBELL = "dumbbell"
    const val STACK = "stack"
    const val BODYWEIGHT = "bodyweight"
    const val ASSISTED = "assisted"
    const val NONE = "none"
}

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val countsToward: List<String> = emptyList(),
    val equipmentIds: List<String> = emptyList(),
    val bigOrSmall: String = SMALL,
    val stresses: List<String> = emptyList(),
    val cues: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val images: List<String> = emptyList(),
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val unilateral: Boolean = false,
    val oneSidedVersionId: String? = null,
    val perSide: Boolean = false,
    val timed: Boolean = false,
    val bodyweightBased: Boolean = false,
    val assisted: Boolean = false,
    val loadKind: String = LoadKind.NONE,
    val bodyRegion: String = UPPER,
    val stepKg: Double? = null,
    val source: String = "custom",
    val sourceId: String? = null,
    val licence: String = "",
) {
    val isBig: Boolean get() = bigOrSmall == BIG

    /** True when the app prescribes a weight for this exercise. */
    val isLoaded: Boolean get() = loadKind != LoadKind.NONE

    companion object {
        const val BIG = "big"
        const val SMALL = "small"
        const val UPPER = "upper"
        const val LOWER = "lower"
    }
}

/** One slot of a program day: sets, a rep (or seconds) range, rest, and ranked exercise options. */
@Serializable
data class Slot(
    val id: String,
    val position: Int,
    val sets: Int,
    val repLow: Int? = null,
    val repHigh: Int? = null,
    val secLow: Int? = null,
    val secHigh: Int? = null,
    val restS: Int? = null,
    val stopLeft: Int? = null,
    val options: List<String>,
    val note: String? = null,
    val promotedExerciseId: String? = null,
) {
    val isTimed: Boolean get() = secLow != null

    /** Ranked options, with the user's promoted first choice moved to the front. */
    val ranked: List<String>
        get() {
            val p = promotedExerciseId ?: return options
            return if (p in options) listOf(p) + options.filter { it != p } else options
        }

    val firstChoice: String get() = ranked.first()
}

object DayKind {
    const val ROTATION = "rotation"
    const val HOME = "home"
    const val AWAY = "away"
    const val PRE_TRIP = "pre_trip"
}

object DayType {
    const val PUSH = "push"
    const val PULL = "pull"
    const val LEGS = "legs"
    const val FULL = "full"
}

@Serializable
data class ProgramDay(
    val id: String,
    val name: String,
    val focus: String,
    val kind: String = DayKind.ROTATION,
    val dayType: String,
    val minutes: Int? = null,
    val slots: List<Slot>,
)

@Serializable
data class Program(
    val rotation: List<String>,
    val restDayText: String,
    val weeklyTargets: Map<String, Int>,
    val homeFor: Map<String, String>,
    val awayTemplates: Map<String, String>,
    val preTrip: String,
    val makeupCandidates: Map<String, List<String>>,
    val days: List<ProgramDay>,
) {
    fun day(id: String): ProgramDay = days.first { it.id == id }

    fun dayOrNull(id: String): ProgramDay? = days.firstOrNull { it.id == id }

    fun rotationDay(index: Int): ProgramDay = day(rotation[Math.floorMod(index, rotation.size)])

    fun homeDay(dayType: String): ProgramDay? = homeFor[dayType]?.let(::dayOrNull)

    fun slot(id: String): Slot? = days.asSequence().flatMap { it.slots.asSequence() }.firstOrNull { it.id == id }

    /** The program with the user's promoted first choices applied (slot id -> exercise id). */
    fun withPromotions(promoted: Map<String, String?>): Program =
        if (promoted.isEmpty()) {
            this
        } else {
            copy(days = days.map { d -> d.copy(slots = d.slots.map { s -> s.copy(promotedExerciseId = promoted[s.id]) }) })
        }

    /** The rep range an exercise has in the program: first where it is a first choice, else any slot, else null. */
    fun repRangeFor(exerciseId: String): Pair<Int, Int>? {
        val rotationSlots = rotation.flatMap { day(it).slots }
        val slot =
            rotationSlots.firstOrNull { it.options.firstOrNull() == exerciseId && it.repLow != null }
                ?: rotationSlots.firstOrNull { exerciseId in it.options && it.repLow != null }
                ?: return null
        return slot.repLow!! to slot.repHigh!!
    }
}

object EquipmentStatus {
    const val WORKING = "working"
    const val BUSY = "busy"
    const val BROKEN = "broken"
    const val GONE = "gone"

    val ALL = listOf(WORKING, BUSY, BROKEN, GONE)
}

object EquipmentKind {
    const val BARBELL = "barbell"
    const val SMITH = "smith"
    const val DUMBBELL = "dumbbell"
    const val MACHINE = "machine"
    const val CABLE = "cable"
    const val BENCH = "bench"
    const val BODYWEIGHT = "bodyweight"
    const val OTHER = "other"
}

@Serializable
data class Equipment(
    val id: String,
    val name: String,
    val kind: String,
    val section: String = "",
    val note: String? = null,
    val status: String = EquipmentStatus.WORKING,
    val statusDate: LocalDate? = null,
    val brokenSince: LocalDate? = null,
    val barKg: Double? = null,
    val platePairsKg: List<Double> = emptyList(),
    val dumbbellPairsKg: List<Double> = emptyList(),
    val stackMin: Double? = null,
    val stackMax: Double? = null,
    val stackStep: Double? = null,
    val missingKg: List<Double> = emptyList(),
) {
    /** Busy only counts on the day it was set: it resets at midnight. Broken and gone items are never usable. */
    fun usableOn(day: LocalDate): Boolean =
        when (status) {
            EquipmentStatus.WORKING -> true
            EquipmentStatus.BUSY -> statusDate != day
            else -> false
        }

    /** The status as it stands on [day]: an old "busy" reads as working. */
    fun statusOn(day: LocalDate): String = if (status == EquipmentStatus.BUSY && statusDate != day) EquipmentStatus.WORKING else status

    val hasStack: Boolean get() = stackMin != null && stackMax != null && stackStep != null
}
