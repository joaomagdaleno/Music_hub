package com.joaomagdaleno.music_hub.utils

import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.di.App

object AppShortcuts {
    fun configureAppShortcuts(app: App) {
        val searchShortcut = ShortcutInfoCompat.Builder(app, "search")
            .setShortLabel(app.getString(R.string.search))
            .setIcon(IconCompat.createWithResource(app, R.drawable.ic_search))
            .setIntent(Intent(Intent.ACTION_VIEW, "echo://search".toUri()))
            .build()

        val libraryShortcut = ShortcutInfoCompat.Builder(app, "library")
            .setShortLabel(app.getString(R.string.library))
            .setIcon(IconCompat.createWithResource(app, R.drawable.ic_library))
            .setIntent(Intent(Intent.ACTION_VIEW, "echo://library".toUri()))
            .build()

        ShortcutManagerCompat.setDynamicShortcuts(app, listOf(searchShortcut, libraryShortcut))
    }
}