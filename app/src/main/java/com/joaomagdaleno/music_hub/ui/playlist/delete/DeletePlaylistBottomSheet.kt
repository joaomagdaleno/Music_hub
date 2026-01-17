package com.joaomagdaleno.music_hub.ui.playlist.delete

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.Playlist
import com.joaomagdaleno.music_hub.databinding.ItemLoadingBinding
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import com.joaomagdaleno.music_hub.utils.ContextUtils.observe
import com.joaomagdaleno.music_hub.utils.Serializer
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf


class DeletePlaylistBottomSheet : BottomSheetDialogFragment(R.layout.item_loading) {

    companion object {
        fun show(
            activity: FragmentActivity, origin: String, item: Playlist, loaded: Boolean = false
        ): AlertDialog = with(activity) {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.confirmation))
                .setMessage(getString(R.string.delete_playlist_confirmation, item.title))
                .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                    newInstance(origin, item, loaded).show(supportFragmentManager, null)
                }
                .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                    dialog.dismiss()
                }.show()
        }

        private fun newInstance(
            origin: String, item: Playlist, loaded: Boolean
        ): DeletePlaylistBottomSheet {
            return DeletePlaylistBottomSheet().apply {
                arguments = Bundle().apply {
                    putString("origin", origin)
                    Serializer.putSerialized(this, "item", item)
                    putBoolean("loaded", loaded)
                }
            }
        }
    }

    val args by lazy { requireArguments() }
    val origin by lazy { args.getString("origin")!! }
    val item by lazy { Serializer.getSerialized<Playlist>(args, "item")!!.getOrThrow() }
    val loaded by lazy { args.getBoolean("loaded", false) }

    val vm by viewModel<DeletePlaylistViewModel> {
        parametersOf(origin, item, loaded)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = ItemLoadingBinding.bind(view)
                observe(this, vm.deleteStateFlow) { state ->
            val result = vm.playlistFlow.value
            val playlist = result?.getOrNull()
            val string = when (state) {
                is DeleteState.Deleted -> {
                    if (state.result.isSuccess) {
                        UiUtils.createSnack(this, getString(R.string.deleted_x, playlist?.title))
                        parentFragmentManager.setFragmentResult(
                            "deleted", bundleOf("id" to playlist?.id)
                        )
                        parentFragmentManager.setFragmentResult("reloadLibrary", Bundle.EMPTY)
                    }
                    dismiss()
                    return@observe
                }

                DeleteState.Deleting ->
                    getString(R.string.deleting_x, playlist?.title)

                DeleteState.Initial ->
                    getString(R.string.loading_x, playlist?.title)

                else -> ""
            }
            binding.textView.text = string
        }
    }
}