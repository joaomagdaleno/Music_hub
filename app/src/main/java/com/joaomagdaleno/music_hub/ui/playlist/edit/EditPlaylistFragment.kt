package com.joaomagdaleno.music_hub.ui.playlist.edit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ConcatAdapter
import com.joaomagdaleno.music_hub.common.models.Playlist
import com.joaomagdaleno.music_hub.common.models.Tab
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.databinding.FragmentPlaylistEditBinding
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import com.joaomagdaleno.music_hub.ui.feed.TabsAdapter
import com.joaomagdaleno.music_hub.ui.playlist.edit.search.EditPlaylistSearchFragment
import com.joaomagdaleno.music_hub.utils.ContextUtils
import com.joaomagdaleno.music_hub.utils.Serializer
import com.joaomagdaleno.music_hub.utils.ui.AnimationUtils
import com.joaomagdaleno.music_hub.utils.ui.AutoClearedValue
import com.joaomagdaleno.music_hub.utils.ui.FastScrollerHelper
import kotlinx.coroutines.flow.combine
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class EditPlaylistFragment : Fragment() {

    companion object {
        fun getBundle(source: String, playlist: Playlist, loaded: Boolean) = Bundle().apply {
            putString("origin", source)
            Serializer.putSerialized(this, "playlist", playlist)
            putBoolean("loaded", loaded)
        }
    }

    private val args by lazy { requireArguments() }
    private val origin by lazy { args.getString("origin")!! }
    private val playlist by lazy { Serializer.getSerialized<Playlist>(args, "playlist")!!.getOrThrow() }
    private val loaded by lazy { args.getBoolean("loaded", false) }
    private val selectedTab by lazy { args.getString("selectedTabId").orEmpty() }

    private var binding: FragmentPlaylistEditBinding by AutoClearedValue.autoCleared(this)
    private val vm by viewModel<EditPlaylistViewModel> {
        parametersOf(origin, playlist, loaded, selectedTab, -1)
    }

    private val adapter by lazy {
        val (listener, itemCallback) = PlaylistTrackAdapter.getTouchHelperAndListener(vm)
        itemCallback.attachToRecyclerView(binding.recyclerView)
        PlaylistTrackAdapter(listener)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPlaylistEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        AnimationUtils.setupTransition(this, view)
        UiUtils.applyInsetsWithChild(this, binding.appBarLayout, binding.recyclerView, 96) {
            UiUtils.applyInsets(binding.fabContainer, it)
        }

        UiUtils.applyBackPressCallback(this)
        UiUtils.configureAppBar(binding.appBarLayout) { offset ->
            binding.toolbarOutline.alpha = offset
            binding.toolbarIconContainer.alpha = 1 - offset
        }

        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.toolbar.setOnMenuItemClickListener {
            parentFragmentManager.setFragmentResult("delete", Bundle().apply {
                Serializer.putSerialized(this, "playlist", playlist)
            })
            parentFragmentManager.popBackStack()
            true
        }

        binding.save.setOnClickListener {
            vm.save()
        }
        ContextUtils.observe(this, vm.isSaveable) {
            binding.save.isEnabled = it
        }

        binding.add.setOnClickListener {
            UiUtils.openFragment<EditPlaylistSearchFragment>(
                this, it, EditPlaylistSearchFragment.getBundle(origin)
            )
        }
        parentFragmentManager.setFragmentResultListener("searchedTracks", this) { _, bundle ->
            val tracks = Serializer.getSerialized<List<Track>>(bundle, "tracks")!!.getOrNull().orEmpty().toMutableList()
            vm.edit(
                EditPlaylistViewModel.Action.Add(
                    vm.currentTracks.value?.size ?: 0, tracks
                )
            )
        }

        FastScrollerHelper.applyTo(binding.recyclerView)

        val headerAdapter = EditPlaylistHeaderAdapter(this, vm)
        val tabAdapter = TabsAdapter<Tab>({ title }) { v, index, tab ->
            vm.selectedTabFlow.value = tab
        }

        binding.recyclerView.adapter = ConcatAdapter(headerAdapter, tabAdapter, adapter)
        ContextUtils.observe(this, vm.dataFlow) { headerAdapter.data = it }
        ContextUtils.observe(this, vm.tabsFlow) { tabAdapter.data = it }
        ContextUtils.observe(this, vm.selectedTabFlow) { tabAdapter.selected = vm.tabsFlow.value.indexOf(it) }
        ContextUtils.observe(this, vm.currentTracks) { adapter.submitList(it) }

        val combined = vm.originalList.combine(vm.saveState) { a, b -> a to b }
        ContextUtils.observe(this, combined) { (tracks, save) ->
            val trackLoading = tracks == null
            val saving = save != EditPlaylistViewModel.SaveState.Initial
            val loading = trackLoading || saving
            binding.recyclerView.isVisible = !loading
            binding.fabContainer.isVisible = !loading
            binding.loading.root.isVisible = loading
            binding.loading.textView.text = EditPlaylistBottomSheet.getSaveStateText(requireContext(), playlist, save)

            val saveRes = save as? EditPlaylistViewModel.SaveState.Saved ?: return@observe
            if (saveRes.result.isSuccess) parentFragmentManager.setFragmentResult(
                "reload", bundleOf("id" to playlist.id)
            )
            parentFragmentManager.popBackStack()
        }
    }
}