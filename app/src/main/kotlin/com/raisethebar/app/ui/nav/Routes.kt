package com.raisethebar.app.ui.nav

import kotlinx.serialization.Serializable

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
