package com.joaomagdaleno.music_hub.common.providers

import com.joaomagdaleno.music_hub.common.settings.Settings

/**
 * Interface to provide global [Settings] to the extension
 */
interface GlobalSettingsProvider {
    /**
     * Called when the extension is initialized, to provide the global [Settings] to the extension
     */
    fun setGlobalSettings(globalSettings: Settings)
}