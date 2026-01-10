package com.joaomagdaleno.music_hub.ui.media

import android.content.Context
import android.content.Intent
import androidx.core.app.ShareCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.*
import com.joaomagdaleno.music_hub.common.models.Feed.Companion.toFeed
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.download.Downloader
import com.joaomagdaleno.music_hub.common.models.MediaState
import com.joaomagdaleno.music_hub.ui.feed.FeedData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
abstract class MediaDetailsViewModel(
    downloader: Downloader,
    private val app: App,
    private val loadFeeds: Boolean,
    private val repository: com.joaomagdaleno.music_hub.data.repository.MusicRepository,
) : ViewModel() {
    val downloadsFlow = downloader.flow

    val refreshFlow = MutableSharedFlow<Unit>()
    val cacheResultFlow = MutableStateFlow<Result<MediaState.Loaded<*>>?>(null)
    val itemResultFlow = MutableStateFlow<Result<MediaState.Loaded<*>>?>(null)

    val uiResultFlow = itemResultFlow.combine(cacheResultFlow) { item, cache ->
        item ?: cache
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val tracksLoadedFlow = itemResultFlow.transformLatest { result ->
        emit(null)
        if (!loadFeeds) return@transformLatest
        val item = result?.getOrNull()?.item ?: return@transformLatest
        
        val tracks = when (item) {
            is Album -> repository.getAlbumTracks(item.id)
            is Playlist -> repository.getPlaylistTracks(item.id)
            is Artist -> repository.getArtistTracks(item.id)
            is Track -> repository.getRadio(item.id)
            else -> emptyList()
        }

        if (tracks.isEmpty()) return@transformLatest

        val shelf = Shelf.Lists.Tracks("internal", "", tracks)
        val feed = listOf(shelf).toFeed<Shelf>()
        emit(FeedData.State("internal", item, feed))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val feedSourceLoadedFlow = itemResultFlow.transformLatest { result ->
        emit(null)
        if (!loadFeeds) return@transformLatest
        val item = result?.getOrNull()?.item ?: return@transformLatest
        
        // Load "Feed" - usually albums for artists, etc.
        val feed: Feed<Shelf>? = when (item) {
             is Artist -> {
                 val albums = repository.getArtistAlbums(item.id)
                 if (albums.isNotEmpty()) {
                     listOf(Shelf.Lists.Items("artist_albums", "Albums", albums, type = Shelf.Lists.Type.Grid)).toFeed()
                 } else null
             }
             else -> null
        }
        
        if (feed != null) {
             emit(FeedData.State("internal", item, feed))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Stubs for caching flows
    val cacheSourceItemFlow = MutableStateFlow(null)
    val trackSourceCachedFlow = MutableStateFlow<FeedData.State<Feed<Shelf>>?>(null)
    val feedSourceCachedFlow = MutableStateFlow<FeedData.State<Feed<Shelf>>?>(null)

    fun refresh() = viewModelScope.launch {
        refreshFlow.emit(Unit)
    }

    abstract fun getItem(): Triple<String, EchoMediaItem, Boolean>?

    fun likeItem(liked: Boolean) = viewModelScope.launch {
        val item = itemResultFlow.value?.getOrNull()?.item as? Track ?: return@launch
        repository.toggleLike(item)
        refresh()
    }

    fun hideItem(hidden: Boolean) {}

    fun followItem(followed: Boolean) = viewModelScope.launch {
        // Implement artist follow if DB support exists
    }

    fun saveToLibrary(saved: Boolean) = viewModelScope.launch {
        val item = itemResultFlow.value?.getOrNull()?.item ?: return@launch
        repository.toggleSave(item)
        refresh()
    }

    fun onShare() = viewModelScope.launch(Dispatchers.IO) {
        val item = itemResultFlow.value?.getOrNull()?.item ?: return@launch
        share(app, item)
    }

    val isRefreshing get() = itemResultFlow.value == null
    val isRefreshingFlow = itemResultFlow.map { isRefreshing }

    companion object {
        suspend fun notFound(app: App, id: Int) {
            val notFound = app.context.run { getString(R.string.no_x_found, getString(id)) }
            app.messageFlow.emit(Message(notFound))
        }

        suspend fun createMessage(app: App, message: Context.() -> String) {
            app.messageFlow.emit(Message(app.context.message()))
        }

        suspend fun like(app: App, item: EchoMediaItem, like: Boolean) {
            createMessage(app) {
                getString(if (like) R.string.liking_x else R.string.unliking_x, item.title)
            }
            // TODO: Implement internal like
        }

        suspend fun hide(app: App, item: EchoMediaItem, hide: Boolean) {
            createMessage(app) {
                getString(if (hide) R.string.hiding_x else R.string.unhiding_x, item.title)
            }
        }

        suspend fun follow(app: App, item: EchoMediaItem, follow: Boolean) {
            createMessage(app) {
                getString(if (follow) R.string.following_x else R.string.unfollowing_x, item.title)
            }
        }

        suspend fun save(app: App, item: EchoMediaItem, save: Boolean) {
            createMessage(app) {
                getString(if (save) R.string.saving_x else R.string.removing_x, item.title)
            }
        }

        suspend fun share(app: App, item: EchoMediaItem) {
            createMessage(app) { getString(R.string.sharing_x, item.title) }
            val url = item.title // Placeholder
            val intent = ShareCompat.IntentBuilder(app.context)
                .setType("text/plain")
                .setChooserTitle("Internal - ${item.title}")
                .setText(url)
                .createChooserIntent()
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.context.startActivity(intent)
        }
    }
}