package com.raisethebar.config

/**
 * Every number from section 5 of the Phase 1 checklist. No other file may hard-code these values.
 * All rounding is round-half-up (see [com.raisethebar.engine.roundHalfUp]).
 */
object Rules {
    // ---------- Estimates and weights ----------

    /** e1RM = weight × (1 + (reps + repsLeft) / EPLEY_DIVISOR). */
    const val EPLEY_DIVISOR = 30.0

    /** The "4+" button is stored and used as 4, a safe lower bound. */
    const val REPS_LEFT_MAX = 4

    /** Honesty correction; fixed at 0 in Phase 1. */
    const val RIR_BIAS = 0

    /** A session below 92% of the estimate is a bad day. */
    const val BAD_DAY_RATIO = 0.92

    /** Two bad days in a row move the estimate halfway down. */
    const val BAD_DAYS_TO_COUNT = 2

    /** A first set this many reps-to-failure off the prediction moves the remaining sets one weight step. */
    const val FIRST_SET_GAP = 2

    /** Sets 2 and 3 target one rep fewer each. */
    const val REP_DROP_PER_SET = 1

    /** A first prescription aims at the bottom of the range plus 1. */
    const val FIRST_TARGET_ABOVE_LOW = 1

    /** Find-your-weight ramp uses sets of 8. */
    const val RAMP_REPS = 8

    /** At most 5 ramp sets per exercise. */
    const val RAMP_MAX_SETS = 5

    /** After a ramp set with 4+ left, the next weight is 20% heavier. */
    const val RAMP_JUMP_4PLUS = 1.2

    /** Stop the ramp at this many reps left or fewer. */
    const val RAMP_STOP_LEFT = 2

    /** At this many reps left the user may tap "That's my weight" to stop the ramp. */
    const val RAMP_CONFIRM_LEFT = 3

    /** Ramp suggestions after a set with 3 or fewer left aim for RAMP_REPS reps with this many left. */
    const val RAMP_SUGGEST_LEFT = 2

    /** Warm-up for the first lift of each session only, rounded down to available weights. */
    val WARMUP: List<WarmupStep> =
        listOf(
            WarmupStep(fraction = 0.50, reps = 8),
            WarmupStep(fraction = 0.70, reps = 4),
            WarmupStep(fraction = 0.85, reps = 2),
        )

    /** Deload weight = 90% of the normal weight, rounded down. */
    const val DELOAD_WEIGHT = 0.9

    /** Deload sets = programmed sets ÷ DELOAD_SETS_DIVISOR, rounded up. */
    const val DELOAD_SETS_DIVISOR = 2

    /** Stop deload sets with 4 reps left. */
    const val DELOAD_LEFT = 4

    /** Timed holds: when every set reaches the top of the range, add 5 s to both ends... */
    const val HOLD_STEP_S = 5

    /** ...up to 90 s. */
    const val HOLD_MAX_S = 90

    /** Weight step for barbell or Smith lifts, upper body (1.25 kg a side). */
    const val STEP_BARBELL_UPPER_KG = 2.5

    /** Weight step for barbell or Smith lifts, lower body (2.5 kg a side). */
    const val STEP_BARBELL_LOWER_KG = 5.0

    /** Minimum sensible difference between two weights, to absorb floating-point noise. */
    const val WEIGHT_EPSILON_KG = 0.001

    // ---------- Calendar and effort ramp ----------

    const val DAYS_PER_WEEK = 7
    const val CALIBRATION_WEEKS = 2
    const val BLOCK_WEEKS = 6

    /** Block weeks plus the deload week: k = (week − 3) mod 7, k = 6 is the deload. */
    const val CYCLE_WEEKS = 7

    /** Working sets in calibration week 2. */
    const val CALIBRATION_2_SETS = 2

    /** Stop-with targets in calibration week 2 (big, small). */
    val CALIBRATION_2_LEFT = EffortLeft(big = 3, small = 3)

    /** Calibration week 1 is the ramp: the final ramp set plus this many more working sets. */
    const val CALIBRATION_1_EXTRA_SETS = 1

