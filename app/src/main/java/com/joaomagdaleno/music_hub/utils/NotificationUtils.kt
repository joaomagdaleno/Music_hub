package com.joaomagdaleno.music_hub.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat

object NotificationUtils {

    fun createNotificationChannel(
        context: Context,
        id: String,
        @StringRes name: Int,
        @StringRes description: Int = 0,
        importance: Int = NotificationManagerCompat.IMPORTANCE_DEFAULT
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appName = context.getString(name)
            val channelDescription = if (description != 0) context.getString(description) else null
            val channel = NotificationChannel(id, appName, importance).apply {
                this.description = channelDescription
            }
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }
    }
}
