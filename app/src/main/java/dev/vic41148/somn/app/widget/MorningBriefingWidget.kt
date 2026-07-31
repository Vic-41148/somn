package dev.vic41148.somn.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.vic41148.somn.app.R
import dev.vic41148.somn.core.data.database.dao.SleepSessionDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Home screen widget that displays the last sleep session's score
 * and a contextual insight. Updates every 30 minutes via system
 * and on each onUpdate callback.
 */
class MorningBriefingWidget : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SleepSessionDaoEntryPoint {
        fun sleepSessionDao(): SleepSessionDao
    }

    private val widgetScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        widgetScope.launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    SleepSessionDaoEntryPoint::class.java
                )
                val sleepSessionDao = entryPoint.sleepSessionDao()

                // SESS-04: morning briefing should reflect last night's main sleep, not a stray nap.
                val recentSessions = sleepSessionDao.getRecentMainSleepSessions(1)
                val session = recentSessions.firstOrNull()

                val views = RemoteViews(context.packageName, R.layout.widget_morning_briefing)

                if (session != null && session.isCompleted) {
                    val score = session.sleepScore
                    val durationHours = session.sleepDurationMinutes / 60
                    val durationMins = session.sleepDurationMinutes % 60

                    views.setTextViewText(R.id.widget_score, "$score")

                    val insight = when {
                        score >= 85 -> "Excellent sleep! ${durationHours}h ${durationMins}m"
                        score >= 70 -> "Good night. ${durationHours}h ${durationMins}m"
                        score >= 50 -> "Room to improve. ${durationHours}h ${durationMins}m"
                        else -> "Tough night. ${durationHours}h ${durationMins}m — prioritize rest tonight."
                    }
                    views.setTextViewText(R.id.widget_insight, insight)
                } else {
                    views.setTextViewText(R.id.widget_score, "--")
                    views.setTextViewText(R.id.widget_insight, "No sleep data yet. Track tonight!")
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                // Fallback if DB not available
                val views = RemoteViews(context.packageName, R.layout.widget_morning_briefing)
                views.setTextViewText(R.id.widget_score, "--")
                views.setTextViewText(R.id.widget_insight, "Open Somn to start tracking")
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