    /** Stop-with targets per block week 1–6. */
    val BLOCK_LEFT: Map<Int, EffortLeft> =
        mapOf(
            1 to EffortLeft(big = 3, small = 2),
            2 to EffortLeft(big = 3, small = 2),
            3 to EffortLeft(big = 2, small = 1),
            4 to EffortLeft(big = 2, small = 1),
            5 to EffortLeft(big = 1, small = 1),
            6 to EffortLeft(big = 1, small = 0),
        )

    /** Stop-with targets in the deload week. */
    val DELOAD_EFFORT = EffortLeft(big = DELOAD_LEFT, small = DELOAD_LEFT)

    /** Minimum session: the day's first exercises, this many sets each. It counts as done. */
    const val MINIMUM_SESSION_EXERCISES = 3
    const val MINIMUM_SESSION_SETS = 2

    /** Home and away sets rest 90 s for big lifts and 60 s for small ones. */
    const val HOME_AWAY_REST_BIG_S = 90
    const val HOME_AWAY_REST_SMALL_S = 60

    /** Rest ranges stored as one value (decision 1). */
    const val REST_2_TO_3_MIN_S = 150
    const val REST_2_MIN_S = 120
    const val REST_90_S = 90
    const val REST_60_TO_90_S = 75
    const val REST_60_S = 60

    // ---------- Skips, day off and make-up mode ----------

    /** Check-in fires this long after the planned time if no session has started. */
    const val CHECKIN_DELAY_MIN = 120

    /** Only the next 7 days of check-ins are scheduled; rebuilt on every app open. */
    const val CHECKIN_DAYS_AHEAD = 7

    /** Default day-off cut-off: 22:00 the night before (minutes after midnight). User setting. */
    const val DAY_OFF_CUTOFF_DEFAULT_MIN = 22 * 60

    /** The 3rd declared day off in a calendar month counts as an unexcused skip. */
    const val DAY_OFF_PER_MONTH = 2

    /** Rolling make-up window, including today. */
    const val MAKEUP_WINDOW_DAYS = 7

    /** Unexcused skips in the window that start make-up mode (user range 2–3). */
    const val MAKEUP_TRIGGER = 2
    val MAKEUP_TRIGGER_RANGE = 2..3

    /** Make-up sessions per skip (user range 1–3). */
    const val MAKEUP_SESSIONS_PER_SKIP = 2
    val MAKEUP_SESSIONS_PER_SKIP_RANGE = 1..3

    /** Cap on make-up sessions (user range 3–8). */
    const val MAKEUP_MAX_SESSIONS = 6
    val MAKEUP_MAX_SESSIONS_RANGE = 3..8

    /** Make-up block: 15 min (user range 10–20), 2 exercises, 2 sets each. */
    const val MAKEUP_BLOCK_MIN = 15
    val MAKEUP_BLOCK_MIN_RANGE = 10..20
    const val MAKEUP_BLOCK_EXERCISES = 2
    const val MAKEUP_BLOCK_SETS = 2

    /** Make-up sets rest 60 s and use the program's rep range, or 10–15 if the exercise isn't in the program. */
    const val MAKEUP_REST_S = 60
    const val MAKEUP_DEFAULT_REP_LOW = 10
    const val MAKEUP_DEFAULT_REP_HIGH = 15

    /** Hard sets per muscle group in 7 days that make-up blocks never exceed. */
    const val WEEKLY_SET_CAP = 20

    /** Skips in 7 days that show the "different training time?" banner. */
    const val SKIP_BANNER_AT = 4

    /** If the last completed session is this many days old, make-up mode doesn't start (comeback stub). */
    const val COMEBACK_STUB_GAP_DAYS = 7

    // ---------- Away mode ----------

    /** Sessions while away = round(days × 3 / 7), at least 1 and at most 3. */
    const val AWAY_SESSIONS_PER_WEEK = 3
    const val AWAY_MIN_SESSIONS = 1
    const val AWAY_MAX_SESSIONS = 3

    /** Day 1 is travel (paused); sessions then go on alternate days (days 2, 4, 6). */
    const val AWAY_FIRST_SESSION_DAY = 2
    const val AWAY_SESSION_EVERY_DAYS = 2

    /** Minutes per away session: 20–45, default 30. */
    val AWAY_MINUTES_RANGE = 20..45
    const val AWAY_MINUTES_DEFAULT = 30

