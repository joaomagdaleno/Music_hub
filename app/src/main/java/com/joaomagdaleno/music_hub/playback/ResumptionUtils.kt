package com.joaomagdaleno.music_hub.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.download.Downloader
import com.joaomagdaleno.music_hub.common.models.MediaState
import com.joaomagdaleno.music_hub.playback.MediaItemUtils
import com.joaomagdaleno.music_hub.utils.CacheUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ResumptionUtils {

    private const val CLEARED = "cleared"
    private const val FOLDER = "queue"
    private const val TRACKS = "queue_tracks"
    private const val CONTEXTS = "queue_contexts"
    private const val ORIGINS = "queue_origins"
    private const val INDEX = "queue_index"
    private const val POSITION = "position"
    private const val SHUFFLE = "shuffle"
    private const val REPEAT = "repeat"

    private fun getMediaItems(player: Player) = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
    
    fun saveIndex(context: Context, index: Int) {
        CacheUtils.saveToCache(context, INDEX, index, FOLDER)
    }

    suspend fun saveQueue(context: Context, player: Player) = withContext(Dispatchers.Main) {
        val list = getMediaItems(player)
        CacheUtils.saveToCache(context, CLEARED, list.isEmpty())
        if (list.isEmpty()) return@withContext
        val currentIndex = player.currentMediaItemIndex
        withContext(Dispatchers.IO) {
            val origins = list.map { MediaItemUtils.getOrigin(it) }
            val tracks = list.map { MediaItemUtils.getTrack(it) }
            val contexts = list.map { MediaItemUtils.getContext(it) }
            CacheUtils.saveToCache(context, INDEX, currentIndex, FOLDER)
            CacheUtils.saveToCache(context, ORIGINS, origins, FOLDER)
            CacheUtils.saveToCache(context, TRACKS, tracks, FOLDER)
            CacheUtils.saveToCache(context, CONTEXTS, contexts, FOLDER)
        }
    }

    fun saveCurrentPos(context: Context, position: Long) {
        CacheUtils.saveToCache(context, POSITION, position, FOLDER)
    }

    fun recoverTracks(context: Context, withClear: Boolean = false): List<Pair<MediaState.Unloaded<Track>, EchoMediaItem?>>? {
        if (withClear && CacheUtils.getFromCache<Boolean>(context, CLEARED) != false) return null
        val tracks = CacheUtils.getFromCache<List<Track>>(context, TRACKS, FOLDER)
        val origins = CacheUtils.getFromCache<List<String>>(context, ORIGINS, FOLDER)
        val contexts = CacheUtils.getFromCache<List<EchoMediaItem>>(context, CONTEXTS, FOLDER)
        return tracks?.mapIndexedNotNull { index, track ->
            val origin = origins?.getOrNull(index) ?: return@mapIndexedNotNull null
            val item = contexts?.getOrNull(index)
            MediaState.Unloaded(origin, track) to item
        }
    }

    private fun recoverQueue(
        context: Context,
        app: App,
        downloads: List<Downloader.Info>,
        withClear: Boolean = false
    ): List<MediaItem>? {
        val tracks = recoverTracks(context, withClear) ?: return null
        return tracks.map { (state, item) ->
            MediaItemUtils.build(app, downloads, state, item)
        }
    }

    fun recoverIndex(context: Context) = CacheUtils.getFromCache<Int>(context, INDEX, FOLDER)
    fun recoverPosition(context: Context) = CacheUtils.getFromCache<Long>(context, POSITION, FOLDER)

    fun recoverShuffle(context: Context) = CacheUtils.getFromCache<Boolean>(context, SHUFFLE, FOLDER)
    fun saveShuffle(context: Context, shuffle: Boolean) {
        CacheUtils.saveToCache(context, SHUFFLE, shuffle, FOLDER)
    }

    fun recoverRepeat(context: Context) = CacheUtils.getFromCache<Int>(context, REPEAT, FOLDER)
    fun saveRepeat(context: Context, repeat: Int) {
        CacheUtils.saveToCache(context, REPEAT, repeat, FOLDER)
    }

    fun recoverPlaylist(
        context: Context,
        app: App,
        downloads: List<Downloader.Info>,
        withClear: Boolean = false
    ): Triple<List<MediaItem>, Int, Long> {
        val items = recoverQueue(context, app, downloads, withClear) ?: emptyList()
        val index = recoverIndex(context) ?: C.INDEX_UNSET
        val position = recoverPosition(context) ?: -1L
        return Triple(items, index, position)
    }
}