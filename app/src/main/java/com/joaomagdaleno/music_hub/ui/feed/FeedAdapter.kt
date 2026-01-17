package com.joaomagdaleno.music_hub.ui.feed

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.paging.LoadState
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.databinding.ItemLoadingBinding
import com.joaomagdaleno.music_hub.playback.PlayerState
import com.joaomagdaleno.music_hub.ui.common.GridAdapter
import com.joaomagdaleno.music_hub.ui.feed.FeedData.FeedTab
import com.joaomagdaleno.music_hub.ui.feed.FeedType.Enum.Category
import com.joaomagdaleno.music_hub.ui.feed.FeedType.Enum.CategoryGrid
import com.joaomagdaleno.music_hub.ui.feed.FeedType.Enum.Header
import com.joaomagdaleno.music_hub.ui.feed.FeedType.Enum.HorizontalList
import com.joaomagdaleno.music_hub.ui.feed.FeedType.Enum.Media
import com.joaomagdaleno.music_hub.ui.feed.FeedType.Enum.MediaGrid
import com.joaomagdaleno.music_hub.ui.feed.viewholders.CategoryViewHolder
import com.joaomagdaleno.music_hub.ui.feed.viewholders.FeedViewHolder
import com.joaomagdaleno.music_hub.ui.feed.viewholders.HeaderViewHolder
import com.joaomagdaleno.music_hub.ui.feed.viewholders.HorizontalListViewHolder
import com.joaomagdaleno.music_hub.ui.feed.viewholders.MediaGridViewHolder
import com.joaomagdaleno.music_hub.ui.feed.viewholders.MediaViewHolder
import com.joaomagdaleno.music_hub.ui.player.PlayerViewModel
import com.joaomagdaleno.music_hub.utils.ContextUtils
import com.joaomagdaleno.music_hub.utils.ui.AnimationUtils
import com.joaomagdaleno.music_hub.utils.ui.scrolling.ScrollAnimPagingAdapter
import kotlinx.coroutines.flow.combine
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import java.lang.ref.WeakReference

