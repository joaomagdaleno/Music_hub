package com.joaomagdaleno.music_hub.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLogger {
    private const val FILE_NAME = "music_hub_debug.txt"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var logFile: File? = null

    fun init(context: Context) {
        // Try multiple locations in order of preference
        val locations = listOf(
            // 1. Public Download folder (most accessible)
            { 
                @Suppress("DEPRECATION")
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            },
            // 2. Public Documents folder
            { 
                @Suppress("DEPRECATION")
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            },
            // 3. External files dir (app-specific but accessible via file manager)
            { context.getExternalFilesDir(null) },
            // 4. Internal storage (last resort)
            { context.filesDir }
        )

        for (locationProvider in locations) {
            try {
                val dir = locationProvider() ?: continue
                if (!dir.exists()) dir.mkdirs()
                
                val file = File(dir, FILE_NAME)
                if (!file.exists()) file.createNewFile()
                
                // Test write
                FileWriter(file, true).use { it.append("") }
                
                logFile = file
                Log.d("FileLogger", "Logger initialized at: ${file.absolutePath}")
                log("FileLogger", "=== App started ===")
                log("FileLogger", "Log path: ${file.absolutePath}")
                return
            } catch (e: Exception) {
                Log.w("FileLogger", "Failed to init at location: ${e.message}")
            }
        }
        
        Log.e("FileLogger", "All log locations failed!")
    }

    @Synchronized
    fun log(tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val logMessage = StringBuilder().apply {
            append(timestamp).append(" [").append(tag).append("] ").append(message)
            throwable?.let {
                append("\n").append(Log.getStackTraceString(it))
            }
        }.toString()
        
        Log.d(tag, message)
        if (throwable != null) Log.e(tag, message, throwable)

        logFile?.let { file ->
            try {
                FileWriter(file, true).use { writer ->
                    writer.append(logMessage).append("\n")
                }
            } catch (e: IOException) {
                Log.e("FileLogger", "Failed to write log", e)
            }
        }
    }
    
    fun getLogPath(): String {
        return logFile?.absolutePath ?: "Not initialized"
    }
}

