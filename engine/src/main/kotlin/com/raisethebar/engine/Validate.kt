package com.raisethebar.engine

import com.raisethebar.config.Rules.Limits

/** Thrown when an input is outside the limits in section 5. Nothing is written when this happens. */
class InputRejected(
    val field: String,
    val value: Any?,
    val allowed: String,
) : IllegalArgumentException("$field = $value is outside $allowed")

/** Input limits from section 5: reject anything outside. */
object Validate {
    fun exerciseWeightKg(kg: Double): Double = inRange("weight_kg", kg, Limits.EXERCISE_WEIGHT_KG)

    fun reps(n: Int): Int = inRange("reps", n, Limits.REPS)

    fun repsLeft(n: Int): Int = inRange("reps_left", n, Limits.REPS_LEFT)

    fun holdSeconds(s: Int): Int = inRange("seconds", s, Limits.HOLD_S)

    fun pain(p: Int): Int = inRange("pain", p, Limits.PAIN)

    fun bodyWeightKg(kg: Double): Double = inRange("body_weight_kg", kg, Limits.BODY_WEIGHT_KG)

    fun heightCm(cm: Double): Double = inRange("height_cm", cm, Limits.HEIGHT_CM)

    fun waistCm(cm: Double): Double = inRange("waist_cm", cm, Limits.WAIST_CM)

    fun age(years: Int): Int = inRange("age", years, Limits.AGE)

    fun minutes(m: Int): Int = inRange("minutes", m, Limits.MINUTES)

    fun isExerciseWeightKg(kg: Double) = kg in Limits.EXERCISE_WEIGHT_KG

    fun isBodyWeightKg(kg: Double) = kg in Limits.BODY_WEIGHT_KG

    fun isHeightCm(cm: Double) = cm in Limits.HEIGHT_CM

    fun isWaistCm(cm: Double) = cm in Limits.WAIST_CM

    fun isAge(years: Int) = years in Limits.AGE

    fun isMinutes(m: Int) = m in Limits.MINUTES

    private fun inRange(field: String, v: Double, range: ClosedFloatingPointRange<Double>): Double {
        if (v.isNaN() || v !in range) throw InputRejected(field, v, "${range.start}–${range.endInclusive}")
        return v
    }

    private fun inRange(field: String, v: Int, range: IntRange): Int {
        if (v !in range) throw InputRejected(field, v, "${range.first}–${range.last}")
        return v
    }
}
