package com.example.climatemonitor

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object WidgetLogger {

    private const val MAX_SIZE = 1_000_000 // 1MB

    fun log(context: Context, msg: String) {
        val timestamp = java.time.LocalDateTime.now().toString()
        val line = "$timestamp $msg\n"

        // 1) Internal app-only file
        writeToFile(context.filesDir.resolve("widget.log"), line)

        // 2) External (adb-readable)
        context.getExternalFilesDir(null)?.let { extDir ->
            writeToFile(extDir.resolve("widget.log"), line)
        }

        // 3) Public Download folder (visible to Files app)
        val public = File(
            android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            ),
            "climate_widget.log"
        )
        writeToFile(public, line)
    }

    private fun writeToFile(file: File, line: String) {
        try {
            if (!file.exists()) file.createNewFile()

            if (file.length() > MAX_SIZE) {
                file.writeText("")  // truncate
            }

            file.appendText(line)
        } catch (_: Exception) {
            // ignore write failures
        }
    }
}
