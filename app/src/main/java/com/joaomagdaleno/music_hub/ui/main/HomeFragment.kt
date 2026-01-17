package com.joaomagdaleno.music_hub.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.joaomagdaleno.music_hub.common.models.Feed
import com.joaomagdaleno.music_hub.ui.common.UiViewModel
import com.joaomagdaleno.music_hub.ui.compose.screens.HomeFeed
import com.joaomagdaleno.music_hub.ui.compose.theme.MusicHubTheme
import com.joaomagdaleno.music_hub.ui.feed.FeedClickListener
import com.joaomagdaleno.music_hub.ui.feed.FeedData
import com.joaomagdaleno.music_hub.ui.feed.FeedViewModel
import com.joaomagdaleno.music_hub.utils.ContextUtils
import kotlinx.coroutines.flow.combine
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class HomeFragment : Fragment() {

    private val feedData by lazy {
        val vm by viewModel<FeedViewModel>()
        val id = "home"
        vm.getFeedData(id, Feed.Buttons.EMPTY, cached = { null }) { repo ->
            val feed = Feed.toFeedFromList(repo.getHomeFeed())
            FeedData.State("internal", null, feed)
        }
    }

    private val listener by lazy { FeedClickListener.getFeedListener(requireParentFragment()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MusicHubTheme {
                    HomeFeed(
                        onItemClick = { item ->
                            listener.onMediaClicked(null, "home", item, null)
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val uiViewModel by activityViewModel<UiViewModel>()
        ContextUtils.observe(
            this,
            uiViewModel.navigation.combine(feedData.backgroundImageFlow) { a, b -> a to b }
        ) { (curr, bg) ->
            if (curr != 0) return@observe
            uiViewModel.currentNavBackground.value = bg
        }
        
        // Listen for permission granted to refresh feed
        requireActivity().supportFragmentManager.setFragmentResultListener("permissionGranted", this) { _, _ ->
            feedData.refresh()
        }
    }
}