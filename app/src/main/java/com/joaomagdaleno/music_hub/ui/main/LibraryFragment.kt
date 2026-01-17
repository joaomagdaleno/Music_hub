package com.joaomagdaleno.music_hub.ui.main

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.transition.MaterialSharedAxis
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.Message
import com.joaomagdaleno.music_hub.common.models.Playlist
import com.joaomagdaleno.music_hub.common.models.Feed
import com.joaomagdaleno.music_hub.databinding.FragmentLibraryBinding
import com.joaomagdaleno.music_hub.ui.common.GridAdapter
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import com.joaomagdaleno.music_hub.ui.common.UiViewModel
import com.joaomagdaleno.music_hub.ui.feed.FeedAdapter
import com.joaomagdaleno.music_hub.ui.feed.FeedClickListener
import com.joaomagdaleno.music_hub.ui.feed.FeedData
import com.joaomagdaleno.music_hub.ui.feed.FeedViewModel
import com.joaomagdaleno.music_hub.ui.playlist.create.CreatePlaylistBottomSheet
import com.joaomagdaleno.music_hub.ui.settings.SettingsBottomSheet
import com.joaomagdaleno.music_hub.utils.ContextUtils
import com.joaomagdaleno.music_hub.utils.Serializer
import com.joaomagdaleno.music_hub.utils.ui.AnimationUtils
import kotlinx.coroutines.flow.combine
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class LibraryFragment : Fragment(R.layout.fragment_library) {
    private val feedData by lazy {
        val vm by viewModel<FeedViewModel>()
        val id = "library"
        vm.getFeedData(id, cached = { null }) { repo ->
            val feed = Feed.toFeedFromList(repo.getLibraryFeed())
            FeedData.State("internal", null, feed)
        }
    }

    private val listener by lazy { FeedClickListener.getFeedListener(requireParentFragment()) }
    private val feedAdapter by lazy { FeedAdapter.getFeedAdapter(this, feedData, listener) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentLibraryBinding.bind(view)
        AnimationUtils.setupTransition(this, view, false, MaterialSharedAxis.Y)
        val headerAdapter = HeaderAdapter(this)
        val uiViewModel by activityViewModel<UiViewModel>()
        val context = requireContext()
        
        ContextUtils.observe(this, uiViewModel.navigationReselected) {
            if (it != 2) return@observe
            SettingsBottomSheet().show(parentFragmentManager, null)
        }
        ContextUtils.observe(
            this,
            uiViewModel.navigation.combine(feedData.backgroundImageFlow) { a, b -> a to b }
        ) { (curr, bg) ->
            if (curr != 2) return@observe
            uiViewModel.currentNavBackground.value = bg
        }
        
        MainFragment.applyInsets(this, binding.recyclerView, binding.appBarOutline, 72) {
            UiUtils.applyInsets(binding.createPlaylistContainer, it)
            UiUtils.configureSwipeRefresh(binding.swipeRefresh, it)
        }
        UiUtils.applyBackPressCallback(this)
        FeedAdapter.getTouchHelper(listener).attachToRecyclerView(binding.recyclerView)
        GridAdapter.configureGridLayout(
            binding.recyclerView,
            feedAdapter.withLoading(this, headerAdapter)
        )
        binding.swipeRefresh.run {
            setOnRefreshListener { feedData.refresh() }
            ContextUtils.observe(this@LibraryFragment, feedData.isRefreshingFlow) {
                isRefreshing = it
            }
        }

        ContextUtils.observe(this, feedData.current) {
            binding.createPlaylist.isVisible = true
        }
        val parent = requireParentFragment()
        binding.createPlaylist.setOnClickListener {
            CreatePlaylistBottomSheet().show(parent.parentFragmentManager, null)
        }

        parent.parentFragmentManager.setFragmentResultListener("createPlaylist", this) { _, data ->
            val origin = data.getString("origin")
            val playlistResult = Serializer.getSerialized<Playlist>(data, "playlist")
            val playlist = playlistResult?.getOrNull()
            if (origin != null && playlist != null) UiUtils.createSnack(
                this,
                Message(
                    getString(R.string.x_created, playlist.title),
                    Message.Action(getString(R.string.view)) {
                        listener.onMediaClicked(null, origin, playlist, null)
                    }
                )
            )
            feedData.refresh()
        }

        parent.parentFragmentManager.setFragmentResultListener("deleted", this) { _, _ ->
            feedData.refresh()
        }

        parent.parentFragmentManager.setFragmentResultListener("reloadLibrary", this) { _, _ ->
            feedData.refresh()
        }
    }
}