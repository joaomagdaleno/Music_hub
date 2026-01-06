package com.joaomagdaleno.music_hub.ui.main

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.transition.MaterialSharedAxis
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.clients.LibraryFeedClient
import com.joaomagdaleno.music_hub.common.clients.PlaylistEditClient
import com.joaomagdaleno.music_hub.common.models.Feed
import com.joaomagdaleno.music_hub.common.models.Message
import com.joaomagdaleno.music_hub.common.models.Playlist
import com.joaomagdaleno.music_hub.common.models.Shelf
import com.joaomagdaleno.music_hub.databinding.FragmentLibraryBinding
import com.joaomagdaleno.music_hub.extensions.ExtensionUtils.getAs
import com.joaomagdaleno.music_hub.extensions.ExtensionUtils.isClient
import com.joaomagdaleno.music_hub.extensions.cache.Cached
import com.joaomagdaleno.music_hub.ui.common.GridAdapter.Companion.configureGridLayout
import com.joaomagdaleno.music_hub.ui.common.SnackBarHandler.Companion.createSnack
import com.joaomagdaleno.music_hub.ui.common.UiViewModel
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.applyBackPressCallback
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.applyInsets
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.configure
import com.joaomagdaleno.music_hub.ui.feed.FeedAdapter.Companion.getFeedAdapter
import com.joaomagdaleno.music_hub.ui.feed.FeedAdapter.Companion.getTouchHelper
import com.joaomagdaleno.music_hub.ui.feed.FeedClickListener.Companion.getFeedListener
import com.joaomagdaleno.music_hub.ui.feed.FeedData
import com.joaomagdaleno.music_hub.ui.feed.FeedViewModel
import com.joaomagdaleno.music_hub.ui.main.MainFragment.Companion.applyInsets
import com.joaomagdaleno.music_hub.ui.playlist.create.CreatePlaylistBottomSheet
import com.joaomagdaleno.music_hub.ui.settings.SettingsBottomSheet
import com.joaomagdaleno.music_hub.utils.ContextUtils.observe
import com.joaomagdaleno.music_hub.utils.Serializer.getSerialized
import com.joaomagdaleno.music_hub.utils.ui.AnimationUtils.setupTransition
import kotlinx.coroutines.flow.combine
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class LibraryFragment : Fragment(R.layout.fragment_library) {
    private val feedData by lazy {
        val vm by viewModel<FeedViewModel>()
        val id = "library"
        vm.getFeedData(id, cached = {
            val curr = current.value!!
            val feed = Cached.getFeedShelf(app, curr.id, id).getOrThrow()
            FeedData.State(curr.id, null, feed)
        }) {
            val curr = current.value!!
            val feed = Cached.savingFeed(
                app, curr, id,
                curr.getAs<LibraryFeedClient, Feed<Shelf>> { loadLibraryFeed() }.getOrThrow()
            )
            FeedData.State(curr.id, null, feed)
        }
    }

    private val listener by lazy { getFeedListener(requireParentFragment()) }
    private val feedAdapter by lazy { getFeedAdapter(feedData, listener) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentLibraryBinding.bind(view)
        setupTransition(view, false, MaterialSharedAxis.Y)
        val headerAdapter = HeaderAdapter(this)
        val uiViewModel by activityViewModel<UiViewModel>()
        observe(uiViewModel.navigationReselected) {
            if (it != 2) return@observe
            SettingsBottomSheet().show(parentFragmentManager, null)
        }
        observe(
            uiViewModel.navigation.combine(feedData.backgroundImageFlow) { a, b -> a to b }
        ) { (curr, bg) ->
            if (curr != 2) return@observe
            uiViewModel.currentNavBackground.value = bg
        }
        applyInsets(binding.recyclerView, binding.appBarOutline, 72) {
            binding.createPlaylistContainer.applyInsets(it)
            binding.swipeRefresh.configure(it)
        }
        applyBackPressCallback()
        getTouchHelper(listener).attachToRecyclerView(binding.recyclerView)
        configureGridLayout(
            binding.recyclerView,
            feedAdapter.withLoading(this, headerAdapter)
        )
        binding.swipeRefresh.run {
            setOnRefreshListener { feedData.refresh() }
            observe(feedData.isRefreshingFlow) {
                isRefreshing = it
            }
        }

        observe(feedData.current) {
            binding.createPlaylist.isVisible = it?.isClient<PlaylistEditClient>() ?: false
        }
        val parent = requireParentFragment()
        binding.createPlaylist.setOnClickListener {
            CreatePlaylistBottomSheet().show(parent.parentFragmentManager, null)
        }

        parent.parentFragmentManager.setFragmentResultListener("createPlaylist", this) { _, data ->
            val extensionId = data.getString("extensionId")
            val playlist = data.getSerialized<Playlist>("playlist")?.getOrNull()
            if (extensionId != null && playlist != null) createSnack(
                Message(
                    getString(R.string.x_created, playlist.title),
                    Message.Action(getString(R.string.view)) {
                        listener.onMediaClicked(null, extensionId, playlist, null)
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