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


class SensorWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val client = OkHttpClient()

    override suspend fun doWork(): Result {
        val url = inputData.getString("url") ?: BuildConfig.SENSOR_URL

        if (url.isBlank()) {
            android.util.Log.e("ClimateMonitor", "No SENSOR_URL configured!")
            return Result.failure()
        }

        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return Result.retry()
            val jsonString = response.body?.string() ?: return Result.retry()
            val json = JSONObject(jsonString)

            val temp = json.optDouble("temperature", Double.NaN)
            val humidity = json.optDouble("humidity", Double.NaN)
            val co2 = json.optInt("co2", -1)

            val views = RemoteViews(applicationContext.packageName, R.layout.widget_layout).apply {
                setTextViewText(R.id.temp_text,
                    if (temp.isNaN()) "--°" else String.format("%.1f°", temp))
                setTextViewText(R.id.humidity_text,
                    if (humidity.isNaN()) "--%" else "${humidity.toInt()}%")
                setTextViewText(R.id.co2_text,
                    if (co2 < 0) "-- ppm" else "$co2 ppm")
            }

            val manager = AppWidgetManager.getInstance(applicationContext)
            val widget = ComponentName(applicationContext, ClimateWidgetProvider::class.java)
            manager.updateAppWidget(widget, views)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
