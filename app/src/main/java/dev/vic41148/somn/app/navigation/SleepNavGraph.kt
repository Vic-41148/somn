package dev.vic41148.somn.app.navigation

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.vic41148.somn.app.integration.UpdateIntegration
import dev.vic41148.somn.feature.alarm.service.AlarmService
import dev.vic41148.somn.feature.alarm.ui.AlarmEditScreen
import dev.vic41148.somn.feature.alarm.ui.AlarmFiringScreen
import dev.vic41148.somn.feature.alarm.ui.AlarmHistoryScreen
import dev.vic41148.somn.feature.alarm.ui.AlarmListScreen
import dev.vic41148.somn.feature.analytics.ui.CircadianInsightsScreen
import dev.vic41148.somn.feature.analytics.ui.HistoryScreen
import dev.vic41148.somn.feature.analytics.ui.ManualSessionScreen
import dev.vic41148.somn.feature.analytics.ui.SessionDetailScreen
import dev.vic41148.somn.feature.analytics.ui.TrendsScreen
import dev.vic41148.somn.feature.habits.ui.CorrelationInsightsScreen
import dev.vic41148.somn.feature.habits.ui.DailyLogScreen
import dev.vic41148.somn.feature.habits.ui.MedicationLogScreen
import dev.vic41148.somn.feature.habits.ui.SleepDebtDetailScreen
import dev.vic41148.somn.feature.onboarding.ui.OnboardingFlow
import dev.vic41148.somn.feature.settings.ui.DataExportBackupScreen
import dev.vic41148.somn.feature.settings.ui.SettingsScreen
import dev.vic41148.somn.feature.tracking.service.SleepTrackingService
import dev.vic41148.somn.feature.tracking.ui.HomeScreen
import dev.vic41148.somn.feature.tracking.ui.MorningReviewScreen
import dev.vic41148.somn.feature.tracking.ui.TrackingScreen
import dev.vic41148.somn.feature.winddown.ui.ADHDCooldownScreen
import dev.vic41148.somn.feature.winddown.ui.BreathingExerciseScreen
import dev.vic41148.somn.feature.winddown.ui.CognitiveWindDownScreen
import dev.vic41148.somn.feature.winddown.ui.WindDownToolkitScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Default.Nightlight)
    data object Habits : Screen("habits", "Habits", Icons.Default.Spa)
    data object History : Screen("history", "History", Icons.Default.BarChart)
    data object Alarms : Screen("alarms", "Alarms", Icons.Default.AccessAlarm)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

private val bottomNavScreens = listOf(
    Screen.Home,
    Screen.Habits,
    Screen.History,
    Screen.Alarms,
    Screen.Settings
)

private val bottomNavRoutes = bottomNavScreens.map { it.route }.toSet()

/** Routes where the bottom bar should be hidden. */
private val hideNavRoutes = setOf(
    "onboarding",
    "tracking",
    "morning_review/{sessionId}",
    "alarm_firing",
    "alarm_edit/{alarmId}",
    "medication_log",
    "sleep_debt",
    "correlation_insights",
    "circadian_insights",
    "trends",
    "manual_session",
    "breathing_exercise",
    "cognitive_winddown",
    "adhd_cooldown",
    "wind_down",
    "alarm_history",
    "data_export"
)

