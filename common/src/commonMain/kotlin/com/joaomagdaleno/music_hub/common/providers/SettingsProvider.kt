package com.joaomagdaleno.music_hub.common.providers

import com.joaomagdaleno.music_hub.common.settings.Setting
import com.joaomagdaleno.music_hub.common.settings.Settings

/**
 * Interface to provide [Settings] to the extension
 */
interface SettingsProvider {
    /**
     * List of [Setting]s to be displayed in the settings screen
     */
    suspend fun getSettingItems() : List<Setting>

    /**
     * Called when the extension is initialized, to provide the [Settings] to the extension
     */
    fun setSettings(settings: Settings)
}