package com.joaomagdaleno.music_hub.ui.playlist.edit

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.Playlist
import com.joaomagdaleno.music_hub.databinding.ItemLoadingBinding
import com.joaomagdaleno.music_hub.utils.ContextUtils.observe
import com.joaomagdaleno.music_hub.utils.Serializer.getSerialized
import com.joaomagdaleno.music_hub.utils.Serializer.putSerialized
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class EditPlaylistBottomSheet : BottomSheetDialogFragment(R.layout.item_loading) {
    companion object {
        fun newInstance(
            extensionId: String, playlist: Playlist, tabId: String?, index: Int
        ) = EditPlaylistBottomSheet().apply {
            arguments = Bundle().apply {
                putString("extensionId", extensionId)
                putSerialized("playlist", playlist)
                putString("tabId", tabId)
                putInt("removeIndex", index)
            }
        }

        fun EditPlaylistViewModel.SaveState.toText(
            playlist: Playlist, context: Context
        ) = when (this) {
            is EditPlaylistViewModel.SaveState.Performing -> when (action) {
                is EditPlaylistViewModel.Action.Add ->
                    context.getString(R.string.adding_x, tracks.joinToString(", ") { it.title })

                is EditPlaylistViewModel.Action.Move ->
                    context.getString(R.string.moving_x, tracks.first().title)

                is EditPlaylistViewModel.Action.Remove ->
                    context.getString(R.string.removing_x, tracks.joinToString(", ") { it.title })
            }

            EditPlaylistViewModel.SaveState.Saving ->
                context.getString(R.string.saving_x, playlist.title)

            EditPlaylistViewModel.SaveState.Initial -> context.getString(R.string.loading)
            is EditPlaylistViewModel.SaveState.Saved -> context.getString(R.string.loading)
        }
    }

    val args by lazy { requireArguments() }
    val extensionId by lazy { args.getString("extensionId")!! }
    val playlist by lazy { args.getSerialized<Playlist>("playlist")!!.getOrThrow() }
    val tabId by lazy { args.getString("tabId") }
    val removeIndex by lazy { args.getInt("removeIndex", -1).takeIf { it != -1 }!! }

    val vm by viewModel<EditPlaylistViewModel> {
        parametersOf(extensionId, playlist, true, tabId, removeIndex)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = ItemLoadingBinding.bind(view)
        observe(vm.saveState) { save ->
            binding.textView.text = save.toText(playlist, requireContext())
            val save = save as? EditPlaylistViewModel.SaveState.Saved ?: return@observe
            if (save.result.isSuccess) parentFragmentManager.setFragmentResult(
                "reload", bundleOf("id" to playlist.id)
            )
            dismiss()
        }
    }
}