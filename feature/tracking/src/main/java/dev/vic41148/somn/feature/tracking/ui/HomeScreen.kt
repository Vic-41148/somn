package dev.vic41148.somn.feature.tracking.ui

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import dev.vic41148.somn.core.ui.theme.DebtMild
import dev.vic41148.somn.core.ui.theme.DebtModerate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.vic41148.somn.core.domain.model.DebtLevel
import dev.vic41148.somn.core.domain.model.SleepDebt
import dev.vic41148.somn.core.domain.model.SleepSession
import dev.vic41148.somn.core.domain.usecase.ReadinessResult
import dev.vic41148.somn.core.domain.usecase.assessReadiness
import dev.vic41148.somn.core.domain.usecase.buildOutlook
import dev.vic41148.somn.core.domain.usecase.formatDurationShort
import dev.vic41148.somn.core.domain.usecase.summarizeSessions
import dev.vic41148.somn.core.ui.components.MetricChip
import dev.vic41148.somn.core.ui.components.PillRow
import dev.vic41148.somn.core.ui.components.SleepCard
import dev.vic41148.somn.core.ui.components.SleepScoreRing
import dev.vic41148.somn.core.ui.components.StatRing
import dev.vic41148.somn.core.ui.theme.scoreColor
import dev.vic41148.somn.feature.habits.HabitViewModel
import dev.vic41148.somn.feature.tracking.SleepTrackingViewModel
import dev.vic41148.somn.feature.tracking.service.SleepTrackingService
import dev.vic41148.somn.feature.tracking.service.TrackingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToTracking: () -> Unit,
    onNavigateToMorningReview: (Long) -> Unit,
    onNavigateToDebt: () -> Unit = {},
    onNavigateToTrends: () -> Unit = {},
    onNavigateToWindDown: () -> Unit = {},
    onNavigateToAlarms: () -> Unit = {},
    trackingMode: dev.vic41148.somn.core.domain.model.TrackingMode = dev.vic41148.somn.core.domain.model.TrackingMode.ACCELEROMETER,
    viewModel: SleepTrackingViewModel = hiltViewModel(),
    habitViewModel: HabitViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val trackingState by viewModel.trackingState.collectAsState()
    // Home-screen widget tap-through (SleepTrackingService.EXTRA_START_TRACKING): one-tap
    // sleep with moon defaults, then land on tracking. Consumed immediately so warm intents
    // and recompositions never double-start; MainActivity already drops this extra until
    // onboarding completes, so this only runs on the real Home.
    val activity = context as? ComponentActivity
    LaunchedEffect(activity?.intent, trackingState) {
        val intent = activity?.intent ?: return@LaunchedEffect
        if (intent.getBooleanExtra(SleepTrackingService.EXTRA_START_TRACKING, false)) {
            intent.removeExtra(SleepTrackingService.EXTRA_START_TRACKING)
            if (trackingState == TrackingState.IDLE) {
                viewModel.startTracking(context, trackingMode)
            }
            onNavigateToTracking()
        }
    }
    val lastSession by viewModel.lastSession.collectAsState()
    val lastScore by viewModel.lastScore.collectAsState()
    val recentSessions by viewModel.recentSessions.collectAsState()
    val readinessVitals by viewModel.readinessVitals.collectAsState()
    val readinessActivity by viewModel.readinessActivity.collectAsState()
    val sleepDebt by habitViewModel.sleepDebt.collectAsState()
    /** Spec §6.6: off hides every score ring and streak on this screen, raw data stays. */
    val showScores by viewModel.showStreaksScores.collectAsState()
    /** Spec §5 Smart Alarm card. */
    val alarms by viewModel.alarms.collectAsState()
    // Morning verdict, the same inputs as the History header plus vitals, so numbers agree.
    // restModeSince excludes sick nights from every baseline (R2 Rest Mode).
    val restModeSince by viewModel.restModeSince.collectAsState()
    val restMode = restModeSince != null
    val readiness = remember(recentSessions, sleepDebt, readinessVitals, readinessActivity, restModeSince) {
        assessReadiness(
            recentSessions,
            sleepDebt,
            readinessVitals,
            activity = readinessActivity,
            excludeSinceMillis = restModeSince
        )
    }
    // Outlook sentence, strongest settled correlation + debt-plan hint, template-built.
    val recoveryPlan by habitViewModel.recoveryPlan.collectAsState()
    val correlationReport by habitViewModel.correlationReport.collectAsState()
    // R5: luteal coaching + widened debt hint inside the cycle's luteal window.
    val cycleCoaching by viewModel.cycleCoaching.collectAsState()
    val lutealExtra by viewModel.lutealExtraMinutes.collectAsState()
    val outlook = remember(readiness, sleepDebt, correlationReport, recoveryPlan, restMode, cycleCoaching, lutealExtra, readinessActivity) {
        val topInsight = correlationReport?.availableCorrelations
            ?.maxByOrNull { kotlin.math.abs(it.correlation) }?.insight
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val baseHint = recoveryPlan?.additionalMinutesPerNight?.takeIf { it > 0 }
        buildOutlook(
            readiness = readiness,
            debt = sleepDebt,
            correlationInsight = topInsight,
            recoveryMinutesHint = if (baseHint != null) baseHint + lutealExtra else null,
            isMorning = hour in 4..16,
            restMode = restMode,
            cycleCoaching = cycleCoaching,
            activity = readinessActivity
        )
    }
    // 7-day rollup for the rings, the same math the History header uses, so the numbers agree.
    val weekSummary = remember(recentSessions) {
        val cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        summarizeSessions(recentSessions.filter { it.startTimeMillis >= cutoff })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = "Somn",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Good night, sleep tight",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // REL-03: battery-optimization exemption re-check, OEM power managers can silently
        // revoke this after an OTA, which kills overnight tracking without any user-visible error.
        val isBatteryExempted by dev.vic41148.somn.core.ui.battery.BatteryExemptionState.isExempted.collectAsState()
        var batteryBannerDismissed by remember { mutableStateOf(false) }
        // Some OEMs (Samsung, Xiaomi, Huawei) enforce a separate "autostart" restriction on
        // top of standard battery optimization. The code exposes it as the gear icon, not a third row.
        val oemIntent = remember {
            dev.vic41148.somn.core.ui.battery.BatteryExemptionState.oemBackgroundRestrictionIntent(context)
        }
        AnimatedVisibility(
            visible = !isBatteryExempted && !batteryBannerDismissed,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(animationSpec = tween(durationMillis = 150)),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeOut(animationSpec = tween(durationMillis = 100))
        ) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                // Compact single-row banner. This used to be a full SleepCard with three
                // stacked action rows (~300dp tall) that pushed the moon button and every
                // stat below the fold, a warning is glanceable, not a screen.
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    // M3 banner shape: text block first with full width, actions on their
                    // own row underneath. Side-by-side buttons squeezed the label into
                    // ellipsis ("Battery optimizati…"), which defeats a warning.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // The warning icon doubles as the OEM-settings entry point
                            // (Samsung / Xiaomi / Huawei enforce a separate autostart
                            // restriction): a fourth trailing action squeezed the label,
                            // so the gear lives here instead of in the action row.
                            if (oemIntent != null) {
                                IconButton(onClick = { context.startActivity(oemIntent) }) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Device settings",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Battery optimization is on",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Overnight tracking may stop.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            IconButton(onClick = { batteryBannerDismissed = true }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                context.startActivity(
                                    dev.vic41148.somn.core.ui.battery.BatteryExemptionState.buildFixIntent(context)
                                )
                            }) {
                                Text("Fix")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Session type selector (SESS-01/02), only meaningful before a session starts.
        // Same expandable-card language as the habit sections, not a stock dropdown.
        var selectedSessionType by remember {
            mutableStateOf(dev.vic41148.somn.core.domain.model.SessionType.MAIN_SLEEP)
        }
        AnimatedVisibility(visible = trackingState == TrackingState.IDLE, enter = fadeIn(), exit = fadeOut()) {
            dev.vic41148.somn.core.ui.components.ExpandablePickerCard(
                title = "Session Type",
                icon = Icons.Default.Nightlight,
                iconColor = MaterialTheme.colorScheme.primary,
                options = dev.vic41148.somn.core.domain.model.SessionType.entries.map { it.displayName },
                selectedIndex = dev.vic41148.somn.core.domain.model.SessionType.entries.indexOf(selectedSessionType),
                onSelect = {
                    selectedSessionType = dev.vic41148.somn.core.domain.model.SessionType.entries[it]
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Moon button, start tracking
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(
                    if (trackingState == TrackingState.TRACKING)
                        MaterialTheme.colorScheme.tertiaryContainer
                    else
                        MaterialTheme.colorScheme.primaryContainer
                )
                .clickable {
                    when (trackingState) {
                        // Start a new session and open the tracking screen.
                        TrackingState.IDLE -> {
                            viewModel.startTracking(context, trackingMode, selectedSessionType)
                            onNavigateToTracking()
                        }
                        // A session is running but this screen is showing (app relaunched, or the
                        // process was restarted while the FGS kept tracking). The moon becomes the
                        // re-entry point to the tracking screen, without this, a live foreground
                        // session has no in-app way back to the Wake Up button.
                        TrackingState.TRACKING -> onNavigateToTracking()
                        TrackingState.PAUSED -> Unit
                    }
                }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Nightlight,
                    contentDescription = "Start sleep tracking",
                    modifier = Modifier.size(56.dp),
                    tint = if (trackingState == TrackingState.TRACKING)
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (trackingState == TrackingState.TRACKING) "Tracking..." else "Sleep",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (trackingState == TrackingState.TRACKING)
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Morning verdict, the prescription to Last Night's description. Sits above
        // it so the first thing a woken-up user reads is what today should look like.
        val showReadiness by viewModel.showReadinessCard.collectAsState()
        if (showReadiness) {
            MorningReadyCard(
                readiness = readiness,
                hasAnySessions = recentSessions.isNotEmpty(),
                restMode = restMode,
                showScores = showScores,
                onRingClick = onNavigateToTrends
            )

            // Daily Outlook, one template-built sentence, morning vs evening variants.
            Spacer(modifier = Modifier.height(16.dp))
            SleepCard(title = "Today") {
                Text(
                    text = outlook,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Breathing room before Last Night: without this the two same-colored cards
        // sit edge to edge and read as one clipped surface.
        Spacer(modifier = Modifier.height(16.dp))

        // Last night's score
        AnimatedVisibility(
            visible = lastSession != null && lastSession!!.isCompleted,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            lastSession?.let { session ->
                SleepCard(title = "Last Night") {
                    if (showScores) {
                        Text(
                            text = scoreTone(session.sleepScore),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = scoreColor(session.sleepScore),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val hours = session.sleepDurationMinutes / 60
                            val mins = session.sleepDurationMinutes % 60
                            Text(
                                text = "${hours}h ${mins}m",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "sleep duration",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (showScores) {
                        SleepScoreRing(
                            score = session.sleepScore,
                            size = 80.dp,
                            strokeWidth = 8.dp
                        )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    PillRow {
                        MetricChip(
                            label = "Efficiency",
                            value = "${session.sleepEfficiency.toInt()}%",
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        MetricChip(
                            label = "Deep",
                            value = "${session.deepSleepPercent.toInt()}%",
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        MetricChip(
                            label = "Wakes",
                            value = "${session.wakeEvents}",
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }

                    // Score explanation, hidden with the kill-switch (score-derived copy).
                    if (showScores) {
                    lastScore?.let { score ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = score.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    }
                }
            }
        }

        // Empty state
        if (lastSession == null) {
            Spacer(modifier = Modifier.height(24.dp))
            SleepCard {
                Text(
                    text = "Tap the moon to start tracking your first night of sleep.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // This week, per-stat rings so each stat's current state reads at a glance.
        weekSummary?.let { summary ->
            Spacer(modifier = Modifier.height(16.dp))
            SleepCard(title = "This Week") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (showScores) {
                    StatRing(
                        label = "Score",
                        value = "${summary.avgScore}",
                        fraction = summary.avgScore / 100f,
                        color = scoreColor(summary.avgScore),
                        size = 72.dp,
                        strokeWidth = 8.dp,
                        onClick = onNavigateToTrends
                    )
                    }
                    StatRing(
                        label = "Sleep",
                        value = formatDurationShort(summary.avgDurationMinutes),
                        fraction = (summary.avgDurationMinutes / 480f).coerceIn(0f, 1f),
                        color = MaterialTheme.colorScheme.primary,
                        size = 72.dp,
                        strokeWidth = 8.dp,
                        onClick = onNavigateToTrends
                    )
                    StatRing(
                        label = "Efficiency",
                        value = "${summary.avgEfficiencyPercent}%",
                        fraction = summary.avgEfficiencyPercent / 100f,
                        color = MaterialTheme.colorScheme.tertiary,
                        size = 72.dp,
                        strokeWidth = 8.dp,
                        onClick = onNavigateToTrends
                    )
                }
            }
        }

        // Spec §5 Weekly Rhythm card: 7 tappable day bars plus the x/7 ring,
        // the widget-level expression of the Consistency Streak (showing up only).
        val rhythmDays = remember(recentSessions) {
            val zone = java.time.ZoneId.systemDefault()
            val today = java.time.LocalDate.now()
            (6 downTo 0).map { ago ->
                val day = today.minusDays(ago.toLong())
                val session = recentSessions.filter { it.isCompleted }
                    .filter {
                        java.time.Instant.ofEpochMilli(it.startTimeMillis)
                            .atZone(zone).toLocalDate() == day
                    }.maxByOrNull { it.startTimeMillis }
                day to session
            }
        }
        if (rhythmDays.any { it.second != null }) {
            Spacer(modifier = Modifier.height(16.dp))
            WeeklyRhythmCard(
                days = rhythmDays,
                showScores = showScores,
                onDayClick = onNavigateToMorningReview,
                onRingClick = onNavigateToTrends
            )
        }

        // Sleep Debt card, the single most-actionable recovery tip on its face
        // (spec §5: one tip, not all four at once).
        val recoveryTip = remember(recoveryPlan) {
            recoveryPlan?.additionalMinutesPerNight?.takeIf { it > 0 }?.let {
                "An extra ${formatDurationShort(it)} tonight pays it down fastest."
            }
        }
        sleepDebt?.let { debt ->
            Spacer(modifier = Modifier.height(16.dp))
            SleepDebtHomeCard(debt = debt, tip = recoveryTip, onClick = onNavigateToDebt)
        }

        // Spec §5 Wind-down CTA, evenings only (17:00–23:00). One tap into the toolkit.
        val eveningNow = remember {
            java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) in 17..23
        }
        if (eveningNow) {
            Spacer(modifier = Modifier.height(16.dp))
            SleepCard(title = "Wind down") {
                Text(
                    text = "Screens off, lights low. A short wind-down now makes sleep come easier " +
                        "than scrolling until you feel tired.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onNavigateToWindDown,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Spa,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open wind-down toolkit")
                }
            }
        }

        // Spec §5 Smart Alarm card: next enabled alarm plus its smart window.
        val nextAlarmToday = remember(alarms) {
            val cal = java.util.Calendar.getInstance()
            val nowMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
                cal.get(java.util.Calendar.MINUTE)
            // Same wrap math as AlarmRepository.getNextAlarm, the card only adds Today/Tomorrow.
            val next = alarms.filter { it.isEnabled }.minByOrNull {
                ((it.hour * 60 + it.minute - nowMinutes) + 1440) % 1440
            }
            next?.let { it to (it.hour * 60 + it.minute > nowMinutes) }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (nextAlarmToday != null) {
            val (alarm, isToday) = nextAlarmToday
            SleepCard(
                title = "Smart Alarm",
                modifier = Modifier.clickable { onNavigateToAlarms() }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${if (isToday) "Today" else "Tomorrow"} · ${alarm.timeFormatted}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        val windowStart =
                            (alarm.hour * 60 + alarm.minute - alarm.wakeWindowMinutes + 1440) % 1440
                        Text(
                            text = "smart window ${formatClock(windowStart)} – ${alarm.timeFormatted}" +
                                if (alarm.label.isNotBlank()) " · ${alarm.label}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = "Next alarm",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        } else {
            SleepCard(title = "Smart Alarm") {
                Text(
                    text = "No alarm set. A smart window wakes you at the lightest moment, " +
                        "not mid-deep-sleep.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onNavigateToAlarms,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Set an alarm") }
            }
        }

        // The floating dock overlays content (no Scaffold slot), trailing
        // clearance so the last card scrolls clear of the pill.
        Spacer(modifier = Modifier.height(72.dp))
    }
}

/** Spec §4.1/§6: neutral-to-warm band words, never "poor" or "bad". */
private fun scoreTone(score: Int): String = when {
    score >= 80 -> "Great night 🌙"
    score >= 60 -> "Solid night 🌙"
    score >= 40 -> "Steady night 🌙"
    else -> "Recovering night 🌙"
}

/** "6:45 AM" from minutes-since-midnight, wraps past midnight. */
private fun formatClock(totalMinutes: Int): String {
    val wrapped = ((totalMinutes % 1440) + 1440) % 1440
    val h24 = wrapped / 60
    val h = if (h24 % 12 == 0) 12 else h24 % 12
    return "%d:%02d %s".format(h, wrapped % 60, if (h24 < 12) "AM" else "PM")
}

/**
 * Spec §5 Weekly Rhythm card: one bar per night (height = duration, color = stage
 * mix via the score tier), the x/7 consistency ring beside them. Bars with a session
 * jump straight to that Morning Review.
 */
@Composable
private fun WeeklyRhythmCard(
    days: List<Pair<java.time.LocalDate, SleepSession?>>,
    showScores: Boolean,
    onDayClick: (Long) -> Unit,
    onRingClick: () -> Unit
) {
    val logged = days.count { it.second != null }
    SleepCard(title = "Weekly Rhythm") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatRing(
                label = "Nights",
                value = "$logged/7",
                fraction = (logged / 7f).coerceIn(0f, 1f),
                color = MaterialTheme.colorScheme.primary,
                size = 64.dp,
                strokeWidth = 8.dp,
                onClick = onRingClick
            )
            Spacer(modifier = Modifier.width(12.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                days.forEach { (day, session) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = session != null) {
                                session?.let { onDayClick(it.id) }
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.BottomCenter,
                            modifier = Modifier.height(72.dp)
                        ) {
                            val fraction = session
                                ?.let { (it.sleepDurationMinutes / 480f).coerceIn(0f, 1f) }
                                ?: 0f
                            Box(
                                modifier = Modifier
                                    .width(12.dp)
                                    .height(
                                        if (session == null) 4.dp
                                        else (16 + 56 * fraction).dp
                                    )
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when {
                                            session == null ->
                                                MaterialTheme.colorScheme.surfaceVariant
                                            showScores -> scoreColor(session.sleepScore)
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = day.dayOfWeek.getDisplayName(
                                java.time.format.TextStyle.NARROW,
                                java.util.Locale.getDefault()
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (session != null) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MorningReadyCard(
    readiness: ReadinessResult?,
    hasAnySessions: Boolean,
    restMode: Boolean,
    showScores: Boolean = true,
    onRingClick: () -> Unit
) {
    Spacer(modifier = Modifier.height(16.dp))
    if (readiness == null) {
        if (!hasAnySessions) return
        SleepCard(title = "Morning Ready") {
            Text(
                text = "Track tonight and read the verdict for tomorrow here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        return
    }

    var expanded by remember { mutableStateOf(false) }
    val verdict = when (readiness.zone) {
        dev.vic41148.somn.core.domain.usecase.ReadinessZone.READY ->
            "Push today. Your body is primed for it."
        dev.vic41148.somn.core.domain.usecase.ReadinessZone.STEADY ->
            "A steady day. Normal load is fine, save max efforts."
        dev.vic41148.somn.core.domain.usecase.ReadinessZone.REST ->
            "Take it easy. Rest beats training today."
    }
    SleepCard(title = "Morning Ready") {
        if (restMode) {
            Text(
                text = "Rest Mode is on. Sick nights do not count.",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Spec §6.6: the verdict ring and its number hide with the kill-switch,
            // the words stay (guidance, not a score).
            if (showScores) {
            StatRing(
                label = readiness.zone.displayName,
                value = "${readiness.score}",
                fraction = readiness.score / 100f,
                color = scoreColor(readiness.score),
                showLabel = false,
                onClick = onRingClick
            )
            Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = readiness.zone.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (showScores) scoreColor(readiness.score)
                    else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = verdict,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (!readiness.isCalibrated) {
                    LinearProgressIndicator(
                        progress = { readiness.nightsUsed / 3f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${readiness.nightsUsed}/3 nights, settling your baseline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "${readiness.nightsUsed} nights in your baseline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                readiness.contributors.forEach { c ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = c.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (c.hasData) c.detail else "Not enough data yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (c.hasData && showScores) "${c.score}" else "–",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (c.hasData && showScores) scoreColor(c.score)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide details" else "Why this verdict")
            }
        }
    }
}

@Composable
private fun SleepDebtHomeCard(debt: SleepDebt, tip: String?, onClick: () -> Unit) {
    val levelColor = when (debt.level) {
        DebtLevel.NONE -> MaterialTheme.colorScheme.primary
        DebtLevel.MILD -> DebtMild
        DebtLevel.MODERATE -> DebtModerate
        DebtLevel.SEVERE -> MaterialTheme.colorScheme.error
    }

    SleepCard(
        title = "Sleep Debt",
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                val h = debt.totalDebtMinutes / 60
                val m = debt.totalDebtMinutes % 60
                val debtStr = when {
                    debt.totalDebtMinutes == 0 -> "None"
                    h > 0 -> "${h}h ${m}m"
                    else -> "${m}m"
                }
                Text(
                    text = debtStr,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = levelColor
                )
                Text(
                    text = "${debt.trend.glyph} ${debt.trend.displayName}  •  ${debt.level.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                contentDescription = "Sleep debt trend",
                tint = levelColor,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        if (tip != null) {
            Text(
                text = tip,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = "Tap for your 14-day breakdown and recovery plan →",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
