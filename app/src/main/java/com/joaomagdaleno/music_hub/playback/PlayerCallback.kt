package com.joaomagdaleno.music_hub.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import androidx.core.os.bundleOf
import androidx.media3.common.Player
import androidx.media3.common.Rating
import androidx.media3.common.ThumbRating
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionResult.RESULT_SUCCESS
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.joaomagdaleno.music_hub.common.helpers.PagedData
import com.joaomagdaleno.music_hub.common.models.*
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.download.Downloader
import com.joaomagdaleno.music_hub.playback.MediaItemUtils
import com.joaomagdaleno.music_hub.playback.exceptions.PlayerException
import com.joaomagdaleno.music_hub.playback.listener.PlayerRadio
import com.joaomagdaleno.music_hub.utils.CoroutineUtils
import com.joaomagdaleno.music_hub.utils.Serializer
import com.joaomagdaleno.music_hub.utils.image.ImageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@OptIn(UnstableApi::class)
class PlayerCallback(
    override val app: App,
    override val scope: CoroutineScope,
    private val throwableFlow: MutableSharedFlow<Throwable>,
    private val repository: com.joaomagdaleno.music_hub.data.repository.MusicRepository,
    private val radioFlow: MutableStateFlow<PlayerState.Radio>,
    override val downloadFlow: StateFlow<List<Downloader.Info>>,
) : AndroidAutoCallback(app, scope, kotlinx.coroutines.flow.MutableStateFlow(emptyList()), downloadFlow) {

    override fun onConnect(
        session: MediaSession, controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val sessionCommands = with(PlayerCommands) {
            MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(likeCommand).add(unlikeCommand).add(repeatCommand).add(repeatOffCommand)
                .add(repeatOneCommand).add(radioCommand).add(sleepTimer)
                .add(playCommand).add(addToQueueCommand).add(addToNextCommand)
                .add(resumeCommand).add(imageCommand)
                .build()
        }
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands).build()
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> = with(PlayerCommands) {
        val player = session.player
        when (customCommand) {
            likeCommand -> onSetRating(session, controller, ThumbRating(true))
            unlikeCommand -> onSetRating(session, controller, ThumbRating())
            repeatOffCommand -> setRepeat(player, Player.REPEAT_MODE_OFF)
            repeatOneCommand -> setRepeat(player, Player.REPEAT_MODE_ONE)
            repeatCommand -> setRepeat(player, Player.REPEAT_MODE_ALL)
            playCommand -> playItem(player, args)
            addToQueueCommand -> addToQueue(player, args)
            addToNextCommand -> addToNext(player, args)
            radioCommand -> radio(player, args)
            sleepTimer -> onSleepTimer(player, args.getLong("ms"))
            resumeCommand -> resume(player, args.getBoolean("cleared", true))
            imageCommand -> getImage(player)
            else -> super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    private fun getImage(player: Player) = CoroutineUtils.future(scope) {
        val item = withPlayer(player) { currentMediaItem }
            ?: ResumptionUtils.recoverPlaylist(context, app, downloadFlow.value, false).run { first.getOrNull(second) }
            ?: return@future SessionResult(SessionError.ERROR_UNKNOWN)
        val image = toScaledBitmap(ImageUtils.loadDrawable(MediaItemUtils.getTrack(item).cover, context) ?: return@future SessionResult(SessionError.ERROR_UNKNOWN), 720)
        SessionResult(RESULT_SUCCESS, Bundle().apply { putParcelable("image", image) })
    }

    private fun toScaledBitmap(drawable: Drawable, width: Int) = drawable.toBitmap().let { bmp ->
        val ratio = width.toFloat() / bmp.width
        val height = (bmp.height * ratio).toInt()
        bmp.scale(width, height)
    }

    private fun resume(player: Player, withClear: Boolean) = CoroutineUtils.future(scope) {
        withContext(Dispatchers.Main) {
            player.shuffleModeEnabled = ResumptionUtils.recoverShuffle(context) == true
            player.repeatMode = ResumptionUtils.recoverRepeat(context) ?: Player.REPEAT_MODE_OFF
        }
        val (items, index, pos) = ResumptionUtils.recoverPlaylist(context, app, downloadFlow.value, withClear)
        withContext(Dispatchers.Main) {
            player.setMediaItems(items, index, pos)
            player.prepare()
        }
        SessionResult(RESULT_SUCCESS)
    }

    private var timerJob: Job? = null
    private fun onSleepTimer(player: Player, ms: Long): ListenableFuture<SessionResult> {
        timerJob?.cancel()
        val time = when (ms) {
            0L -> return Futures.immediateFuture(SessionResult(RESULT_SUCCESS))
            Long.MAX_VALUE -> player.duration - player.currentPosition
            else -> ms
        }

        timerJob = scope.launch {
            delay(time)
            withPlayer(player) { pause() }
        }
        return Futures.immediateFuture(SessionResult(RESULT_SUCCESS))
    }

    private fun setRepeat(player: Player, repeat: Int) = run {
        player.repeatMode = repeat
        Futures.immediateFuture(SessionResult(RESULT_SUCCESS))
    }


    @OptIn(UnstableApi::class)
    private fun radio(player: Player, args: Bundle) = CoroutineUtils.future(scope) {
        val error = SessionResult(SessionError.ERROR_UNKNOWN)
        val origin = args.getString("origin") ?: return@future error
        val item = Serializer.getSerialized<EchoMediaItem>(args, "item")?.getOrNull() ?: return@future error
        val tracks = when (item) {
            is Track -> repository.getRadio(item.id)
            else -> emptyList()
        }
        if (tracks.isEmpty()) return@future error
        radioFlow.value = PlayerState.Radio.Loading
        val mediaItems = tracks.map { track ->
            MediaItemUtils.build(
                app, downloadFlow.value, MediaState.Unloaded("internal", track), item
            )
        }
        withPlayer(player) {
            clearMediaItems()
            shuffleModeEnabled = false
            setMediaItems(mediaItems)
            prepare()
            play()
        }
        radioFlow.value = PlayerRadio.start(repository, item) ?: PlayerState.Radio.Empty
        SessionResult(RESULT_SUCCESS)
    }

    private fun playItem(player: Player, args: Bundle) = CoroutineUtils.future(scope) {
        val error = SessionResult(SessionError.ERROR_UNKNOWN)
        val origin = args.getString("origin") ?: "internal"
        val item = Serializer.getSerialized<EchoMediaItem>(args, "item")?.getOrNull() ?: return@future error
        val shuffle = args.getBoolean("shuffle", false)
        
        when (item) {
            is Track -> {
                val mediaItem = MediaItemUtils.build(
                    app, downloadFlow.value, MediaState.Unloaded(origin, item), null
                )
                withPlayer(player) {
                    setMediaItem(mediaItem)
                    prepare()
                    seekTo(item.playedDuration ?: 0)
                    play()
                }
            }

            else -> {
                val trackList = listTracks(item)
                if (trackList.isEmpty()) return@future error
                
                val list = if (shuffle) trackList.shuffled() else trackList
                withPlayer(player) {
                    setMediaItems(list.map {
                        MediaItemUtils.build(
                            app, downloadFlow.value, MediaState.Unloaded(origin, it), item
                        )
                    })
                    shuffleModeEnabled = shuffle
                    seekTo(0, list.firstOrNull()?.playedDuration ?: 0)
                    play()
                }
            }
        }
        SessionResult(RESULT_SUCCESS)
    }

    private suspend fun listTracks(item: EchoMediaItem): List<Track> = when (item) {
        is Album -> repository.getAlbumTracks(item.id)
        is Playlist -> repository.getPlaylistTracks(item.id)
        is Artist -> repository.getArtistTracks(item.id)
        is Track -> listOf(item)
        is Radio -> repository.getRadio(item.id)
    }

    private suspend fun <T> withPlayer(player: Player, block: suspend Player.() -> T): T =
        withContext(Dispatchers.Main) { player.block() }

    private fun addToQueue(player: Player, args: Bundle) = CoroutineUtils.future(scope) {
        val error = SessionResult(SessionError.ERROR_UNKNOWN)
        val origin = args.getString("origin") ?: "internal"
        val item = Serializer.getSerialized<EchoMediaItem>(args, "item")?.getOrNull() ?: return@future error
        
        val tracks = listTracks(item)
        if (tracks.isEmpty()) return@future error
        val mediaItems = tracks.map { track ->
            MediaItemUtils.build(
                app,
                downloadFlow.value,
                MediaState.Unloaded(origin, track),
                null
            )
        }
        withPlayer(player) {
            addMediaItems(mediaItems)
            prepare()
        }
        SessionResult(RESULT_SUCCESS)
    }

    private var next = 0
    private var nextJob: Job? = null
    private fun addToNext(player: Player, args: Bundle) = CoroutineUtils.future(scope) {
        val error = SessionResult(SessionError.ERROR_UNKNOWN)
        val origin = args.getString("origin") ?: "internal"
        val item = Serializer.getSerialized<EchoMediaItem>(args, "item")?.getOrNull() ?: return@future error
        nextJob?.cancel()
        
        val tracks = listTracks(item)
        if (tracks.isEmpty()) return@future error
        val mediaItems = tracks.map { track ->
            MediaItemUtils.build(
                app,
                downloadFlow.value,
                MediaState.Unloaded(origin, track),
                null
            )
        }
        withPlayer(player) {
            if (mediaItemCount == 0) playWhenReady = true
            addMediaItems(currentMediaItemIndex + 1 + next, mediaItems)
            prepare()
        }
        next += mediaItems.size
        nextJob = scope.launch {
            delay(5000)
            next = 0
        }
        SessionResult(RESULT_SUCCESS)
    }

    override fun onSetRating(
        session: MediaSession, controller: MediaSession.ControllerInfo, rating: Rating,
    ): ListenableFuture<SessionResult> {
        return if (rating !is ThumbRating) super.onSetRating(session, controller, rating)
        else CoroutineUtils.future(scope) {
            val item = withPlayer(session.player) { currentMediaItem }
                ?: return@future SessionResult(SessionError.ERROR_UNKNOWN)
            val track = MediaItemUtils.getTrack(item)

            val liked = rating.isThumbsUp
            if (liked != repository.isLiked(track)) {
                repository.toggleLike(track)
            }

            val newItem = item.run {
                buildUpon().setMediaMetadata(
                    mediaMetadata.buildUpon().setUserRating(ThumbRating(liked)).build()
                )
            }.build()
            withPlayer(session.player) {
                replaceMediaItem(currentMediaItemIndex, newItem)
            }
            SessionResult(RESULT_SUCCESS, bundleOf("liked" to liked))
        }
    }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
    ) = CoroutineUtils.future(scope) {
        withContext(Dispatchers.Main) {
            mediaSession.player.shuffleModeEnabled = ResumptionUtils.recoverShuffle(context) ?: false
            mediaSession.player.repeatMode = ResumptionUtils.recoverRepeat(context) ?: Player.REPEAT_MODE_OFF
        }
        val (items, index, pos) = ResumptionUtils.recoverPlaylist(context, app, downloadFlow.value)
        MediaItemsWithStartPosition(items, index, pos)
    }

    companion object {
        fun toTracks(pagedData: PagedData<Shelf>) = pagedData.map {
            it.getOrThrow().mapNotNull { shelf ->
                when (shelf) {
                    is Shelf.Category -> null
                    is Shelf.Item -> listOfNotNull(shelf.media as? Track)
                    is Shelf.Lists.Categories -> null
                    is Shelf.Lists.Items -> shelf.list.filterIsInstance<Track>()
                    is Shelf.Lists.Tracks -> shelf.list
                }
            }.flatten()
        }
    }
}