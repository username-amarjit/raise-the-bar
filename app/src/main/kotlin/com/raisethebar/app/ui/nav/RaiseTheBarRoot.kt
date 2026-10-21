package com.raisethebar.app.ui.nav

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.raisethebar.app.ui.LocalSnackbar
import com.raisethebar.app.ui.exercise.ExerciseScreen
import com.raisethebar.app.ui.rememberLoad
import com.raisethebar.app.ui.settings.AboutScreen
import com.raisethebar.app.ui.settings.SettingsScreen
import com.raisethebar.app.ui.theme.Rtb
import com.raisethebar.app.ui.today.TodayScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private fun Tab.route(): Any =
    when (this) {
        Tab.TODAY -> TodayRoute
        Tab.FOOD -> FoodRoute
        Tab.PROGRESS -> ProgressRoute
        Tab.GYM -> GymRoute
    }

private fun NavDestination?.tab(): Tab? =
    when {
        this == null -> null
        hasRoute<TodayRoute>() -> Tab.TODAY
        hasRoute<FoodRoute>() -> Tab.FOOD
        hasRoute<ProgressRoute>() -> Tab.PROGRESS
        hasRoute<GymRoute>() -> Tab.GYM
        else -> null
    }

@Composable
fun RaiseTheBarRoot(ready: StateFlow<Boolean> = MutableStateFlow(true), checkinRequest: MutableState<String?> = remember { mutableStateOf(null) }) {
    val snackbar = remember { SnackbarHostState() }
    val isReady by ready.collectAsState()
    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        if (!isReady) {
            Box(Modifier.fillMaxSize().background(Rtb.colors.bg), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            HideInSwitcher()
            AppNav(snackbar, checkinRequest)
        }
    }
}

/** Optional privacy setting: hide the app's content in the app switcher. */
@Composable
private fun HideInSwitcher() {
    val hide = rememberLoad { profile()?.hideInSwitcher == true }
    val activity = LocalActivity.current
    LaunchedEffect(hide, activity) {
        val window = activity?.window ?: return@LaunchedEffect
        if (hide == true) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

@Composable
private fun AppNav(
    snackbar: SnackbarHostState,
    @Suppress("UNUSED_PARAMETER") checkinRequest: MutableState<String?>,
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val currentTab = entry?.destination.tab()
    Scaffold(
        containerColor = Rtb.colors.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (currentTab != null) {
                RtbBottomBar(current = currentTab) { tab -> nav.goToTab(tab) }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = TodayRoute, modifier = Modifier.padding(padding)) {
            composable<TodayRoute> { TodayScreen(onSettings = { nav.navigate(SettingsRoute) }) }
            composable<FoodRoute> { PendingScreen("Food", "Protein, quick portions and your usual meals arrive in milestone 12.") }
            composable<ProgressRoute> { PendingScreen("Progress", "Session history and the skip screen arrive in milestones 8 and 13.") }
            composable<GymRoute> { PendingScreen("My Gym", "Your equipment arrives in milestone 5.") }
            composable<ExerciseRoute> { e ->
                val r = e.toRoute<ExerciseRoute>()
                ExerciseScreen(r.exerciseId, r.slotId, onBack = { nav.popBackStack() }, onOpen = { id -> nav.navigate(ExerciseRoute(id, r.slotId)) })
            }
            composable<SettingsRoute> { SettingsScreen(onBack = { nav.popBackStack() }, onAbout = { nav.navigate(AboutRoute) }) }
            composable<AboutRoute> { AboutScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

fun NavHostController.goToTab(tab: Tab) {
    navigate(tab.route()) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
