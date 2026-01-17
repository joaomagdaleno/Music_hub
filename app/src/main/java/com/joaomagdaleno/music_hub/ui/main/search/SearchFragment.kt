package com.joaomagdaleno.music_hub.ui.main.search

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.transition.MaterialSharedAxis
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.Feed
import com.joaomagdaleno.music_hub.common.models.QuickSearchItem
import com.joaomagdaleno.music_hub.common.models.Shelf
import com.joaomagdaleno.music_hub.databinding.FragmentSearchBinding
import com.joaomagdaleno.music_hub.ui.common.GridAdapter
import com.joaomagdaleno.music_hub.ui.common.UiViewModel
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import com.joaomagdaleno.music_hub.ui.feed.FeedAdapter
import com.joaomagdaleno.music_hub.ui.feed.FeedClickListener
import com.joaomagdaleno.music_hub.ui.feed.FeedData
import com.joaomagdaleno.music_hub.ui.feed.FeedViewModel
import com.joaomagdaleno.music_hub.ui.main.HeaderAdapter
import com.joaomagdaleno.music_hub.ui.main.MainFragment
import com.joaomagdaleno.music_hub.utils.ContextUtils
import com.joaomagdaleno.music_hub.utils.ui.AnimationUtils
import kotlinx.coroutines.flow.combine
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.android.ext.android.inject

class SearchFragment : Fragment(R.layout.fragment_search) {

    private val argId by lazy { arguments?.getString("origin") }
    private val searchViewModel by viewModel<SearchViewModel>()
    private val repository: com.joaomagdaleno.music_hub.data.repository.MusicRepository by inject()

    private var origin = ""

    private val feedData by lazy {
        val vm by viewModel<FeedViewModel>()
        val id = "search"
        vm.getFeedData(
            id,
            Feed.Buttons.EMPTY,
            false,
            searchViewModel.queryFlow,
            cached = { null }
        ) { repo ->
            val query = searchViewModel.queryFlow.value
            if (query.isBlank()) {
                val feed = Feed.toFeedFromList<Shelf>(emptyList())
                FeedData.State("internal", null, feed)
            } else {
                searchViewModel.saveQuery(query)
                val results = repo.search(query)
                val tracks = if(results.isEmpty()) {
                     listOfNotNull(repo.getTrack(query))
                } else results
                
                val shelf = Shelf.Lists.Tracks("search_results", getString(R.string.search), tracks)
                val feed = Feed.toFeedFromList<Shelf>(listOf(shelf))
                FeedData.State("internal", null, feed)
            }
        }
    }

    private val listener by lazy {
        FeedClickListener.getFeedListener(if (argId == null) requireParentFragment() else this)
    }

    private val feedAdapter by lazy {
        FeedAdapter.getFeedAdapter(this, feedData, listener)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentSearchBinding.bind(view)
        AnimationUtils.setupTransition(this, view, applyBackground = false, axis = MaterialSharedAxis.Y)
        MainFragment.applyInsets(this, binding.recyclerView, binding.appBarOutline) { it ->
            UiUtils.configureSwipeRefresh(binding.swipeRefresh, it)
        }
        val uiViewModel by activityViewModel<UiViewModel>()
        val context = requireContext()
        
        ContextUtils.observe(this, uiViewModel.navigationReselected) {
            if (it != 1) return@observe
            binding.quickSearchView.show()
        }
        ContextUtils.observe(this, uiViewModel.navigation) {
            binding.quickSearchView.hide()
        }
        ContextUtils.observe(
            this,
            uiViewModel.navigation.combine(feedData.backgroundImageFlow) { a, b -> a to b }
        ) { (curr, bg) ->
            if (curr != 1) return@observe
            uiViewModel.currentNavBackground.value = bg
        }
        val backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                binding.quickSearchView.hide()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        binding.quickSearchView.addTransitionListener { v, _, _ ->
            backCallback.isEnabled = v.isShowing
        }
        UiUtils.applyBackPressCallback(this) {
            if (it == STATE_EXPANDED) binding.quickSearchView.hide()
        }
        val searchAdapter = SearchBarAdapter(searchViewModel, binding.quickSearchView)
        ContextUtils.observe(this, searchViewModel.queryFlow) {
            searchAdapter.notifyItemChanged(0)
            binding.quickSearchView.setText(it)
        }
        FeedAdapter.getTouchHelper(listener).attachToRecyclerView(binding.recyclerView)
        GridAdapter.configureGridLayout(
            binding.recyclerView,
            feedAdapter.withLoading(this, HeaderAdapter(this), searchAdapter)
        )
        binding.swipeRefresh.run {
            setOnRefreshListener { feedData.refresh() }
            ContextUtils.observe(this@SearchFragment, feedData.isRefreshingFlow) {
                isRefreshing = it
            }
        }
        binding.quickSearchView.editText.setText(searchViewModel.queryFlow.value)
        binding.quickSearchView.editText.doOnTextChanged { text, _, _, _ ->
            searchViewModel.quickSearch(text.toString())
        }
        binding.quickSearchView.editText.setOnEditorActionListener { textView, _, _ ->
            val query = textView.text.toString()
            binding.quickSearchView.hide()
            searchViewModel.queryFlow.value = query
            false
        }
        val quickSearchAdapter = QuickSearchAdapter(object : QuickSearchAdapter.Listener {
            override fun onClick(item: QuickSearchAdapter.Item, transitionView: View) {
                when (val actualItem = item.actual) {
                    is QuickSearchItem.Query -> {
                        binding.quickSearchView.editText.run {
                            setText(actualItem.query)
                            onEditorAction(imeOptions)
                        }
                    }

                    is QuickSearchItem.Media -> {
                        val origin = item.origin
                        listener.onMediaClicked(transitionView, origin, actualItem.media, null)
                    }
                }
            }

            override fun onDeleteClick(item: QuickSearchAdapter.Item) =
                searchViewModel.deleteSearch(item.actual)

            override fun onLongClick(item: QuickSearchAdapter.Item, transitionView: View) =
                when (val actualItem = item.actual) {
                    is QuickSearchItem.Query -> {
                        onDeleteClick(item)
                        true
                    }

                    is QuickSearchItem.Media -> {
                        val origin = item.origin
                        listener.onMediaLongClicked(
                            transitionView, origin, actualItem.media,
                            null, null, -1
                        )
                        true
                    }
                }

            override fun onInsert(item: QuickSearchAdapter.Item) {
                binding.quickSearchView.editText.run {
                    setText(item.actual.title)
                    setSelection(length())
                }
            }
        })

        binding.quickSearchRecyclerView.adapter = quickSearchAdapter
        ContextUtils.observe(this, searchViewModel.quickFeed) { list ->
            quickSearchAdapter.submitList(list.map {
                QuickSearchAdapter.Item(origin, it)
            })
        }
    }
}