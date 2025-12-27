package com.example.climatemonitor

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import android.util.Log

class SensorWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val client = OkHttpClient()

    override suspend fun doWork(): Result {
        WidgetLogger.log(applicationContext, "SensorWorker.doWork() started")

        val url = inputData.getString("url") ?: BuildConfig.SENSOR_URL

        if (url.isBlank()) {
            Log.e("ClimateMonitor", "No SENSOR_URL configured!")
            WidgetLogger.log(applicationContext, "ERROR: No SENSOR_URL configured")
            return Result.failure()
        }

        return try {
            WidgetLogger.log(applicationContext, "Requesting URL: $url")

            val request = Request.Builder()
                .url(url)
                .header("Cache-Control", "no-cache")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val msg = "HTTP ${response.code} for $url"
                Log.w("ClimateMonitor", msg)
                WidgetLogger.log(applicationContext, "WARN: $msg")
                return Result.retry()
            }

            val jsonString = response.body?.string()
            if (jsonString == null) {
                WidgetLogger.log(applicationContext, "ERROR: Empty response body → retrying")
                return Result.retry()
            }

            Log.d("ClimateMonitor", "Fetched JSON: $jsonString")
            WidgetLogger.log(applicationContext, "Fetched JSON: $jsonString")

            val json = JSONObject(jsonString)

            val temp = json.optDouble("temperature", Double.NaN)
            val humidity = json.optDouble("humidity", Double.NaN)
            val co2 = json.optInt("co2", -1)

            val parsedMsg = "Parsed → temp=$temp humidity=$humidity co2=$co2"
            Log.d("ClimateMonitor", parsedMsg)
            WidgetLogger.log(applicationContext, parsedMsg)

            val manager = AppWidgetManager.getInstance(applicationContext)
            val widgetIds = manager.getAppWidgetIds(
                ComponentName(applicationContext, ClimateWidgetProvider::class.java)
            )

            if (widgetIds.isEmpty()) {
                WidgetLogger.log(applicationContext, "No widgets found; skipping update")
                return Result.success()
            }

            WidgetLogger.log(applicationContext, "Updating widgets: ${widgetIds.toList()}")

            for (id in widgetIds) {
                val views = RemoteViews(applicationContext.packageName, R.layout.widget_layout).apply {
                    setTextViewText(R.id.temp_text,
                        if (temp.isNaN()) "--°" else String.format("%.1f°", temp))
                    setTextViewText(R.id.humidity_text,
                        if (humidity.isNaN()) "--%" else "${humidity.toInt()}%")
                    setTextViewText(R.id.co2_text,
                        if (co2 < 0) "-- ppm" else "$co2 ppm")
                    setOnClickPendingIntent(
                        R.id.widget_root,
                        ClimateWidgetProvider.createRefreshPendingIntent(applicationContext, id)
                    )
                }
                manager.updateAppWidget(id, views)
            }

            WidgetLogger.log(applicationContext, "Widget updated successfully")
            Log.d("ClimateMonitor", "Widget updated successfully")

            Result.success()

        } catch (e: Exception) {
            val msg = "ERROR: ${e::class.java.simpleName}: ${e.message}"
            Log.e("ClimateMonitor", msg, e)
            WidgetLogger.log(applicationContext, msg)
            Result.retry()
        }
    }

}
