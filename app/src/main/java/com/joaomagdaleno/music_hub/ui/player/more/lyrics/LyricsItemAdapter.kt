package com.joaomagdaleno.music_hub.ui.player.more.lyrics

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.paging.LoadState
import androidx.recyclerview.widget.DiffUtil
import com.joaomagdaleno.music_hub.common.models.Lyrics
import com.joaomagdaleno.music_hub.common.models.Tab
import com.joaomagdaleno.music_hub.databinding.ItemLoadingSmallBinding
import com.joaomagdaleno.music_hub.databinding.ItemShelfCategoryBinding
import com.joaomagdaleno.music_hub.ui.common.GridAdapter
import com.joaomagdaleno.music_hub.ui.feed.EmptyAdapter
import com.joaomagdaleno.music_hub.ui.feed.FeedAdapter.LoadingViewHolder
import com.joaomagdaleno.music_hub.ui.feed.FeedLoadingAdapter
import com.joaomagdaleno.music_hub.ui.feed.FeedLoadingAdapter.Companion.createListener
import com.joaomagdaleno.music_hub.ui.feed.TabsAdapter
import com.joaomagdaleno.music_hub.utils.ContextUtils.observe
import com.joaomagdaleno.music_hub.utils.ui.scrolling.ScrollAnimPagingAdapter
import com.joaomagdaleno.music_hub.utils.ui.scrolling.ScrollAnimViewHolder
import kotlinx.coroutines.flow.combine

class LyricsItemAdapter(
    private val listener: Listener
) : ScrollAnimPagingAdapter<Lyrics, LyricsItemAdapter.ViewHolder>(DiffCallback), GridAdapter {

    object DiffCallback : DiffUtil.ItemCallback<Lyrics>() {
        override fun areItemsTheSame(oldItem: Lyrics, newItem: Lyrics) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Lyrics, newItem: Lyrics) = oldItem == newItem
    }

    fun interface Listener {
        fun onLyricsSelected(lyrics: Lyrics)
    }

    class ViewHolder(val binding: ItemShelfCategoryBinding) : ScrollAnimViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemShelfCategoryBinding.inflate(inflater, parent, false)
        binding.icon.isVisible = false
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val lyrics = getItem(position) ?: return
        holder.binding.run {
            root.setOnClickListener { listener.onLyricsSelected(lyrics) }
            title.text = lyrics.title
            subtitle.text = lyrics.subtitle
            subtitle.isVisible = !lyrics.subtitle.isNullOrBlank()
        }
    }

    data class Loading(
        val parent: ViewGroup,
        val binding: ItemLoadingSmallBinding = ItemLoadingSmallBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
    ) : FeedLoadingAdapter.ViewHolder(binding.root)

    fun withLoaders(fragment: Fragment, viewModel: LyricsViewModel): GridAdapter {
        val tabs = TabsAdapter<Tab>({ title }) { view, index, tab ->
            viewModel.selectedTabIndexFlow.value = index
        }
        observe(fragment, viewModel.tabsFlow) { tabs.data = it }
        observe(fragment, viewModel.selectedTabIndexFlow) { tabs.selected = it }
        val loadStateListener = createListener(fragment) { retry() }
        val header = FeedLoadingAdapter(loadStateListener) { LoadingViewHolder(it) }
        val footer = FeedLoadingAdapter(loadStateListener) { LoadingViewHolder(it) }
        val empty = EmptyAdapter()
        observe(
            fragment,
            loadStateFlow.combine(viewModel.shouldShowEmpty) { a, b -> a to b }
        ) { pair ->
            val loadStates = pair.first
            val shouldShowEmpty = pair.second
            val isEmpty =
                shouldShowEmpty && itemCount == 0 && loadStates.append is LoadState.NotLoading
            empty.loadState = if (isEmpty) LoadState.Loading else LoadState.NotLoading(false)
        }
        addLoadStateListener { loadStates ->
            header.loadState = loadStates.refresh
            footer.loadState = loadStates.append
        }
        return GridAdapter.Concat(tabs, header, empty, this, footer)
    }

    override val adapter = this
    override fun getSpanSize(position: Int, width: Int, count: Int) = count

}