@Composable
fun SleepNavGraph(
    isOnboardingCompleted: Boolean,
    updateIntegrations: Set<@JvmSuppressWildcards UpdateIntegration>
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val startDestination = if (isOnboardingCompleted) Screen.Home.route else "onboarding"

    val hideBottomBar = currentDestination?.route in hideNavRoutes

    // The alarm_firing route used to be registered but unreachable — nothing ever navigated to
    // it, so while the alarm was ringing the in-app full-screen experience only ever existed as
    // the system AlarmActivity. If the full-screen intent is unavailable (API 34+ can revoke the
    // permission) that left no in-app firing surface. Navigate here whenever a firing episode
    // starts while the app is open, and leave when the episode ends (dismiss, or snooze — which
    // the service now reports as ending the episode). During the WAKE-01 confirmation window the
    // route is kept so the screen can show its countdown.
    val isAlarmFiring by AlarmService.isAlarmFiring.collectAsState()
    val alarmPhase by AlarmService.phase.collectAsState()
    val currentRoute = currentDestination?.route

    LaunchedEffect(isAlarmFiring, alarmPhase, currentRoute) {
        when {
            isAlarmFiring && currentRoute != "alarm_firing" ->
                navController.navigate("alarm_firing")

            alarmPhase == AlarmService.AlarmPhase.DISMISSED && currentRoute == "alarm_firing" ->
                navController.popBackStack()
        }
    }

    // FGS notification tap-through (SleepTrackingService.EXTRA_OPEN_TRACKING): land straight on
    // the tracking screen so the Wake Up button is always one tap away. Keyed on the activity
    // intent so both cold starts (original intent) and warm taps (MainActivity.onNewIntent ->
    // setIntent) fire it; launchSingleTop keeps an already-open tracking screen from duplicating.
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(activity?.intent) {
        if (activity?.intent?.getBooleanExtra(SleepTrackingService.EXTRA_OPEN_TRACKING, false) == true) {
            navController.navigate("tracking") {
                launchSingleTop = true
            }
        }
    }

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
            // MainActivity calls enableEdgeToEdge(), so the window no longer resizes when the
            // soft keyboard opens, and Scaffold's default contentWindowInsets covers only the
            // system bars. Without imePadding here the keyboard silently draws over whatever the
            // user is typing into — the NAS host/port/password fields, the alarm label, the
            // morning-review notes. Applied once at the single Scaffold every screen sits inside
            // rather than per-screen.
            modifier = Modifier
                .padding(innerPadding)
                .imePadding(),
            // Tab switches crossfade (siblings under one bar); detail pushes slide with the
            // direction of travel (right-to-left in, left-to-right on pop) so the user can
            // tell at a glance whether back returns to a tab or pops a stack.
            enterTransition = {
                val sliding = targetState.destination.route !in bottomNavRoutes &&
                    initialState.destination.route !in bottomNavRoutes
                if (sliding) slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    tween(280)
                ) else fadeIn(tween(220))
            },
            exitTransition = {
                val sliding = targetState.destination.route !in bottomNavRoutes &&
                    initialState.destination.route !in bottomNavRoutes
                if (sliding) slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    tween(280)
                ) else fadeOut(tween(220))
            },
            popEnterTransition = {
                val sliding = targetState.destination.route !in bottomNavRoutes &&
                    initialState.destination.route !in bottomNavRoutes
                if (sliding) slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    tween(280)
                ) else fadeIn(tween(220))
            },
            popExitTransition = {
                val sliding = targetState.destination.route !in bottomNavRoutes &&
                    initialState.destination.route !in bottomNavRoutes
                if (sliding) slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    tween(280)
                ) else fadeOut(tween(220))
            }
        ) {
            // Onboarding
            composable("onboarding") {
                OnboardingFlow(
                    onOnboardingComplete = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }

            // ---- Main tabs ----

            composable(Screen.Home.route) {
                Box {
                    HomeScreen(
                        onNavigateToTracking = { navController.navigate("tracking") },
                        onNavigateToMorningReview = { sessionId ->
                            navController.navigate("morning_review/$sessionId")
                        },
                        onNavigateToDebt = { navController.navigate("sleep_debt") }
                    )
                    // In-app update banner (standalone channel only). Rendered as an overlay so the
                    // store channel, whose integration is a no-op composable, draws nothing here.
                    updateIntegrations.forEach {
                        it.HomeBanner(
                            onOpenUpdates = { navController.navigate("updates") },
                            onGoToBackup = { navController.navigate("data_export") }
                        )
                    }
                }
            }

            composable(Screen.Habits.route) {
                DailyLogScreen(
                    onNavigateToMedication = { navController.navigate("medication_log") },
                    onNavigateToCorrelations = { navController.navigate("correlation_insights") }
                )
            }

            composable(Screen.History.route) {
                HistoryScreen(
                    onSessionClick = { sessionId ->
                        navController.navigate("session_detail/$sessionId")
                    },
                    onNavigateToCircadian = {
                        navController.navigate("circadian_insights")
                    },
                    onNavigateToTrends = {
                        navController.navigate("trends")
                    },
                    onAddManualSession = {
                        navController.navigate("manual_session")
                    }
                )
            }

            composable("manual_session") {
                ManualSessionScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }

            composable(Screen.Alarms.route) {
                AlarmListScreen(
                    onAddAlarm = { navController.navigate("alarm_edit/-1") },
                    onEditAlarm = { alarm -> navController.navigate("alarm_edit/${alarm.id}") },
                    onHistory = { navController.navigate("alarm_history") }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToWindDownToolkit = { navController.navigate("wind_down") },
                    onNavigateToDataExport = { navController.navigate("data_export") },
                    onNavigateToUpdates = { navController.navigate("updates") }
                )
            }

            // ---- Tracking ----

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

            // ---- Alarms ----

            composable(
                route = "alarm_edit/{alarmId}",
                arguments = listOf(navArgument("alarmId") { type = NavType.LongType })
            ) { backStackEntry ->
                val alarmId = backStackEntry.arguments?.getLong("alarmId") ?: -1L
                AlarmEditScreen(
                    alarmId = if (alarmId >= 0) alarmId else 0L,
                    onSaved = { navController.popBackStack() }
                )
            }

            composable("alarm_firing") {
                AlarmFiringScreen()
            }

            // ---- Phase 2: Habits detail screens ----

            composable("sleep_debt") {
                SleepDebtDetailScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable("medication_log") {
                MedicationLogScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable("correlation_insights") {
                CorrelationInsightsScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable("circadian_insights") {
                CircadianInsightsScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // ---- Phase 6: Trends (DATA-03/04) ----

            composable("trends") {
                TrendsScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // ---- Phase 5: Wind-down screens ----

            composable("breathing_exercise") {
                BreathingExerciseScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable("cognitive_winddown") {
                CognitiveWindDownScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable("adhd_cooldown") {
                ADHDCooldownScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // ---- Phase 6+: wind-down toolkit / alarm history / data export ----

            composable("wind_down") {
                WindDownToolkitScreen(
                    onNavigateToBreathing = { navController.navigate("breathing_exercise") },
                    onNavigateToCognitiveWindDown = { navController.navigate("cognitive_winddown") },
                    onNavigateToADHDCooldown = { navController.navigate("adhd_cooldown") },
                    onBack = { navController.popBackStack() }
                )
            }

            composable("alarm_history") {
                AlarmHistoryScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable("data_export") {
                DataExportBackupScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // Channel-scoped routes (updates screen only on standalone builds, no-op on store).
            updateIntegrations.forEach {
                it.registerUpdateRoutes(builder = this, onBack = { navController.popBackStack() })
            }
        }
    }
}
