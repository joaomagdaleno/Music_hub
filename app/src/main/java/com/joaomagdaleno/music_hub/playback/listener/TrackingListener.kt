package com.joaomagdaleno.music_hub.playback.listener

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.joaomagdaleno.music_hub.common.models.TrackDetails
import com.joaomagdaleno.music_hub.data.repository.MusicRepository
import com.joaomagdaleno.music_hub.playback.MediaItemUtils.context
import com.joaomagdaleno.music_hub.playback.MediaItemUtils.origin
import com.joaomagdaleno.music_hub.playback.MediaItemUtils.track
import com.joaomagdaleno.music_hub.playback.PlayerState
import com.joaomagdaleno.music_hub.utils.PauseTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@UnstableApi
@UnstableApi
class TrackingListener(
    // We pass context for SharedPreferences access (ResumptionUtils)
    private val context: android.content.Context,
    private val player: Player,
    private val scope: CoroutineScope,
    private val repository: MusicRepository,
    private val currentFlow: MutableStateFlow<PlayerState.Current?>,
    private val throwableFlow: MutableSharedFlow<Throwable>
) : Player.Listener {

    // In monolithic mode, trackers are stubbed
    // TODO: Implement local history tracking via repository

    private var current: MediaItem? = null
    private var previousId: String? = null

    private suspend fun getDetails() = withContext(Dispatchers.Main) {
        current?.let { curr ->
            val (pos, total) = player.currentPosition to player.duration.takeIf { it != C.TIME_UNSET }
            TrackDetails(curr.origin, curr.track, curr.context, pos, total)
        }
    }

    private fun trackMedia(
        block: suspend (details: TrackDetails?) -> Unit
    ) {
        scope.launch {
            val details = getDetails()
            // In monolithic mode, just call the block with details
            // Tracker sources are no longer used
            block(details)
        }
    }

    private val mutex = Mutex()
    private val timers = mutableMapOf<String, PauseTimer>()
    private fun onTrackChanged(mediaItem: MediaItem?) {
        previousId = current?.origin
        current = mediaItem

        // Save Queue and Index on track change
        scope.launch {
            ResumptionUtils.saveQueue(context, player)
        }

        scope.launch {
            mutex.withLock {
                timers.forEach { (_, timer) -> timer.pause() }
                timers.clear()
            }
            trackMedia { details ->
                // Stubbed: onTrackChanged for local history
                // TODO: Save to local history via repository
                details ?: return@trackMedia
                // Mark as played timer stubbed
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        scope.launch {
            playState.value = getDetails() to isPlaying
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
    ) {
        // Save current position on discontinuity (seek, etc)
        scope.launch {
            ResumptionUtils.saveCurrentPos(context, newPosition.positionMs)
        }

        scope.launch {
            val isPlaying = withContext(Dispatchers.Main) { player.isPlaying }
            playState.value = getDetails() to isPlaying
        }
        if (reason == 2 || current == null) return
        val mediaItem = newPosition.mediaItem ?: return
        if (oldPosition.mediaItem != mediaItem) return
        if (newPosition.positionMs != 0L) return

        onTrackChanged(current)
    }

    override fun onPlayerError(error: PlaybackException) {
        onTrackChanged(null)
    }

    private val playState = MutableStateFlow<Pair<TrackDetails?, Boolean>>(null to false)

    init {
        scope.launch {
            currentFlow.map { it?.let { curr -> curr.mediaItem.takeIf { curr.isLoaded } } }
                .distinctUntilChanged().collectLatest {
                    onTrackChanged(it)
                }
        }
        scope.launch {
            @OptIn(FlowPreview::class)
            playState.debounce(500).collectLatest { (_, isPlaying) ->
                mutex.withLock {
                    timers.forEach { (_, timer) ->
                        if (isPlaying) timer.resume()
                        else timer.pause()
                    }
                }
                trackMedia { details ->
                    // Stubbed: onPlayingStateChanged for local history
                    // TODO: Update play state in local history
                }
            }
        }
    }
}