package com.joaomagdaleno.music_hub.ui.feed

import android.os.Parcelable
import androidx.paging.cachedIn
import com.joaomagdaleno.music_hub.common.helpers.PagedData
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.Feed
import com.joaomagdaleno.music_hub.common.models.Shelf
import com.joaomagdaleno.music_hub.common.models.Tab
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.data.repository.MusicRepository
import com.joaomagdaleno.music_hub.ui.common.PagedSource
import com.joaomagdaleno.music_hub.ui.feed.viewholders.HorizontalListViewHolder
import com.joaomagdaleno.music_hub.utils.CacheUtils
import com.joaomagdaleno.music_hub.utils.CoroutineUtils
import com.joaomagdaleno.music_hub.utils.image.ImageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import com.joaomagdaleno.music_hub.utils.FileLogger

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
data class FeedData(
    private val feedId: String,
    private val scope: CoroutineScope,
    private val app: App,
    private val repository: MusicRepository,
    private val cached: suspend (MusicRepository) -> State<Feed<Shelf>>?,
    private val load: suspend (MusicRepository) -> State<Feed<Shelf>>?,
    private val defaultButtons: Feed.Buttons,
    private val noVideos: Boolean,
    private val extraLoadFlow: Flow<*>
) {
    val current = MutableStateFlow<String?>("internal")
    val usersFlow = emptyFlow<Unit>()
    fun getSource(id: String) = Unit

    val layoutManagerStates = hashMapOf<Int, Parcelable?>()
    val visibleScrollableViews = hashMapOf<Int, WeakReference<HorizontalListViewHolder>>()

    private val refreshFlow = MutableSharedFlow<Unit>(1)
    private val cachedState = MutableStateFlow<Result<State<Feed<Shelf>>?>?>(null)
    private val loadedState = MutableStateFlow<Result<State<Feed<Shelf>>?>?>(null)
    private val selectedTabFlow = MutableStateFlow<Tab?>(null)

    val loadedShelves = MutableStateFlow<List<Shelf>?>(null)
    var searchToggled: Boolean = false
    var searchQuery: String? = null
    val feedSortState = MutableStateFlow<FeedSort.State?>(null)
    val searchClickedFlow = MutableSharedFlow<Unit>()

    private val stateFlow = cachedState.combine(loadedState) { a, b -> a to b }
        .stateIn(scope, SharingStarted.Lazily, null to null)

    private val cachedDataFlow = combine(cachedState, selectedTabFlow) { feed, tab -> feed to tab }
        .transformLatest { (feed, tab) ->
            emit(null)
            if (feed == null) return@transformLatest
            emit(getData(feed, tab))
        }.stateIn(scope, SharingStarted.Lazily, null)

    private val loadedDataFlow = combine(loadedState, selectedTabFlow) { feed, tab -> feed to tab }
        .transformLatest { (feed, tab) ->
            emit(null)
            if (feed == null) return@transformLatest
            emit(getData(feed, tab))
        }.stateIn(scope, SharingStarted.Lazily, null)

    private suspend fun getData(
        state: Result<State<Feed<Shelf>>?>, tab: Tab?
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val (origin, item, feed) = state.getOrThrow() ?: return@runCatching null
            State(origin, item, feed.getPagedData(tab))
        }
    }

    val dataFlow = cachedDataFlow.combine(loadedDataFlow) { cached, loaded ->
        val origin = (loaded?.getOrNull() ?: cached?.getOrNull())?.origin
        val tabId = selectedTabFlow.value?.id
        searchQuery = null
        searchToggled = false
        val id = "$origin-$feedId-$tabId"
        feedSortState.value = origin?.let { CacheUtils.getFromCache<FeedSort.State>(app.context, id, "sort") }
        loadedShelves.value = null
        cached to loaded
    }

    val shouldShowEmpty = dataFlow.map { (cached, loaded) ->
        val data = loaded?.getOrNull() ?: cached?.getOrNull()
        data != null
    }.stateIn(scope, SharingStarted.Lazily, false)

    val tabsFlow = stateFlow.map { (cached, loaded) ->
        val state = (loaded?.getOrNull() ?: cached?.getOrNull()) ?: return@map listOf()
        state.feed.tabs.map {
            FeedTab(feedId, state.origin, it)
        }
    }

    val selectedTabIndexFlow = tabsFlow.combine(selectedTabFlow) { tabs, tab ->
        tabs.indexOfFirst { it.tab.id == tab?.id }
    }

    data class FeedTab(
        val feedId: String,
        val origin: String,
        val tab: Tab
    )

    data class Buttons(
        val feedId: String,
        val origin: String,
        val buttons: Feed.Buttons,
        val item: EchoMediaItem? = null,
        val sortState: FeedSort.State? = null,
    )

    val buttonsFlow = dataFlow.combine(feedSortState) { data, state ->
        val feed = data.run { second?.getOrNull() ?: first?.getOrNull() } ?: return@combine null
        Buttons(
            feedId,
            feed.origin,
            feed.feed.buttons ?: defaultButtons,
            feed.item,
            state,
        )
    }

    private val imageFlow = dataFlow.map { (cached, loaded) ->
        (loaded?.getOrNull() ?: cached?.getOrNull())?.feed?.background
    }.stateIn(scope, SharingStarted.Lazily, null)

    val backgroundImageFlow = imageFlow.mapLatest { image ->
        if (image != null) ImageUtils.loadDrawable(image, app.context) else null
    }.flowOn(Dispatchers.IO).stateIn(scope, SharingStarted.Lazily, null)

    val cachedFeedTypeFlow =
        combine(cachedDataFlow, feedSortState, searchClickedFlow) { _, _, _ -> Unit }
            .transformLatest {
                emit(null)
                val cached = cachedDataFlow.value ?: return@transformLatest
                emit(getFeedSourceData(cached))
            }.stateIn(scope, SharingStarted.Lazily, null)

    val loadedFeedTypeFlow =
        combine(loadedDataFlow, feedSortState, searchClickedFlow) { _, _, _ -> Unit }
            .transformLatest {
                emit(null)
                val loaded = loadedDataFlow.value ?: return@transformLatest
                emit(getFeedSourceData(loaded))
            }.stateIn(scope, SharingStarted.Lazily, null)

    val pagingFlow =
        combine(cachedFeedTypeFlow, loadedFeedTypeFlow) { cached, loaded -> cached to loaded }
            .transformLatest { (cached, loaded) ->
                emitAll(PagedSource(loaded, cached).flow)
            }.cachedIn(scope)

    private suspend fun getFeedSourceData(
        result: Result<State<Feed.Data<Shelf>>?>
    ): Result<PagedData<FeedType>> = withContext(Dispatchers.IO) {
        val tabId = selectedTabFlow.value?.id
        val data = if (feedSortState.value != null || searchQuery != null) {
            result.mapCatching { state ->
                state ?: return@mapCatching PagedData.empty()
                val origin = state.origin
                val data = state.feed.pagedData

                val sortState = feedSortState.value
                val query = searchQuery
                var shelves = loadTill(data, 2000)
                shelves = if (sortState?.feedSort != null || query != null)
                    shelves.flatMap { shelf ->
                        when (shelf) {
                            is Shelf.Category -> listOf(shelf)
                            is Shelf.Item -> listOf(shelf)
                            is Shelf.Lists.Categories -> shelf.list
                            is Shelf.Lists.Items -> shelf.list.map { it.toShelf() }
                            is Shelf.Lists.Tracks -> shelf.list.map { it.toShelf() }
                        }
                    }
                else shelves
                loadedShelves.value = shelves
                if (sortState != null) {
                    shelves = sortState.feedSort?.sorter?.invoke(app.context, shelves) ?: shelves
                    if (sortState.reversed) shelves = shelves.reversed()
                    if (sortState.save)
                        CacheUtils.saveToCache(app.context, "$origin-$feedId-$tabId", sortState, "sort")
                }
                if (query != null) {
                    shelves = shelves.filter { it.title.contains(query, true) }
                }
                PagedData.Single {
                    FeedType.toFeedType(
                        shelves,
                        feedId,
                        origin,
                        state.item,
                        tabId,
                        noVideos
                    )
                }
            }
        } else result.mapCatching { state ->
            state ?: return@mapCatching PagedData.empty()
            val origin = state.origin
            val data = state.feed.pagedData
            data.loadPage(null)
            var start = 0L
            data.map { res ->
                res.map {
                    val list = FeedType.toFeedType(it, feedId, origin, state.item, tabId, noVideos, start)
                    start += list.size
                    list
                }.getOrThrow()
            }
        }
        data
    }

    private suspend fun <T : Any> loadTill(pagedData: PagedData<T>, limit: Long): List<T> {
        val list = mutableListOf<T>()
        var page = pagedData.loadPage(null)
        list.addAll(page.data)
        while (page.continuation != null && list.size < limit) {
            page = pagedData.loadPage(page.continuation)
            list.addAll(page.data)
        }
        return list
    }

    val isRefreshingFlow = loadedFeedTypeFlow.map {
        loadedFeedTypeFlow.value == null
    }.stateIn(scope, SharingStarted.Lazily, true)

    fun selectTab(origin: String?, pos: Int) {
        val state = stateFlow.value.run { second?.getOrNull() ?: first?.getOrNull() }
        val tab = state?.feed?.tabs?.getOrNull(pos)
            ?.takeIf { state.origin == origin }
        CacheUtils.saveToCache(app.context, feedId, tab?.id, "selected_tab")
        selectedTabFlow.value = tab
    }

    fun refresh() = scope.launch { 
        FileLogger.log("FeedData", "refresh() called for feedId=$feedId")
        refreshFlow.emit(Unit) 
    }

    init {
        FileLogger.log("FeedData", "init start for feedId=$feedId")
        scope.launch(Dispatchers.IO) {
            listOfNotNull(current, refreshFlow, usersFlow, extraLoadFlow)
                .merge().debounce(100L).collectLatest {
                    FileLogger.log("FeedData", "collectLatest triggered for feedId=$feedId")
                    cachedState.value = null
                    loadedState.value = null
                    FileLogger.log("FeedData", "Loading cached for feedId=$feedId")
                    cachedState.value = runCatching { cached(repository) }
                    FileLogger.log("FeedData", "Cached result: ${cachedState.value?.isSuccess} for feedId=$feedId")
                    FileLogger.log("FeedData", "Loading fresh for feedId=$feedId")
                    loadedState.value = runCatching { load(repository) }
                    FileLogger.log("FeedData", "Loaded result: ${loadedState.value?.isSuccess} for feedId=$feedId")
                    loadedState.value?.exceptionOrNull()?.let { ex ->
                        FileLogger.log("FeedData", "Load exception for feedId=$feedId: ${ex.message}", ex)
                    }
                }
        }

        scope.launch {
            stateFlow.collect { res ->
                val feed = res.run { second?.getOrNull() ?: first?.getOrNull() }?.feed?.tabs
                selectedTabFlow.value = if (feed == null) null else {
                    val last = CacheUtils.getFromCache<String>(app.context, feedId, "selected_tab")
                    feed.find { it.id == last } ?: feed.firstOrNull()
                }
            }
        }
    }

    data class State<T>(
        val origin: String,
        val item: EchoMediaItem?,
        val feed: T,
    )

    fun onSearchClicked() = scope.launch { searchClickedFlow.emit(Unit) }
}