    /** Pre-trip rule: these groups may not go more than 7 days untrained (days since last trained + trip length). */
    val PRE_TRIP_GROUPS: List<String> = listOf("chest", "back", "quads", "hams_glutes")
    const val PRE_TRIP_MAX_UNTRAINED_DAYS = 7

    // ---------- Injury mode ----------

    /** Pain light: 0–2 green (progress), 3–5 amber (hold), 6 or more or any sharp pain red. */
    const val PAIN_GREEN_MAX = 2
    const val PAIN_AMBER_MAX = 5

    /** Phase 1 moves on after at least this many days once the morning check is green. */
    const val PHASE_1_MIN_DAYS = 1

    /** Phase 2: 40% of the pre-injury working weight, +10% per green session up to 60%; 12–20 reps. */
    const val PHASE_2_START_FRACTION = 0.40
    const val PHASE_2_STEP_FRACTION = 0.10
    const val PHASE_2_MAX_FRACTION = 0.60
    const val PHASE_2_REP_LOW = 12
    const val PHASE_2_REP_HIGH = 20

    /** Phase 2 moves on after 2 green sessions. */
    const val PHASE_2_GREEN_SESSIONS = 2

    /** Phase 3: original exercise at 60% of the pre-injury weight; +10% after a green session. */
    const val PHASE_3_START_FRACTION = 0.60
    const val PHASE_3_STEP_FRACTION = 0.10

    /** Phase 4: at the pre-injury weight; 2 green sessions there ends injury mode. */
    const val PHASE_4_GREEN_SESSIONS = 2

    /** Red flag: hasn't improved in two weeks. */
    const val INJURY_NO_IMPROVEMENT_DAYS = 14

    // ---------- Food and body ----------

    /** Protein target: 1.7 g per kg body weight, rounded to the nearest 5 g; show the 1.6–2.0 g/kg range. */
    const val PROTEIN_G_PER_KG = 1.7
    const val PROTEIN_ROUND_TO_G = 5
    const val PROTEIN_RANGE_LOW_G_PER_KG = 1.6
    const val PROTEIN_RANGE_HIGH_G_PER_KG = 2.0

    /** Calorie first guess: 36 × kg for lean bulk, 31 × kg for recomp, rounded to the nearest 100. */
    const val KCAL_PER_KG_LEAN_BULK = 36
    const val KCAL_PER_KG_RECOMP = 31
    const val KCAL_ROUND_TO = 100

    /** Goal suggestion: waist less than half the height suggests lean bulk; otherwise recomp. */
    const val GOAL_WAIST_TO_HEIGHT_RATIO = 0.5

    /** Canteen oil: +45 kcal (1 tsp oil) per cooked item when "Canteen" is on. */
    const val CANTEEN_OIL_KCAL = 45

    /** Weight average: mean of morning weights in the last 7 days; shown only with 3 or more entries. */
    const val WEIGHT_AVERAGE_DAYS = 7
    const val WEIGHT_AVERAGE_MIN_ENTRIES = 3

    /** Protein and calorie targets are recalculated when body weight changes by this much. */
    const val TARGETS_RECALC_WEIGHT_CHANGE_KG = 2.0

    // ---------- My Gym ----------

    /** A broken item asks "Is it fixed yet?" every 3 days. */
    const val BROKEN_ASK_EVERY_DAYS = 3

    // ---------- Input limits (reject anything outside) ----------

    object Limits {
        val EXERCISE_WEIGHT_KG = 0.5..500.0
        val REPS = 0..50
        val REPS_LEFT = 0..REPS_LEFT_MAX
        val HOLD_S = 5..300
        val PAIN = 0..10
        val BODY_WEIGHT_KG = 30.0..250.0
        val HEIGHT_CM = 120.0..230.0
        val WAIST_CM = 50.0..200.0
        val AGE = 16..90
        val MINUTES = 10..120
    }
}

/** One warm-up step: a fraction of the working weight for a number of reps. */
data class WarmupStep(val fraction: Double, val reps: Int)

/** How many reps to stop with, for big and for small lifts. */
data class EffortLeft(val big: Int, val small: Int)
