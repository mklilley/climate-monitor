package com.example.climatemonitor

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class ClimateWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_REFRESH = "com.example.climatemonitor.ACTION_REFRESH"
        private const val UNIQUE_PERIODIC_WORK = "ClimateMonitorWork"
        private const val TAG = "ClimateMonitor"
        private const val SENSOR_URL = BuildConfig.SENSOR_URL

        /**
         * Create the PendingIntent for widget taps.
         * Now always uses a broadcast (no foreground-service PendingIntent).
         */
        @JvmStatic
        internal fun createRefreshPendingIntent(context: Context, widgetId: Int): PendingIntent {
            WidgetLogger.log(context, "Creating PI for widgetId=$widgetId")

            val intent = Intent(context, ClimateWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }

            return PendingIntent.getBroadcast(
                context,
                widgetId, // unique per widget instance
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun periodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<SensorWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInputData(workDataOf("url" to SENSOR_URL))
                .build()

        private fun oneShotRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<SensorWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInputData(workDataOf("url" to SENSOR_URL))
                .build()

        private fun enqueuePeriodic(context: Context) {
            WidgetLogger.log(context, "Enqueue periodic work")
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest()
            )
        }

        private fun enqueueOneShot(context: Context) {
            WidgetLogger.log(context, "Enqueue ONE-SHOT worker")
            WorkManager.getInstance(context).enqueue(oneShotRequest())
        }
    }

    override fun onEnabled(context: Context) {
        Log.d(TAG, "Widget enabled → start periodic work")
        WidgetLogger.log(context, "onEnabled(): first widget added → start periodic work")
        enqueuePeriodic(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Log.d(TAG, "onUpdate() for widgetIds=${ids.toList()}")
        WidgetLogger.log(context, "onUpdate() for widgetIds=${ids.toList()}")

        for (id in ids) {
            WidgetLogger.log(context, "Binding click PI for widgetId=$id")

            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            val pi = createRefreshPendingIntent(context, id)

            views.setOnClickPendingIntent(R.id.widget_root, pi)
            manager.updateAppWidget(id, views)
        }

        WidgetLogger.log(context, "onUpdate(): request immediate refresh")
        enqueueOneShot(context)

        WidgetLogger.log(context, "onUpdate(): ensure periodic worker stays running")
        enqueuePeriodic(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val action = intent.action
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)

        Log.d(TAG, "onReceive() action=$action id=$widgetId")
        WidgetLogger.log(context, "onReceive() action=$action widgetId=$widgetId")

        if (action != ACTION_REFRESH) {
            WidgetLogger.log(context, "onReceive(): ignoring non-refresh event")
            return
        }

        WidgetLogger.log(context, "Tap received → rebinding PendingIntents")

        val manager = AppWidgetManager.getInstance(context)
        val allWidgetIds = manager.getAppWidgetIds(
            ComponentName(context, ClimateWidgetProvider::class.java)
        )

        WidgetLogger.log(context, "Rebinding PI for ALL widgets: ${allWidgetIds.toList()}")

        for (id in allWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            val pi = createRefreshPendingIntent(context, id)
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            manager.updateAppWidget(id, views)
        }

        // Start TapBoostService as a *normal* service (no foreground)
        WidgetLogger.log(context, "Starting TapBoostService via startService()")
        try {
            context.startService(Intent(context, TapBoostService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start TapBoostService", e)
            WidgetLogger.log(context, "ERROR: Failed to start TapBoostService: ${e.message}")
        }

        WidgetLogger.log(context, "Triggering one-shot worker after tap")
        enqueueOneShot(context)
    }
}
