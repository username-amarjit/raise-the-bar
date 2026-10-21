package com.raisethebar.app.ui.nav

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.raisethebar.app.data.planForToday
import com.raisethebar.app.data.startSession
import com.raisethebar.app.ui.LocalSnackbar
import com.raisethebar.app.ui.exercise.ExerciseScreen
import com.raisethebar.app.ui.gym.EquipmentScreen
import com.raisethebar.app.ui.gym.GymScreen
import com.raisethebar.app.ui.rememberActions
import com.raisethebar.app.ui.rememberLoad
import com.raisethebar.app.ui.settings.AboutScreen
import com.raisethebar.app.ui.settings.SettingsScreen
import com.raisethebar.app.ui.setup.EditScreen
import com.raisethebar.app.ui.setup.EditSection
import com.raisethebar.app.ui.setup.SetupScreen
import com.raisethebar.app.ui.theme.Rtb
import com.raisethebar.app.ui.today.TodayActions
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
        val setupDone = if (isReady) rememberLoad { isSetupDone() } else null
        var start by remember { mutableStateOf<Any?>(null) }
        LaunchedEffect(setupDone) { if (start == null && setupDone != null) start = if (setupDone) TodayRoute else SetupRoute }
        val first = start
        if (first == null) {
            Box(Modifier.fillMaxSize().background(Rtb.colors.bg), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            HideInSwitcher()
            AppNav(first, snackbar, checkinRequest)
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
private fun AppNav(start: Any, snackbar: SnackbarHostState, checkinRequest: MutableState<String?>) {
    val nav = rememberNavController()
    val actions = rememberActions()
    val entry by nav.currentBackStackEntryAsState()
    val currentTab = entry?.destination.tab()
    val requested = checkinRequest.value
    LaunchedEffect(requested, entry?.destination?.route) {
        if (requested != null && entry != null && entry?.destination?.hasRoute<SetupRoute>() != true) {
            checkinRequest.value = null
            nav.navigate(CheckinRoute(requested))
        }
    }
    val today =
        TodayActions(
            onSettings = { nav.navigate(SettingsRoute) },
            onStart = { kind ->
                actions.run {
                    val plan = planForToday(kind) ?: return@run
                    nav.navigate(SessionRoute(startSession(plan)))
                }
            },
            onResume = { id -> nav.navigate(SessionRoute(id)) },
            onExercise = { id, slot -> nav.navigate(ExerciseRoute(id, slot)) },
            onSomethingHurts = { nav.navigate(InjuryRoute()) },
            onPlanChanges = { nav.navigate(PlanChangesRoute) },
            onCheckin = { date -> nav.navigate(CheckinRoute(date.toString())) },
            onEditWeek = { nav.navigate(EditRoute(EditSection.WEEK)) },
        )
    Scaffold(
        containerColor = Rtb.colors.bg,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (currentTab != null) {
                RtbBottomBar(current = currentTab) { tab -> nav.goToTab(tab) }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = start, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable<SetupRoute> {
                SetupScreen(onDone = { hurting ->
                    nav.navigate(TodayRoute) { popUpTo(SetupRoute) { inclusive = true } }
                    if (hurting.isNotEmpty()) nav.navigate(InjuryRoute())
                })
            }
            composable<TodayRoute> { TodayScreen(today) }
            composable<FoodRoute> { PendingScreen("Food", "Protein, quick portions and your usual meals arrive in milestone 12.") }
            composable<ProgressRoute> { PendingScreen("Progress", "Session history and the skip screen arrive in milestones 8 and 13.") }
            composable<GymRoute> { GymScreen(onItem = { id -> nav.navigate(EquipmentRoute(id)) }) }
            composable<ExerciseRoute> { e ->
                val r = e.toRoute<ExerciseRoute>()
                ExerciseScreen(r.exerciseId, r.slotId, onBack = { nav.popBackStack() }, onOpen = { id -> nav.navigate(ExerciseRoute(id, r.slotId)) })
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onAbout = { nav.navigate(AboutRoute) },
                    onEdit = { section -> nav.navigate(EditRoute(section)) },
                    onGym = { nav.goToTab(Tab.GYM) },
                )
            }
            composable<AboutRoute> { AboutScreen(onBack = { nav.popBackStack() }) }
            composable<EditRoute> { e -> EditScreen(e.toRoute<EditRoute>().section, onBack = { nav.popBackStack() }) }
            composable<SessionRoute> { PendingScreen("Workout", "The workout log arrives in milestone 7.") }
            composable<InjuryRoute> { PendingScreen("Something hurts", "Injury mode arrives in milestone 11.") }
            composable<PlanChangesRoute> { PendingScreen("Plan changes", "Day off and away mode arrive in milestone 10.") }
            composable<CheckinRoute> { PendingScreen("Check-in", "Check-ins arrive in milestone 8.") }
            composable<EquipmentRoute> { e ->
                EquipmentScreen(e.toRoute<EquipmentRoute>().id, onBack = { nav.popBackStack() }, onExercise = { id ->
                    nav.navigate(ExerciseRoute(id))
                })
            }
        }
    }
}

fun NavHostController.goToTab(tab: Tab) {
    navigate(tab.route()) {
        popUpTo<TodayRoute> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
