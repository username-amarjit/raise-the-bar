package com.raisethebar.app.ui.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.raisethebar.app.ui.components.RtbCard
import com.raisethebar.app.ui.components.ScreenTitle
import com.raisethebar.app.ui.theme.Rtb
import com.raisethebar.app.ui.today.TodayScreen
import kotlinx.serialization.Serializable

@Serializable object TodayRoute

@Serializable object FoodRoute

@Serializable object ProgressRoute

@Serializable object GymRoute

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
fun RaiseTheBarRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val currentTab = entry?.destination.tab()
    Scaffold(
        containerColor = Rtb.colors.bg,
        bottomBar = {
            if (currentTab != null) {
                RtbBottomBar(current = currentTab) { tab ->
                    nav.navigate(tab.route()) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = TodayRoute, modifier = Modifier.padding(padding)) {
            composable<TodayRoute> { TodayScreen() }
            composable<FoodRoute> { PlaceholderScreen("Food", "Protein, quick portions and your usual meals arrive in milestone 12.") }
            composable<ProgressRoute> { PlaceholderScreen("Progress", "Session history and the skip screen arrive in milestones 8 and 13.") }
            composable<GymRoute> { PlaceholderScreen("My Gym", "Your equipment arrives in milestones 4 and 5.") }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle(title)
        RtbCard { Text(body, style = MaterialTheme.typography.bodyMedium, color = Rtb.colors.ink2) }
    }
}
