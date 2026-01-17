package com.joaomagdaleno.music_hub.ui.media

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.paging.LoadState
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.Playlist
import com.joaomagdaleno.music_hub.databinding.FragmentMediaDetailsBinding
import com.joaomagdaleno.music_hub.ui.common.GridAdapter
import com.joaomagdaleno.music_hub.ui.common.UiViewModel
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import com.joaomagdaleno.music_hub.ui.feed.FeedAdapter
import com.joaomagdaleno.music_hub.ui.feed.FeedClickListener
import com.joaomagdaleno.music_hub.ui.feed.FeedViewModel
import com.joaomagdaleno.music_hub.utils.ContextUtils
import com.joaomagdaleno.music_hub.utils.ui.FastScrollerHelper
import kotlinx.coroutines.flow.combine
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class MediaDetailsFragment : Fragment(R.layout.fragment_media_details) {

    interface Parent {
        val feedId: String
        val viewModel: MediaDetailsViewModel
        val fromPlayer: Boolean
    }

    val parent by lazy { requireParentFragment() as Parent }
    val viewModel by lazy { parent.viewModel }
    private val feedViewModel by lazy {
        requireParentFragment().viewModel<FeedViewModel>().value
    }

    private val trackFeedData by lazy {
        feedViewModel.getFeedData(
            "${parent.feedId}_tracks",
            com.joaomagdaleno.music_hub.common.models.Feed.Buttons(showPlayAndShuffle = true),
            true,
            cached = { viewModel.trackSourceCachedFlow.value },
            loader = { viewModel.tracksLoadedFlow.value as? com.joaomagdaleno.music_hub.ui.feed.FeedData.State<com.joaomagdaleno.music_hub.common.models.Feed<com.joaomagdaleno.music_hub.common.models.Shelf>> }
        )
    }

    private val feedData by lazy {
        feedViewModel.getFeedData(
            "${parent.feedId}_feed",
            com.joaomagdaleno.music_hub.common.models.Feed.Buttons(),
            false,
            cached = { viewModel.feedSourceCachedFlow.value },
            loader = { viewModel.feedSourceLoadedFlow.value }
        )
    }

    private val mediaHeaderAdapter by lazy {
        MediaHeaderAdapter(
            MediaHeaderAdapter.getMediaHeaderListener(requireParentFragment(), viewModel),
            parent.fromPlayer
        )
    }

    private val feedListener by lazy {
        if (!parent.fromPlayer) FeedClickListener.getFeedListener(requireParentFragment())
        else FeedClickListener(
            requireParentFragment(),
            requireActivity().supportFragmentManager,
            R.id.navHostFragment
        ) {
            val uiViewModel by activityViewModel<UiViewModel>()
            uiViewModel.collapsePlayer()
        }
    }

    private val trackAdapter by lazy {
        FeedAdapter.getFeedAdapter(this, trackFeedData, feedListener, true)
    }
    private val feedAdapter by lazy {
        FeedAdapter.getFeedAdapter(this, feedData, feedListener)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentMediaDetailsBinding.bind(view)
        FastScrollerHelper.applyTo(binding.recyclerView)
        UiUtils.applyInsets(this) {
            val item = viewModel.uiResultFlow.value?.getOrNull()?.item as? Playlist
            val bottom = if (item?.isEditable == true) 72 else 16
            UiUtils.applyContentInsets(binding.recyclerView, it, 20, 8, bottom)
        }
        val lineAdapter = LineAdapter()
        ContextUtils.observe(this, trackFeedData.shouldShowEmpty) {
            lineAdapter.loadState = if (it) LoadState.Loading else LoadState.NotLoading(false)
        }
        ContextUtils.observe(this, viewModel.uiResultFlow) { result ->
            mediaHeaderAdapter.result = result
        }
        FeedAdapter.getTouchHelper(feedListener).attachToRecyclerView(binding.recyclerView)
        GridAdapter.configureGridLayout(
            binding.recyclerView,
            GridAdapter.Concat(
                mediaHeaderAdapter,
                trackAdapter.withLoading(this),
                lineAdapter,
                feedAdapter.withLoading(this)
            )
        )
        val loadingFlow = viewModel.isRefreshingFlow
            .combine(trackFeedData.isRefreshingFlow) { a, b -> a || b }
                .combine(feedData.isRefreshingFlow) { a, b -> a || b }
        binding.swipeRefresh.run {
            UiUtils.configureSwipeRefresh(this)
            setOnRefreshListener { viewModel.refresh() }
            ContextUtils.observe(this@MediaDetailsFragment, loadingFlow) {
                isRefreshing = it
            }
        }
    }
}