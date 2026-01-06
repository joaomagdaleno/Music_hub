package com.joaomagdaleno.music_hub.common.clients

import com.joaomagdaleno.music_hub.common.providers.SettingsProvider
import com.joaomagdaleno.music_hub.common.settings.Setting
import com.joaomagdaleno.music_hub.common.settings.Settings

/**
 * A client that listens to changes in the extension's [Settings].
 */
interface SettingsChangeListenerClient : SettingsProvider {
    /**
     * Called when the extension's [Settings] have changed or when a [Setting] has been clicked.
     *
     * @param settings The new [Settings].
     * @param key The key of the setting that has changed or clicked. Null if all settings have changed.
     */
    suspend fun onSettingsChanged(settings: Settings, key: String?)
}