class FeedAdapter(
    private val viewModel: FeedData,
    private val listener: FeedClickListener,
    private val takeFullScreen: Boolean = false,
) : ScrollAnimPagingAdapter<FeedType, FeedViewHolder<*>>(DiffCallback), GridAdapter {

    object DiffCallback : DiffUtil.ItemCallback<FeedType>() {
        override fun areContentsTheSame(oldItem: FeedType, newItem: FeedType) = oldItem == newItem
        override fun areItemsTheSame(oldItem: FeedType, newItem: FeedType): Boolean {
            if (oldItem.origin != newItem.origin) return false
            if (newItem.type != oldItem.type) return false
            if (oldItem.id != newItem.id) return false
            return true
        }
    }

    private val viewPool = RecyclerView.RecycledViewPool()
    override fun getItemViewType(position: Int) =
        runCatching { getItem(position)!! }.getOrNull()?.type?.ordinal ?: 0

    private var isPlayButtonShown = false
    private fun toTrack(feed: FeedType): Track? = when (feed) {
        is FeedType.Media -> feed.item as? Track
        is FeedType.MediaGrid -> feed.item as? Track
        else -> null
    }

    fun getAllTracks(feed: FeedType): Pair<List<Track>, Int> {
        if (!isPlayButtonShown) return listOfNotNull(toTrack(feed)) to 0
        val list = snapshot().mapNotNull { it }
        val index = list.indexOfFirst { it.id == feed.id }
        if (index == -1) return listOf<Track>() to -1
        val from = list.take(index).indexOfLast { it.type != feed.type }
        val to = list.drop(index + 1).indexOfFirst { it.type != feed.type }
        val feeds = list.subList(from + 1, if (to == -1) list.size else index + to + 1)
        val tracks = feeds.mapNotNull { toTrack(it) }
        val newIndex = tracks.indexOfFirst { it.id == feed.id }
        return tracks to newIndex
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder<*> {
        val type = FeedType.Enum.entries[viewType]
        return when (type) {
            Header -> HeaderViewHolder(parent, listener)
            HorizontalList -> HorizontalListViewHolder(parent, listener, viewPool)
            Category -> CategoryViewHolder(parent, listener)
            CategoryGrid -> CategoryViewHolder(parent, listener)
            Media -> MediaViewHolder(parent, listener, ::getAllTracks)
            MediaGrid -> MediaGridViewHolder(parent, listener, ::getAllTracks)
        }
    }

    override fun onBindViewHolder(holder: FeedViewHolder<*>, position: Int) {
        super.onBindViewHolder(holder, position)
        val feed = runCatching { getItem(position) }.getOrNull() ?: return
        when (holder) {
            is HeaderViewHolder -> holder.bind(feed as FeedType.Header)
            is CategoryViewHolder -> holder.bind(feed as FeedType.Category)
            is MediaViewHolder -> holder.bind(feed as FeedType.Media)
            is MediaGridViewHolder -> holder.bind(feed as FeedType.MediaGrid)
            is HorizontalListViewHolder -> {
                holder.bind(feed as FeedType.HorizontalList)
                viewModel.visibleScrollableViews[position] = WeakReference(holder)
                holder.layoutManager.apply {
                    val state = viewModel.layoutManagerStates[position]
                    if (state != null) onRestoreInstanceState(state)
                    else scrollToPosition(0)
                }
            }
        }
        holder.onCurrentChanged(current)
    }

    override fun onViewRecycled(holder: FeedViewHolder<*>) {
        if (holder is HorizontalListViewHolder) saveScrollState(holder) {
            viewModel.visibleScrollableViews.remove(holder.bindingAdapterPosition)
        }
    }

    override fun onViewAttachedToWindow(holder: FeedViewHolder<*>) {
        holder.onCurrentChanged(current)
    }

    class LoadingViewHolder(
        parent: ViewGroup,
        val binding: ItemLoadingBinding = ItemLoadingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        ),
    ) : FeedLoadingAdapter.ViewHolder(binding.root) {
        init {
            binding.textView.isVisible = false
        }

        override fun bind(loadState: LoadState) {
            binding.root.alpha = 0f
            AnimationUtils.animatedWithAlpha(binding.root, 500)
        }
    }

    fun getAllTracks() = snapshot().mapNotNull {
        when (it) {
            is FeedType.Media -> listOfNotNull(it.item as? Track)
            is FeedType.MediaGrid -> listOfNotNull(it.item as? Track)
            is FeedType.HorizontalList -> it.shelf.list.filterIsInstance<Track>()
            else -> null
        }
    }.flatten()

    fun withLoading(fragment: Fragment, vararg before: GridAdapter): GridAdapter.Concat {
        val tabs = TabsAdapter<FeedTab>({ tab.title }) { view, index, tab ->
            listener.onTabSelected(view, tab.feedId, tab.origin, index)
        }
        ContextUtils.observe(fragment, viewModel.tabsFlow) { tabs.data = it }
        ContextUtils.observe(fragment, viewModel.selectedTabIndexFlow) { tabs.selected = it }
        val buttons = ButtonsAdapter(viewModel, listener, ::getAllTracks)
        ContextUtils.observe(fragment, viewModel.buttonsFlow) {
            buttons.buttons = it
            isPlayButtonShown = it?.buttons?.showPlayAndShuffle == true
        }
        val loadStateListener = FeedLoadingAdapter.createListener(fragment) { retry() }
        val header = FeedLoadingAdapter(loadStateListener) { LoadingViewHolder(it) }
        val footer = FeedLoadingAdapter(loadStateListener) { LoadingViewHolder(it) }
        val empty = EmptyAdapter()
        ContextUtils.observe(
            fragment,
            loadStateFlow.combine(viewModel.shouldShowEmpty) { a, b -> a to b }
        ) { (loadStates, shouldShowEmpty) ->
            val isEmpty =
                shouldShowEmpty && itemCount == 0 && loadStates.append is LoadState.NotLoading
            empty.loadState = if (isEmpty) LoadState.Loading else LoadState.NotLoading(false)
        }
        addLoadStateListener { loadStates ->
            header.loadState = loadStates.refresh
            footer.loadState = loadStates.append
        }
        return GridAdapter.Concat(*before, tabs, buttons, header, empty, this, footer)
    }

    override val adapter = this
    override fun getSpanSize(position: Int, width: Int, count: Int) =
        when (FeedType.Enum.entries[getItemViewType(position)]) {
            Header, HorizontalList -> count
            Category, Media -> if (takeFullScreen) count else 2.coerceAtMost(count)
            CategoryGrid, MediaGrid -> 1
        }

    private fun clearState() {
        viewModel.layoutManagerStates.clear()
        viewModel.visibleScrollableViews.clear()
    }

    private fun saveScrollState(
        holder: HorizontalListViewHolder, block: ((HorizontalListViewHolder) -> Unit)? = null,
    ) = runCatching {
        val layoutManagerStates = viewModel.layoutManagerStates
        layoutManagerStates[holder.bindingAdapterPosition] =
            holder.layoutManager.onSaveInstanceState()
        block?.invoke(holder)
    }

    fun saveState() {
        viewModel.visibleScrollableViews.values.forEach { item ->
            item.get()?.let { saveScrollState(it) }
        }
        viewModel.visibleScrollableViews.clear()
    }

    init {
        addLoadStateListener {
            if (it.refresh == LoadState.Loading) clearState()
        }
    }

    private var current: PlayerState.Current? = null
    fun onCurrentChanged(current: PlayerState.Current?) {
        this.current = current
        onEachViewHolder { onCurrentChanged(current) }
    }

    companion object {
        fun getFeedAdapter(
            fragment: Fragment,
            viewModel: FeedData,
            listener: FeedClickListener,
            takeFullScreen: Boolean = false,
        ): FeedAdapter {
            val playerViewModel by fragment.activityViewModel<PlayerViewModel>()
            val adapter = FeedAdapter(viewModel, listener, takeFullScreen)
            ContextUtils.observe(fragment, viewModel.pagingFlow) {
                adapter.saveState()
                adapter.submitData(it)
            }
            ContextUtils.observe(fragment, playerViewModel.playerState.current) { adapter.onCurrentChanged(it) }
            return adapter
        }

        fun getTouchHelper(listener: FeedClickListener) = ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.START) {
                override fun getMovementFlags(
                    recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                ): Int {
                    if (viewHolder !is MediaViewHolder) return 0
                    if (viewHolder.feed?.item !is Track) return 0
                    return makeMovementFlags(0, ItemTouchHelper.START)
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val feed = (viewHolder as MediaViewHolder).feed ?: return
                    val track = feed.item as? Track ?: return
                    listener.onTrackSwiped(viewHolder.itemView, feed.origin, track)
                    viewHolder.bindingAdapter?.notifyItemChanged(viewHolder.bindingAdapterPosition)
                }

                override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 0.25f
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ) = false
            }
        )
    }
}