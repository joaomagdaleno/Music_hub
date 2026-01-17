package com.joaomagdaleno.music_hub.playback

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.net.Uri
import androidx.annotation.CallSuper
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.MediaState
import com.joaomagdaleno.music_hub.common.helpers.PagedData
import com.joaomagdaleno.music_hub.common.models.*
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.download.Downloader
import com.joaomagdaleno.music_hub.utils.CacheUtils
import com.joaomagdaleno.music_hub.utils.CoroutineUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import java.util.WeakHashMap

@UnstableApi
abstract class AndroidAutoCallback(
    open val app: App,
    open val scope: CoroutineScope,
    open val sourceList: StateFlow<List<Any>>,
    open val downloadFlow: StateFlow<List<Downloader.Info>>
) : MediaLibrarySession.Callback {

    val context get() = app.context

    @CallSuper
    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        return Futures.immediateFuture(
            LibraryResult.ofItem(browsableItem(ROOT, "", browsable = false), null)
        )
    }

    @OptIn(UnstableApi::class)
    @CallSuper
    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = CoroutineUtils.future(scope) {
        LibraryResult.ofItemList(listOf(), null)
    }

    @OptIn(UnstableApi::class)
    @CallSuper
    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return CoroutineUtils.future(scope) {
            LibraryResult.ofItemList(listOf(), null)
        }
    }

    @CallSuper
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ) = CoroutineUtils.future(scope) {
        val new = mediaItems.mapNotNull {
            if (it.mediaId.startsWith("auto/")) {
                val id = it.mediaId.substringAfter("auto/")
                val triple = CacheUtils.getFromCache<Triple<Track, String, EchoMediaItem?>>(context, id, "auto")
                         ?: return@mapNotNull null
                val (track, origin, con) = triple
                MediaItemUtils.build(
                    app,
                    downloadFlow.value,
                    MediaState.Unloaded(origin, track),
                    con
                )
            } else it
        }
        val future = super.onSetMediaItems(
            mediaSession, controller, new, startIndex, startPositionMs
        )
        CoroutineUtils.await(future, context)
    }

    companion object {
        private const val ROOT = "root"
        private const val LIBRARY = "library"
        private const val HOME = "home"
        private const val SEARCH = "search"
        private const val FEED = "feed"
        private const val SHELF = "shelf"
        private const val LIST = "list"

        private const val ARTIST = "artist"
        private const val USER = "user"
        private const val ALBUM = "album"
        private const val PLAYLIST = "playlist"
        private const val RADIO = "radio"

        private fun getResourcesUri(resources: Resources, int: Int): Uri {
            val scheme = ContentResolver.SCHEME_ANDROID_RESOURCE
            val pkg = resources.getResourcePackageName(int)
            val type = resources.getResourceTypeName(int)
            val name = resources.getResourceEntryName(int)
            val uri = "$scheme://$pkg/$type/$name"
            return uri.toUri()
        }

        private fun imageToUri(context: Context, image: ImageHolder) = when (image) {
            is ImageHolder.ResourceUriImageHolder -> image.uri.toUri()
            is ImageHolder.NetworkRequestImageHolder -> image.request.url.toUri()
            is ImageHolder.ResourceIdImageHolder -> getResourcesUri(context.resources, image.resId)
            is ImageHolder.HexColorImageHolder -> "".toUri()
        }

        fun browsableItem(
            id: String,
            title: String,
            subtitle: String? = null,
            browsable: Boolean = true,
            artWorkUri: Uri? = null,
            type: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
        ) = MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsPlayable(false)
                    .setIsBrowsable(browsable)
                    .setMediaType(type)
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setArtworkUri(artWorkUri)
                    .build()
            )
            .build()

        private fun trackToMediaItem(
            context: Context, track: Track, origin: String, con: EchoMediaItem? = null
        ): MediaItem {
            CacheUtils.saveToCache(context, track.id, Triple(track, origin, con), "auto")
            return MediaItem.Builder()
                .setMediaId("auto/${track.id}")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsPlayable(true)
                        .setIsBrowsable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .setTitle(track.title)
                        .setArtist(track.subtitleWithE)
                        .setAlbumTitle(track.album?.title)
                        .setArtworkUri(track.cover?.let { imageToUri(context, it) })
                        .build()
                ).build()
        }


        fun anyToMediaItem(context: Context, any: Any): MediaItem {
            // Placeholder for now
            return browsableItem("placeholder", "Placeholder")
        }

        @OptIn(UnstableApi::class)
        val notSupported =
            LibraryResult.ofError<ImmutableList<MediaItem>>(SessionError.ERROR_NOT_SUPPORTED)

        @OptIn(UnstableApi::class)
        val errorIo = LibraryResult.ofError<ImmutableList<MediaItem>>(SessionError.ERROR_IO)

        private val itemMap = WeakHashMap<String, EchoMediaItem>()
        fun echoMediaItemToMediaItem(
            context: Context, item: EchoMediaItem, origin: String
        ): MediaItem = when (item) {
            is Track -> trackToMediaItem(context, item, origin)
            else -> {
                val id = item.hashCode().toString()
                itemMap[id] = item
                val (page, type) = when (item) {
                    is Artist, is Radio -> USER to MediaMetadata.MEDIA_TYPE_MIXED
                    is Album -> ALBUM to MediaMetadata.MEDIA_TYPE_ALBUM
                    is Playlist -> PLAYLIST to MediaMetadata.MEDIA_TYPE_PLAYLIST
                    else -> throw IllegalStateException("Invalid type")
                }
                browsableItem(
                    "$ROOT/$origin/$page/$id",
                    item.title,
                    item.subtitleWithE,
                    true,
                    item.cover?.let { imageToUri(context, it) },
                    type
                )
            }
        }

        private val listsMap = WeakHashMap<String, Shelf.Lists<*>>()
        private val feedMap = WeakHashMap<String, Feed<Shelf>>()
        fun getListsItems(
            context: Context, id: String, origin: String
        ) = run {
            val shelf = listsMap[id]!!
            when (shelf) {
                is Shelf.Lists.Categories -> shelf.list.map { shelfToMediaItem(context, it, origin) }
                is Shelf.Lists.Items -> shelf.list.map { echoMediaItemToMediaItem(context, it, origin) }
                is Shelf.Lists.Tracks -> shelf.list.map { trackToMediaItem(context, it, origin) }
            } + listOfNotNull(
                if (shelf.more != null) {
                    val moreId = shelf.id
                    feedMap[moreId] = shelf.more!!
                    browsableItem(
                        "$ROOT/$origin/$FEED/$moreId",
                        context.getString(R.string.more)
                    )
                } else null
            )
        }

        fun shelfToMediaItem(
            context: Context, shelf: Shelf, origin: String
        ): MediaItem = when (shelf) {
            is Shelf.Category -> {
                val items = shelf.feed
                if (items != null) feedMap[shelf.id] = items
                browsableItem("$ROOT/$origin/$FEED/${shelf.id}", shelf.title, shelf.subtitle, items != null)
            }

            is Shelf.Item -> echoMediaItemToMediaItem(context, shelf.media, origin)
            is Shelf.Lists<*> -> {
                val id = "${shelf.id.hashCode()}"
                listsMap[id] = shelf
                browsableItem("$ROOT/$origin/$LIST/$id", shelf.title, shelf.subtitle)
            }
        }


        // THIS PROBABLY BREAKS GOING BACK TBH, NEED TO TEST
        private val shelvesMap = WeakHashMap<String, PagedData<Shelf>>()
        private val continuations = WeakHashMap<Pair<String, Int>, String?>()
        suspend fun getShelfItems(
            context: Context, id: String, origin: String, page: Int
        ): List<MediaItem> {
            val shelf = shelvesMap[id]!!
            val (list, next) = shelf.loadPage(continuations[id to page])
            continuations[id to page + 1] = next
            return list.map { shelfToMediaItem(context, it, origin) }
        }

        suspend fun feedToMediaItems(
            feed: Feed<Shelf>, id: String, context: Context, origin: String, page: Int
        ): List<MediaItem> {
            val feedId = "${id.hashCode()}"
            feedMap[feedId] = feed
            //TODO
            return listOf()
        }

        private val tracksMap = WeakHashMap<String, Pair<EchoMediaItem, PagedData<Track>>>()
        suspend fun getTracks(
            context: Context,
            id: String,
            page: Int,
            getTracks: suspend () -> Pair<EchoMediaItem, Feed<Track>?>
        ): List<MediaItem> {
            val (item, tracks) = tracksMap.getOrPut(id) {
                val result = getTracks()
                result.first to (result.second?.run { getPagedData(tabs.firstOrNull()) }?.pagedData ?: PagedData.empty())
            }
            val (list, next) = tracks.loadPage(continuations[id to page])
            continuations[id to page + 1] = next
            return list.map { trackToMediaItem(context, it, id, item) }
        }
    }
}