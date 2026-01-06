package com.joaomagdaleno.music_hub.widget

import android.graphics.Bitmap
import androidx.media3.session.MediaController
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.playback.PlayerService.Companion.getController

object ControllerHelper {

    var controller: MediaController? = null
    var callback: (() -> Unit)? = null
    var image: Bitmap? = null
    var listener: WidgetPlayerListener? = null

    val map = mutableMapOf<String, () -> Unit>()
    fun register(app: App, key: String, updateCallback: () -> Unit) {
        map[key] = updateCallback
        if (callback != null) return
        callback = getController(app.context) {
            controller = it
            val playerListener = WidgetPlayerListener { img ->
                image = img
                updateWidgets()
            }
            listener = playerListener
            it.addListener(playerListener)
            playerListener.controller = it
            updateWidgets()
        }
    }

    fun unregister(key: String) {
        map.remove(key)
        if (map.isNotEmpty()) return
        callback?.invoke()
        controller = null
        listener?.removed()
        listener = null
        image = null
    }

    fun updateWidgets() {
        map.values.forEach { it() }
    }
}