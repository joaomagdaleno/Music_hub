package com.joaomagdaleno.music_hub.ui.download

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.paging.LoadState
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.Feed
import com.joaomagdaleno.music_hub.databinding.FragmentDownloadBinding
import com.joaomagdaleno.music_hub.extensions.builtin.unified.UnifiedExtension
import com.joaomagdaleno.music_hub.extensions.builtin.unified.UnifiedExtension.Companion.getFeed
import com.joaomagdaleno.music_hub.ui.common.ExceptionFragment
import com.joaomagdaleno.music_hub.ui.common.ExceptionUtils
import com.joaomagdaleno.music_hub.ui.common.FragmentUtils.openFragment
import com.joaomagdaleno.music_hub.ui.common.GridAdapter
import com.joaomagdaleno.music_hub.ui.common.GridAdapter.Companion.configureGridLayout
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.applyBackPressCallback
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.applyContentInsets
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.applyFabInsets
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.applyInsets
import com.joaomagdaleno.music_hub.ui.download.DownloadsAdapter.Companion.toItems
import com.joaomagdaleno.music_hub.ui.feed.FeedAdapter.Companion.getFeedAdapter
import com.joaomagdaleno.music_hub.ui.feed.FeedAdapter.Companion.getTouchHelper
import com.joaomagdaleno.music_hub.ui.feed.FeedClickListener.Companion.getFeedListener
import com.joaomagdaleno.music_hub.ui.feed.FeedData
import com.joaomagdaleno.music_hub.ui.feed.FeedViewModel
import com.joaomagdaleno.music_hub.ui.media.LineAdapter
import com.joaomagdaleno.music_hub.utils.ContextUtils.observe
import com.joaomagdaleno.music_hub.utils.ui.AnimationUtils.setupTransition
import com.joaomagdaleno.music_hub.utils.ui.FastScrollerHelper
import com.joaomagdaleno.music_hub.utils.ui.UiUtils.configureAppBar
import org.koin.androidx.viewmodel.ext.android.viewModel

class DownloadFragment : Fragment(R.layout.fragment_download) {

    private val vm by viewModel<DownloadViewModel>()
    private val downloadsAdapter by lazy {
        DownloadsAdapter(object : DownloadsAdapter.Listener {
            override fun onCancel(trackId: Long) = vm.cancel(trackId)
            override fun onRestart(trackId: Long) = vm.restart(trackId)
            override fun onExceptionClicked(data: ExceptionUtils.Data) = requireActivity()
                .openFragment<ExceptionFragment>(null, ExceptionFragment.getBundle(data))
        })
    }

    private val feedViewModel by viewModel<FeedViewModel>()
    private val feedData by lazy {
        val flow = vm.downloader.unified.downloadFeed
        feedViewModel.getFeedData(
            "downloads", Feed.Buttons(), false, flow
        ) {
            val feed = requireContext().getFeed(flow.value)
            FeedData.State(UnifiedExtension.metadata.id, null, feed)
        }
    }

    private val feedListener by lazy { getFeedListener() }
    private val feedAdapter by lazy {
        getFeedAdapter(feedData, feedListener)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentDownloadBinding.bind(view)
        setupTransition(view)
        applyBackPressCallback()
        binding.appBarLayout.configureAppBar { offset ->
            binding.toolbarOutline.alpha = offset
            binding.iconContainer.alpha = 1 - offset
        }
        binding.toolBar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        applyInsets {
            binding.recyclerView.applyContentInsets(it, 20, 8, 72)
            binding.fabContainer.applyFabInsets(it, systemInsets.value)
        }
        FastScrollerHelper.applyTo(binding.recyclerView)
        val lineAdapter = LineAdapter()
        binding.fabCancel.setOnClickListener {
            vm.cancelAll()
        }
        binding.recyclerView.itemAnimator = null
        getTouchHelper(feedListener).attachToRecyclerView(binding.recyclerView)
        configureGridLayout(
            binding.recyclerView,
            GridAdapter.Concat(
                downloadsAdapter,
                lineAdapter,
                feedAdapter.withLoading(this)
            )
        )
        observe(vm.flow) { infos ->
            binding.fabCancel.isVisible = infos.any { it.download.finalFile == null }
            lineAdapter.loadState = if (infos.isNotEmpty()) LoadState.Loading
            else LoadState.NotLoading(false)
            downloadsAdapter.submitList(infos.toItems(vm.extensions.music.value))
        }
    }
}