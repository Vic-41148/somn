package dev.vic41148.somn.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.vic41148.somn.feature.alarm.ui.AlarmEditScreen
import dev.vic41148.somn.feature.alarm.ui.AlarmFiringScreen
import dev.vic41148.somn.feature.alarm.ui.AlarmListScreen
import dev.vic41148.somn.feature.analytics.ui.HistoryScreen
import dev.vic41148.somn.feature.analytics.ui.SessionDetailScreen
import dev.vic41148.somn.feature.onboarding.ui.OnboardingFlow
import dev.vic41148.somn.feature.settings.ui.SettingsScreen
import dev.vic41148.somn.feature.tracking.ui.HomeScreen
import dev.vic41148.somn.feature.tracking.ui.MorningReviewScreen
import dev.vic41148.somn.feature.tracking.ui.TrackingScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Default.Nightlight)
    data object History : Screen("history", "History", Icons.Default.BarChart)
    data object Alarms : Screen("alarms", "Alarms", Icons.Default.AccessAlarm)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

private val bottomNavScreens = listOf(
    Screen.Home,
    Screen.History,
    Screen.Alarms,
    Screen.Settings
)

@Composable
fun SleepNavGraph(
    isOnboardingCompleted: Boolean
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Determine start destination based on onboarding status
    val startDestination = if (isOnboardingCompleted) Screen.Home.route else "onboarding"

    // Hide bottom bar on certain screens
    val hideBottomBar = currentDestination?.route in listOf(
        "onboarding", "tracking", "morning_review/{sessionId}", "alarm_firing", "alarm_edit"
    )

    Scaffold(
        bottomBar = {
            if (!hideBottomBar) {
                NavigationBar {
                    bottomNavScreens.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentDestination?.hierarchy?.any {
                                it.route == screen.route
                            } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Onboarding flow
            composable("onboarding") {
                OnboardingFlow(
                    onOnboardingComplete = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }

            // Main tabs
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToTracking = { navController.navigate("tracking") },
                    onNavigateToMorningReview = { sessionId ->
                        navController.navigate("morning_review/$sessionId")
                    }
                )
            }

            composable(Screen.History.route) {
                HistoryScreen(
                    onSessionClick = { sessionId ->
                        navController.navigate("session_detail/$sessionId")
                    }
                )
            }

            composable(Screen.Alarms.route) {
                AlarmListScreen(
                    onAddAlarm = { navController.navigate("alarm_edit") }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen()
            }

            // Detail screens
            composable("tracking") {
                TrackingScreen(
                    onTrackingStopped = { sessionId ->
                        navController.navigate("morning_review/$sessionId") {
                            popUpTo("tracking") { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = "morning_review/{sessionId}",
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
                MorningReviewScreen(
                    sessionId = sessionId,
                    onDone = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = "session_detail/{sessionId}",
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
                SessionDetailScreen(
                    sessionId = sessionId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("alarm_edit") {
                AlarmEditScreen(
                    onSaved = { navController.popBackStack() }
                )
            }

            composable("alarm_firing") {
                AlarmFiringScreen(
                    onDismissed = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
