package com.joaomagdaleno.music_hub.ui.media

import android.os.Bundle
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.Artist
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.Playlist
import com.joaomagdaleno.music_hub.databinding.FragmentMediaBinding
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import com.joaomagdaleno.music_hub.ui.feed.viewholders.MediaViewHolder
import com.joaomagdaleno.music_hub.ui.media.more.MediaMoreBottomSheet
import com.joaomagdaleno.music_hub.ui.playlist.delete.DeletePlaylistBottomSheet
import com.joaomagdaleno.music_hub.ui.playlist.edit.EditPlaylistFragment
import com.joaomagdaleno.music_hub.utils.ContextUtils
import com.joaomagdaleno.music_hub.utils.Serializer
import com.joaomagdaleno.music_hub.utils.image.ImageUtils
import com.joaomagdaleno.music_hub.utils.ui.AnimationUtils
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class MediaFragment : Fragment(R.layout.fragment_media), MediaDetailsFragment.Parent {
    companion object {
        fun getBundle(origin: String, item: EchoMediaItem, loaded: Boolean) = Bundle().apply {
            putString("origin", origin)
            Serializer.putSerialized(this, "item", item)
            putBoolean("loaded", loaded)
        }
    }

    val args by lazy { requireArguments() }
    val origin by lazy { args.getString("origin")!! }
    val item by lazy { Serializer.getSerialized<EchoMediaItem>(args, "item")!!.getOrThrow() }
    val loaded by lazy { args.getBoolean("loaded") }

    override val fromPlayer = false
    override val feedId by lazy { item.id }

    override val viewModel by viewModel<MediaViewModel> {
        parametersOf(true, origin, item, loaded)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentMediaBinding.bind(view)
        AnimationUtils.setupTransition(this, view)
        UiUtils.applyBackPressCallback(this)
        UiUtils.configureAppBar(binding.appBarLayout) { offset ->
            binding.appbarOutline.alpha = offset
            binding.coverContainer.alpha = 1 - offset
            binding.endIcon.alpha = 1 - offset
        }
        binding.toolBar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.toolBar.setOnMenuItemClickListener {
            val item = viewModel.itemResultFlow.value?.getOrNull()?.item ?: item
            MediaMoreBottomSheet.newInstance(
                id, origin, item, !viewModel.isRefreshing
            ).show(parentFragmentManager, null)
            true
        }
        UiUtils.applyInsets(this) { insets ->
            UiUtils.applyFabInsets(binding.fabContainer, insets, systemInsets.value)
        }

        ContextUtils.observe(this, viewModel.itemResultFlow) { result ->
            val item = result?.getOrNull()?.item ?: item
            binding.toolBar.title = item.title.trim()
            binding.endIcon.setImageResource(MediaViewHolder.getIcon(item))
            if (item is Artist) binding.coverContainer.run {
                val maxWidth = UiUtils.dpToPx(context, 240)
                radius = maxWidth.toFloat()
                updateLayoutParams<ConstraintLayout.LayoutParams> {
                    matchConstraintMaxWidth = maxWidth
                }
            }
            ImageUtils.loadInto(item.cover, binding.cover, MediaViewHolder.getPlaceHolder(item))
            ImageUtils.loadWithThumb(item.background, view) { _, drawable ->
                UiUtils.applyGradient(this@MediaFragment, view, drawable)
            }
            val isEditable = (result?.getOrNull()?.item as? Playlist)?.isEditable ?: false
            binding.fabEditPlaylist.isVisible = isEditable
            binding.fabEditPlaylist.setOnClickListener {
                val playlist = item as? Playlist ?: return@setOnClickListener
                UiUtils.openFragment<EditPlaylistFragment>(
                    this, it, EditPlaylistFragment.getBundle(origin, playlist, loaded)
                )
            }
        }
        parentFragmentManager.setFragmentResultListener("reload", this) { _, data ->
            if (data.getString("id") == item.id) viewModel.refresh()
        }
        parentFragmentManager.setFragmentResultListener("delete", this) { _, data ->
            val playlist = item as? Playlist ?: return@setFragmentResultListener
            DeletePlaylistBottomSheet.show(
                requireActivity(), origin, playlist, !viewModel.isRefreshing
            )
        }
        parentFragmentManager.setFragmentResultListener("deleted", this) { _, data ->
            if (data.getString("id") == item.id) parentFragmentManager.popBackStack()
        }
    }
}