package com.raisethebar.engine

import kotlin.math.floor

/**
 * Round half up, exactly like JavaScript's Math.round: ties go towards positive infinity.
 * The checklist requires this everywhere, never banker's rounding. `x - floor(x)` is exact for doubles,
 * so this avoids the classic `floor(x + 0.5)` error at 0.49999999999999994.
 */
fun roundHalfUp(x: Double): Int {
    val f = floor(x)
    return (if (x - f >= 0.5) f + 1 else f).toInt()
}

/** Rounds [x] to the nearest multiple of [step], half up. */
fun roundToNearest(x: Double, step: Int): Int = roundHalfUp(x / step) * step
