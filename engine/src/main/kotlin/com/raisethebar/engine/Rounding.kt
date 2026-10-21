package com.raisethebar.engine

import kotlin.math.floor

/**
 * Round half up, the same as JavaScript's Math.round: ties go towards positive infinity.
 * The checklist requires this everywhere, never banker's rounding.
 */
fun roundHalfUp(x: Double): Int = floor(x + 0.5).toInt()

/** Rounds [x] to the nearest multiple of [step], half up. */
fun roundToNearest(x: Double, step: Int): Int = roundHalfUp(x / step) * step
