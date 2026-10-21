package com.raisethebar.app.ui.nav

import kotlinx.serialization.Serializable

@Serializable object SetupRoute

@Serializable object TodayRoute

@Serializable object FoodRoute

@Serializable object ProgressRoute

@Serializable object GymRoute

@Serializable data class ExerciseRoute(
    val exerciseId: String,
    val slotId: String? = null,
)

@Serializable object SettingsRoute

@Serializable object AboutRoute

@Serializable data class EditRoute(
    val section: String,
)

@Serializable data class SessionRoute(
    val id: Long,
)

/** Injury triage; [fromSession] returns to an open workout afterwards, [date] records "injured" for a check-in. */
@Serializable data class InjuryRoute(
    val fromSession: Long = -1L,
    val date: String? = null,
)

@Serializable object PlanChangesRoute

@Serializable data class CheckinRoute(
    val date: String,
)
