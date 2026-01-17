package com.joaomagdaleno.music_hub.playback.listener

import android.content.SharedPreferences
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Timeline
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.download.Downloader
import com.joaomagdaleno.music_hub.common.models.MediaState
import com.joaomagdaleno.music_hub.playback.MediaItemUtils
// Imports removed
import com.joaomagdaleno.music_hub.playback.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerRadio(
    private val app: App,
    private val scope: CoroutineScope,
    private val player: Player,
    private val throwFlow: MutableSharedFlow<Throwable>,
    private val stateFlow: MutableStateFlow<PlayerState.Radio>,
    private val repository: com.joaomagdaleno.music_hub.data.repository.MusicRepository,
    private val downloadFlow: StateFlow<List<Downloader.Info>>
) : Player.Listener {

    companion object {
        const val AUTO_START_RADIO = "auto_start_radio"
        // Simplified start using generic Repository
        suspend fun start(
            repository: com.joaomagdaleno.music_hub.data.repository.MusicRepository,
            item: EchoMediaItem
        ): PlayerState.Radio.Loaded? {
            if (item !is com.joaomagdaleno.music_hub.common.models.Track) return null
            val radio = repository.getRadio(item.id)
            if (radio.isEmpty()) return null
            // For PagedData, we wrap the list. 
            // In internal, we probably just want a list. 
            // But PlayerRadio expects PagedData to handle infinite loading?
            // "cont" suggests continuation. Piped radio is often just a list.
            // We'll wrap it in a simple 1-page PagedData for now to minimize refactor of internal logic.
            return PlayerState.Radio.Loaded("internal", item, null) {
                // Continuation loading not implemented yet for internal radio
                com.joaomagdaleno.music_hub.common.helpers.Page(emptyList(), null)
            }
        }

        suspend fun play(
            player: Player,
            downloadFlow: StateFlow<List<Downloader.Info>>,
            app: App,
            stateFlow: MutableStateFlow<PlayerState.Radio>,
            loaded: PlayerState.Radio.Loaded
        ) {
            stateFlow.value = PlayerState.Radio.Loading
            val tracks = loaded.tracks(loaded.cont) ?: return

            stateFlow.value = if (tracks.continuation == null) PlayerState.Radio.Empty
            else loaded.copy(cont = tracks.continuation)

            val item = tracks.data.map {
                MediaItemUtils.build(
                    app,
                    downloadFlow.value,
                    MediaState.Unloaded(loaded.clientId, it), // ClientId is "internal" or similar
                    loaded.context
                )
            }

            withContext(Dispatchers.Main) {
                player.addMediaItems(item)
                player.prepare()
            }
        }
    }

    private suspend fun loadPlaylist() {
        val mediaItem = withContext(Dispatchers.Main) { player.currentMediaItem } ?: return
        val item = MediaItemUtils.getTrack(mediaItem)
        stateFlow.value = PlayerState.Radio.Loading
        val loaded = start(repository, item)
        stateFlow.value = loaded ?: PlayerState.Radio.Empty
        if (loaded != null) play(player, downloadFlow, app, stateFlow, loaded)
    }

    private var autoStartRadio = app.settings.getBoolean(AUTO_START_RADIO, true)

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { pref, key ->
        if (key != AUTO_START_RADIO) return@OnSharedPreferenceChangeListener
        autoStartRadio = pref.getBoolean(AUTO_START_RADIO, true)
    }

    init {
        app.settings.registerOnSharedPreferenceChangeListener(listener)
    }

    private suspend fun startRadio() {
        if (!autoStartRadio) return
        val shouldNotStart = withContext(Dispatchers.Main) {
            player.run {
                currentMediaItem == null || repeatMode != REPEAT_MODE_OFF || hasNextMediaItem()
            }
        }
        if (shouldNotStart) return
        when (val state = stateFlow.value) {
            is PlayerState.Radio.Loading -> {}
            is PlayerState.Radio.Empty -> loadPlaylist()
            is PlayerState.Radio.Loaded -> play(player, downloadFlow, app, stateFlow, state)
        }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        scope.launch { startRadio() }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (player.mediaItemCount == 0) stateFlow.value = PlayerState.Radio.Empty
        scope.launch { startRadio() }
    }
}

