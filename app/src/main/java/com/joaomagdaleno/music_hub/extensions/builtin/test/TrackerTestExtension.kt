package com.joaomagdaleno.music_hub.extensions.builtin.test

import com.joaomagdaleno.music_hub.common.clients.TrackerMarkClient
import com.joaomagdaleno.music_hub.common.models.ExtensionType
import com.joaomagdaleno.music_hub.common.models.ImportType
import com.joaomagdaleno.music_hub.common.models.Metadata
import com.joaomagdaleno.music_hub.common.models.TrackDetails
import com.joaomagdaleno.music_hub.common.settings.Setting
import com.joaomagdaleno.music_hub.common.settings.Settings

class TrackerTestExtension : TrackerMarkClient {
    companion object {
        val metadata = Metadata(
            "TrackerTestExtension",
            "",
            ImportType.BuiltIn,
            ExtensionType.TRACKER,
            "test",
            "Tracker Test Extension",
            "1.0.0",
            "Test extension for offline testing",
            "Test",
        )
    }

    override suspend fun getSettingItems() = listOf<Setting>()
    override fun setSettings(settings: Settings) {}

    override suspend fun onTrackChanged(details: TrackDetails?) {
        println("onTrackChanged ${details?.track?.id}")
    }

    override suspend fun getMarkAsPlayedDuration(details: TrackDetails): Long? {
        return details.totalDuration?.div(3)
    }

    override suspend fun onMarkAsPlayed(details: TrackDetails) {
        println("onMarkAsPlayed: ${details.track.id}")
    }

    override suspend fun onPlayingStateChanged(details: TrackDetails?, isPlaying: Boolean) {
        println("onPlayingStateChanged $isPlaying: ${details?.track?.id}")
    }
}