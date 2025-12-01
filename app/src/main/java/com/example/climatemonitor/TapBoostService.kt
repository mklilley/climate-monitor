package com.example.climatemonitor

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class TapBoostService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ClimateMonitor", "TapBoostService started")

        // Just keep the process alive for 200ms
        Thread {
            try { Thread.sleep(200) } catch (_: Exception) {}
            Log.d("ClimateMonitor", "TapBoostService self-stop")
            stopSelf()
        }.start()

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
