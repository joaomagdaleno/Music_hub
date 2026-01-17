package com.joaomagdaleno.music_hub.ui.player.more.info

import androidx.lifecycle.viewModelScope
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.download.Downloader
import com.joaomagdaleno.music_hub.common.models.MediaState
import com.joaomagdaleno.music_hub.playback.MediaItemUtils
import com.joaomagdaleno.music_hub.playback.PlayerState
import com.joaomagdaleno.music_hub.ui.media.MediaDetailsViewModel
import com.joaomagdaleno.music_hub.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

class TrackInfoViewModel(
    val app: App,
    val repository: MusicRepository,
    playerState: PlayerState,
    downloader: Downloader
) : MediaDetailsViewModel(
    downloader, app, true, repository
) {
    val currentFlow = playerState.current

    override fun getItem(): Triple<String, EchoMediaItem, Boolean>? {
        return currentFlow.value?.let { (_, item) ->
            Triple("internal", MediaItemUtils.getTrack(item), MediaItemUtils.isLoaded(item))
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            listOf(playerState.current, refreshFlow).merge().collectLatest {
                itemResultFlow.value = null
                val current = currentFlow.value ?: return@collectLatest
                val track = if (current.isLoaded) current.track else return@collectLatest
                
                // Use Repository to fetch track details if needed
                val loadedTrack = repository.getTrack(track.id) ?: track
                itemResultFlow.value = Result.success(MediaState.Loaded(origin = "internal", item = loadedTrack))
            }
        }
    }
}