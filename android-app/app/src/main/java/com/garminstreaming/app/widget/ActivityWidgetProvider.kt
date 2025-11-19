package com.garminstreaming.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.garminstreaming.app.MainActivity
import com.garminstreaming.app.R
import com.garminstreaming.app.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Widget provider for displaying activity statistics on home screen
 */
class ActivityWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Widget added for the first time
    }

    override fun onDisabled(context: Context) {
        // Last widget removed
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_activity)

            // Set click intent to open app
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_open_app, pendingIntent)

            // Load stats from database
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val database = AppDatabase.getInstance(context)
                    val dao = database.activitySessionDao()

                    val sessionCount = dao.getSessionCount()
                    val totalDistance = dao.getTotalDistance() ?: 0.0
                    val totalDuration = dao.getTotalDuration() ?: 0L

                    // Format values
                    val distanceKm = totalDistance / 1000.0
                    val distanceText = "%.2f".format(distanceKm)

                    val totalSeconds = totalDuration / 1000
                    val hours = totalSeconds / 3600
                    val minutes = (totalSeconds % 3600) / 60
                    val durationText = if (hours > 0) {
                        "%d:%02d".format(hours, minutes)
                    } else {
                        "%02d:00".format(minutes)
                    }

                    withContext(Dispatchers.Main) {
                        views.setTextViewText(R.id.widget_distance, distanceText)
                        views.setTextViewText(R.id.widget_duration, durationText)
                        views.setTextViewText(R.id.widget_sessions, sessionCount.toString())

                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                } catch (e: Exception) {
                    // If database not available, show defaults
                    withContext(Dispatchers.Main) {
                        views.setTextViewText(R.id.widget_distance, "0.00")
                        views.setTextViewText(R.id.widget_duration, "00:00")
                        views.setTextViewText(R.id.widget_sessions, "0")

                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                }
            }

            // Initial update with defaults (will be replaced by coroutine)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /**
         * Request widget update from anywhere in the app
         */
        fun requestUpdate(context: Context) {
            val intent = Intent(context, ActivityWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(android.content.ComponentName(context, ActivityWidgetProvider::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}
