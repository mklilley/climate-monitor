package com.example.climatemonitor

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.*
import java.util.concurrent.TimeUnit
import android.util.Log

class ClimateWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_REFRESH = "com.example.climatemonitor.ACTION_REFRESH"
        private const val ACTION_DEBUG_BOOT = "com.example.climatemonitor.DEBUG_BOOT" // 👈 for adb testing
        private const val UNIQUE_PERIODIC_WORK = "ClimateMonitorWork"
        private const val SENSOR_URL = BuildConfig.SENSOR_URL

        private fun periodicRequest(): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            return PeriodicWorkRequestBuilder<SensorWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(workDataOf("url" to SENSOR_URL))
                .build()
        }

        private fun oneShotRequest(): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            return OneTimeWorkRequestBuilder<SensorWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf("url" to SENSOR_URL))
                .build()
        }

        private fun enqueuePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest()
            )
        }

        private fun enqueueOneShot(context: Context) {
            WorkManager.getInstance(context).enqueue(oneShotRequest())
        }
    }

    override fun onEnabled(context: Context) {
        enqueuePeriodic(context) // start background refresh when first widget added
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Log.d("ClimateMonitor", "onUpdate called → reattaching tap PendingIntents")
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // Tap-to-refresh anywhere on widget
        val intent = Intent(context, ClimateWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val pi = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(R.id.widget_root, pi)

        manager.updateAppWidget(ids, views)

        enqueueOneShot(context)   // immediate refresh when added
        enqueuePeriodic(context)  // ensure periodic updates keep running
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d("ClimateMonitor", "onReceive called with action=${intent.action}")

        when (intent.action) {
            ACTION_REFRESH -> {
                Log.d("ClimateMonitor", "User tapped → refreshing now")
                enqueueOneShot(context)
            }
            Intent.ACTION_BOOT_COMPLETED,
            ACTION_DEBUG_BOOT -> {
                Log.d("ClimateMonitor", "Boot (real or debug) → restoring widget setup")

                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, ClimateWidgetProvider::class.java)
                val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

                if (allWidgetIds.isNotEmpty()) {
                    onUpdate(context, appWidgetManager, allWidgetIds)
                }

                enqueuePeriodic(context)
            }
        }
    }
}
