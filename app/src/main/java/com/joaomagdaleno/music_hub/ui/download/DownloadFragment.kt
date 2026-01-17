package com.joaomagdaleno.music_hub.ui.download

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.paging.LoadState
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.Feed
import com.joaomagdaleno.music_hub.common.models.Shelf
import com.joaomagdaleno.music_hub.databinding.FragmentDownloadBinding
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import com.joaomagdaleno.music_hub.utils.ui.ExceptionData
import com.joaomagdaleno.music_hub.common.helpers.PagedData
import com.joaomagdaleno.music_hub.ui.common.GridAdapter
import com.joaomagdaleno.music_hub.ui.download.DownloadsAdapter
import com.joaomagdaleno.music_hub.ui.feed.FeedAdapter
import com.joaomagdaleno.music_hub.ui.feed.FeedClickListener
import com.joaomagdaleno.music_hub.ui.feed.FeedData
import com.joaomagdaleno.music_hub.ui.feed.FeedViewModel
import com.joaomagdaleno.music_hub.ui.media.LineAdapter
import com.joaomagdaleno.music_hub.utils.ContextUtils
import com.joaomagdaleno.music_hub.utils.ui.AnimationUtils
import com.joaomagdaleno.music_hub.utils.ui.FastScrollerHelper
import org.koin.androidx.viewmodel.ext.android.viewModel

class DownloadFragment : Fragment(R.layout.fragment_download) {

    private val vm by viewModel<DownloadViewModel>()
    private val downloadsAdapter by lazy {
        DownloadsAdapter(object : DownloadsAdapter.Listener {
            override fun onCancel(trackId: Long) = vm.cancel(trackId)
            override fun onRestart(trackId: Long) = vm.restart(trackId)
            override fun onExceptionClicked(data: ExceptionData) = 
                UiUtils.openException(requireActivity(), data, null)
        })
    }

    private val feedViewModel by viewModel<FeedViewModel>()
    private val feedData by lazy {
        val flow = vm.downloader.downloadFeed
        feedViewModel.getFeedData(
            "downloads", Feed.Buttons(), false, flow
        ) {
            val list = flow.value
            val shelf = Shelf.Lists.Items("downloads_list", getString(R.string.downloads), list)
            val feed = Feed<Shelf>(tabs = emptyList()) {
                Feed.Data(PagedData.Single { listOf(shelf) })
            }
            FeedData.State("internal", null, feed)
        }
    }

    private val feedListener by lazy { FeedClickListener.getFeedListener(this) }
    private val feedAdapter by lazy {
        FeedAdapter.getFeedAdapter(this, feedData, feedListener)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentDownloadBinding.bind(view)
        AnimationUtils.setupTransition(this, view)
        UiUtils.applyBackPressCallback(this)
        UiUtils.configureAppBar(binding.appBarLayout) { offset ->
            binding.toolbarOutline.alpha = offset
            binding.iconContainer.alpha = 1 - offset
        }
        binding.toolBar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        UiUtils.applyInsets(this) { insets ->
            UiUtils.applyContentInsets(binding.recyclerView, insets, 20, 8, 72)
            UiUtils.applyFabInsets(binding.fabContainer, insets, systemInsets.value)
        }
        FastScrollerHelper.applyTo(binding.recyclerView)
        val lineAdapter = LineAdapter()
        binding.fabCancel.setOnClickListener {
            vm.cancelAll()
        }
        binding.recyclerView.itemAnimator = null
        FeedAdapter.getTouchHelper(feedListener).attachToRecyclerView(binding.recyclerView)
        GridAdapter.configureGridLayout(
            binding.recyclerView,
            GridAdapter.Concat(
                downloadsAdapter,
                lineAdapter,
                feedAdapter.withLoading(this)
            )
        )
        ContextUtils.observe(this, vm.flow) { infos ->
            binding.fabCancel.isVisible = infos.any { it.download.finalFile == null }
            lineAdapter.loadState = if (infos.isNotEmpty()) LoadState.Loading
            else LoadState.NotLoading(false)
            downloadsAdapter.submitList(DownloadsAdapter.toItems(infos))
        }
    }
}