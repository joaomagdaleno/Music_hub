package com.joaomagdaleno.music_hub.playback.source

import android.net.Uri
import androidx.media3.common.MediaItem
import com.joaomagdaleno.music_hub.common.models.Streamable
import com.joaomagdaleno.music_hub.common.models.NetworkRequest
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.download.Downloader
import com.joaomagdaleno.music_hub.common.models.MediaState
import com.joaomagdaleno.music_hub.playback.MediaItemUtils
// Imports removed
import com.joaomagdaleno.music_hub.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

class StreamableLoader(
    private val app: App,
    private val repository: com.joaomagdaleno.music_hub.data.repository.MusicRepository,
    private val downloadFlow: StateFlow<List<Downloader.Info>>
) {
    suspend fun load(mediaItem: MediaItem) = withContext(Dispatchers.IO) {
        FileLogger.log("StreamableLoader", "load() called for mediaItem.id=${mediaItem.mediaId}")
        // No need to wait for sources flow
        val new = if (MediaItemUtils.isLoaded(mediaItem)) {
            FileLogger.log("StreamableLoader", "mediaItem already loaded")
            mediaItem
        } else {
            FileLogger.log("StreamableLoader", "mediaItem not loaded, calling loadTrack()")
            MediaItemUtils.buildLoaded(
                app, downloadFlow.value, mediaItem, loadTrack(mediaItem)
            )
        }

        val server = async { loadServer(new) }
        val background = async { null } // Backgrounds not supported yet
        val subtitle = async { null } // Subtitles not supported yet

        val serverResult = server.await()
        FileLogger.log("StreamableLoader", "load() complete. Server success=${serverResult.isSuccess}")
        if (serverResult.isFailure) {
            FileLogger.log("StreamableLoader", "Server load failure: ${serverResult.exceptionOrNull()?.message}", serverResult.exceptionOrNull())
        }
        
        MediaItemUtils.buildWithBackgroundAndSubtitle(
            new, background.await(), subtitle.await()
        ) to serverResult
    }

    private suspend fun loadTrack(item: MediaItem): MediaState.Loaded<Track> {
        FileLogger.log("StreamableLoader", "loadTrack() called for item.state=${MediaItemUtils.getState(item)::class.simpleName}")
        return when (val state = MediaItemUtils.getState(item)) {
             is MediaState.Loaded<*> -> {
                 FileLogger.log("StreamableLoader", "loadTrack() - already MediaState.Loaded")
                 state as MediaState.Loaded<Track>
             }
             is MediaState.Unloaded -> {
                 FileLogger.log("StreamableLoader", "loadTrack() - MediaState.Unloaded, fetching track id=${state.item.id}")
                 val track = repository.getTrack(state.item.id)
                 if (track == null) {
                     FileLogger.log("StreamableLoader", "loadTrack() - Track not found for id=${state.item.id}")
                     throw Exception("Track not found")
                 }
                 FileLogger.log("StreamableLoader", "loadTrack() - Track loaded: ${track.title}")
                 MediaState.Loaded(
                     origin = state.origin,
                     item = track,
                     isFollowed = null,
                     followers = null,
                     isSaved = null,
                     isLiked = null,
                     isHidden = null,
                     showRadio = true,
                     showShare = true
                 )
             }
        }
    }

    private suspend fun loadServer(mediaItem: MediaItem): Result<Streamable.Media.Server> {
        FileLogger.log("StreamableLoader", "loadServer() called for track=${MediaItemUtils.getTrack(mediaItem).title}")
        val downloaded = MediaItemUtils.getDownloaded(mediaItem)
        val servers = MediaItemUtils.getTrack(mediaItem).servers
        val index = MediaItemUtils.getServerIndex(mediaItem)
        FileLogger.log("StreamableLoader", "loadServer() - downloaded=${downloaded?.size ?: 0}, servers=${servers.size}, index=$index")
        
        if (!downloaded.isNullOrEmpty() && servers.size == index) {
            FileLogger.log("StreamableLoader", "loadServer() - using downloaded files")
            return runCatching {
                Streamable.Media.Server(
                    downloaded.map { Streamable.Stream.toStream(Uri.fromFile(File(it)).toString()) },
                    true
                )
            }
        }
        
        FileLogger.log("StreamableLoader", "loadServer() - fetching stream URL from repository")
        return runCatching {
             // Use Repository to get stream URL
             val url = repository.getStreamUrl(MediaItemUtils.getTrack(mediaItem))
             FileLogger.log("StreamableLoader", "loadServer() - got stream URL: $url")
             // Create server object
             val stream = Streamable.Stream.Http(
                 request = NetworkRequest(url),
                 quality = 0,
                 format = Streamable.StreamFormat.Progressive
             )
             Streamable.Media.Server(listOf(stream), false)
        }.onFailure { ex ->
            FileLogger.log("StreamableLoader", "loadServer() failed: ${ex.message}", ex)
        }
    }
}