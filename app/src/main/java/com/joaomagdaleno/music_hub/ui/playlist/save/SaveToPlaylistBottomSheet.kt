package com.joaomagdaleno.music_hub.ui.playlist.save

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.Playlist
import com.joaomagdaleno.music_hub.databinding.DialogPlaylistSaveBinding
import com.joaomagdaleno.music_hub.ui.common.GridAdapter
import com.joaomagdaleno.music_hub.ui.playlist.SelectableMediaAdapter
import com.joaomagdaleno.music_hub.ui.playlist.create.CreatePlaylistBottomSheet
import com.joaomagdaleno.music_hub.utils.ContextUtils
import com.joaomagdaleno.music_hub.utils.Serializer
import com.joaomagdaleno.music_hub.utils.ui.AutoClearedValue
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import kotlinx.coroutines.flow.combine
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class SaveToPlaylistBottomSheet : BottomSheetDialogFragment() {

    companion object {
        fun newInstance(origin: String, item: EchoMediaItem) =
            SaveToPlaylistBottomSheet().apply {
                arguments = Bundle().apply {
                    putString("origin", origin)
                    Serializer.putSerialized(this, "item", item)
                }
            }
    }

    private val args by lazy { requireArguments() }
    private val origin by lazy { args.getString("origin")!! }
    private val item by lazy { Serializer.getSerialized<EchoMediaItem>(args, "item")!!.getOrThrow() }

    private val itemAdapter by lazy {
        MediaItemAdapter { _, _ -> }
    }

    private val adapter by lazy {
        SelectableMediaAdapter { _, item ->
            viewModel.togglePlaylist(item as Playlist)
        }
    }

    private val topBarAdapter by lazy {
        TopAppBarAdapter(
            { dismiss() },
            { CreatePlaylistBottomSheet().show(parentFragmentManager, null) }
        )
    }

    private var binding by AutoClearedValue.autoCleared<DialogPlaylistSaveBinding>(this)
    private val viewModel by viewModel<SaveToPlaylistViewModel> {
        parametersOf(origin, item)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        binding = DialogPlaylistSaveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        UiUtils.configureBottomBar(this, binding.saveCont)
        binding.save.setOnClickListener {
            viewModel.saveTracks()
        }
        itemAdapter.submitList(listOf(MediaItemAdapter.Item(origin, item)))
        val combined = viewModel.playlistsFlow.combine(viewModel.saveFlow) { playlists, save ->
            playlists to save
        }
        GridAdapter.configureGridLayout(
            binding.recyclerView,
            GridAdapter.Concat(
                topBarAdapter,
                itemAdapter,
                adapter.withHeader { viewModel.toggleAll(it) },
            ),
            false
        )
        ContextUtils.observe(this, combined) { (state, save) ->
            val playlistLoading = state !is SaveToPlaylistViewModel.PlaylistState.Loaded
            val saving = save != SaveToPlaylistViewModel.SaveState.Initial
            val loading = playlistLoading || saving
            binding.recyclerView.isVisible = !saving
            binding.loading.root.isVisible = loading
            binding.loading.textView.text = when (save) {
                SaveToPlaylistViewModel.SaveState.Initial -> getString(R.string.loading)
                is SaveToPlaylistViewModel.SaveState.LoadingPlaylist ->
                    getString(R.string.loading_x, save.playlist.title)

                SaveToPlaylistViewModel.SaveState.LoadingTracks -> getString(
                    R.string.loading_x,
                    item.title
                )

                is SaveToPlaylistViewModel.SaveState.Saved -> {
                    dismiss()
                    getString(R.string.not_loading)
                }

                is SaveToPlaylistViewModel.SaveState.Saving ->
                    getString(R.string.saving_x, save.playlist.title)
            }
            if (state is SaveToPlaylistViewModel.PlaylistState.Loaded) {
                if (state.list == null) {
                    dismiss()
                    return@observe
                }
                adapter.submitList(state.list)
                binding.save.isEnabled = state.list.any { it.second }
            }
        }

        parentFragmentManager.setFragmentResultListener(
            "createPlaylist", this
        ) { _, _ -> viewModel.refresh() }
    }
}