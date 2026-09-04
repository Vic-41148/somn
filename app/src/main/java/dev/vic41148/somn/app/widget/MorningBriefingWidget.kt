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
import dev.vic41148.somn.core.domain.model.SleepSession
import dev.vic41148.somn.core.domain.usecase.assessReadiness
import dev.vic41148.somn.core.domain.usecase.buildOutlook
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
                // Readiness runs on lightweight domain copies (the entity→domain mapper lives
                // private in SleepRepository) with no debt/vitals here — the engine degrades
                // to sleep signals, which is all a 30-minute widget refresh needs.
                val recentEntities = sleepSessionDao.getRecentMainSleepSessions(14)
                val session = recentEntities.firstOrNull()

                val views = RemoteViews(context.packageName, R.layout.widget_morning_briefing)

                if (session != null && session.isCompleted) {
                    val score = session.sleepScore

                    views.setTextViewText(R.id.widget_score, "$score")

                    val domainSessions = recentEntities.map {
                        SleepSession(
                            startTimeMillis = it.startTimeMillis,
                            sleepDurationMinutes = it.sleepDurationMinutes,
                            sleepScore = it.sleepScore,
                            isCompleted = it.isCompleted
                        )
                    }
                    val readiness = assessReadiness(domainSessions, null, null)
                    val hour = java.util.Calendar.getInstance()
                        .get(java.util.Calendar.HOUR_OF_DAY)
                    val insight = buildOutlook(readiness, null, null, isMorning = hour < 12)
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
