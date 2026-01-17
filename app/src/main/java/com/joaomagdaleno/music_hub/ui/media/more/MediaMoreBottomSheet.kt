package com.joaomagdaleno.music_hub.ui.media.more

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.paging.LoadState
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.Artist
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.Playlist
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.databinding.DialogMediaMoreBinding
import com.joaomagdaleno.music_hub.download.Downloader
import com.joaomagdaleno.music_hub.common.models.MediaState
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import com.joaomagdaleno.music_hub.ui.common.GridAdapter
import com.joaomagdaleno.music_hub.ui.download.DownloadViewModel
import com.joaomagdaleno.music_hub.ui.feed.FeedLoadingAdapter
import com.joaomagdaleno.music_hub.ui.feed.viewholders.MediaViewHolder
import com.joaomagdaleno.music_hub.ui.media.MediaFragment
import com.joaomagdaleno.music_hub.ui.media.MediaViewModel
import com.joaomagdaleno.music_hub.ui.media.more.button
import com.joaomagdaleno.music_hub.ui.player.PlayerViewModel
import com.joaomagdaleno.music_hub.ui.player.audiofx.AudioEffectsBottomSheet
import com.joaomagdaleno.music_hub.ui.player.more.lyrics.LyricsItemAdapter
import com.joaomagdaleno.music_hub.ui.player.quality.QualitySelectionBottomSheet
import com.joaomagdaleno.music_hub.ui.player.sleep.SleepTimerBottomSheet
import com.joaomagdaleno.music_hub.ui.playlist.delete.DeletePlaylistBottomSheet
import com.joaomagdaleno.music_hub.ui.playlist.edit.EditPlaylistBottomSheet
import com.joaomagdaleno.music_hub.ui.playlist.edit.EditPlaylistFragment
import com.joaomagdaleno.music_hub.ui.playlist.save.SaveToPlaylistBottomSheet
import com.joaomagdaleno.music_hub.utils.ContextUtils
import com.joaomagdaleno.music_hub.utils.Serializer
import kotlinx.coroutines.flow.combine
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class MediaMoreBottomSheet : BottomSheetDialogFragment(R.layout.dialog_media_more) {
    companion object {
        fun newInstance(
            contId: Int,
            origin: String,
            item: EchoMediaItem,
            loaded: Boolean,
            fromPlayer: Boolean = false,
            context: EchoMediaItem? = null,
            tabId: String? = null,
            pos: Int? = null,
        ) = MediaMoreBottomSheet().apply {
            arguments = Bundle().apply {
                putInt("contId", contId)
                putString("origin", origin)
                Serializer.putSerialized(this, "item", item)
                putBoolean("loaded", loaded)
                Serializer.putSerialized(this, "context", context)
                putBoolean("fromPlayer", fromPlayer)
                putString("tabId", tabId)
                putInt("pos", pos ?: -1)
            }
        }
    }

    private val args by lazy { requireArguments() }
    private val contId by lazy { args.getInt("contId", -1).takeIf { it != -1 }!! }
    private val origin by lazy { args.getString("origin")!! }
    private val item by lazy { Serializer.getSerialized<EchoMediaItem>(args, "item")!!.getOrThrow() }
    private val loaded by lazy { args.getBoolean("loaded") }
    private val itemContext by lazy { Serializer.getSerialized<EchoMediaItem?>(args, "context")?.getOrNull() }
    private val tabId by lazy { args.getString("tabId") }
    private val pos by lazy { args.getInt("pos") }
    private val fromPlayer by lazy { args.getBoolean("fromPlayer") }

    private val vm by viewModel<MediaViewModel> {
        parametersOf(false, origin, item, loaded)
    }
    private val playerViewModel by activityViewModel<PlayerViewModel>()

    private val actionAdapter by lazy { MoreButtonAdapter() }
    private val headerAdapter by lazy {
        MoreHeaderAdapter({ dismiss() }, {
            openItemFragment(origin, item, loaded)
            dismiss()
        })
    }

    private val loadingAdapter by lazy {
        FeedLoadingAdapter(FeedLoadingAdapter.createListener(this) { vm.refresh() }) {
            LyricsItemAdapter.Loading(it)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = DialogMediaMoreBinding.bind(view)
        ContextUtils.observe(this, playerViewModel.playerState.current) {
            headerAdapter.onCurrentChanged(it)
        }
        val actionFlow =
            combine(vm.downloadsFlow, vm.uiResultFlow) { _, _ -> }
        ContextUtils.observe(this, actionFlow) {
            val result = vm.uiResultFlow.value?.getOrNull()
            val downloads = vm.downloadsFlow.value.filter { it.download.finalFile != null }
            val loaded = if (result != null) true else loaded
            val list = getButtons(result, loaded, downloads)
            actionAdapter.submitList(list)
            headerAdapter.item = result?.item ?: item
        }
        ContextUtils.observe(this, vm.itemResultFlow) { result ->
            loadingAdapter.loadState = result?.map { LoadState.NotLoading(false) }?.getOrElse {
                LoadState.Error(it)
            } ?: LoadState.Loading
        }
        GridAdapter.configureGridLayout(
            binding.root,
            GridAdapter.Concat(
                headerAdapter,
                actionAdapter,
                loadingAdapter
            )
        )
    }

    private fun getButtons(
        state: MediaState.Loaded<*>?,
        loaded: Boolean,
        downloads: List<Downloader.Info>
    ) = getPlayerButtons() +
            getPlayButtons(state?.item ?: item, loaded) +
            getPlaylistEditButtons(state, loaded) +
            getDownloadButtons(state, downloads) +
            getActionButtons(state) +
            getItemButtons(state?.item ?: item)

    private fun getPlayerButtons() = if (fromPlayer) listOf(
        button("audio_fx", R.string.audio_fx, R.drawable.ic_equalizer) {
            AudioEffectsBottomSheet().show(parentFragmentManager, null)
        },
        button("sleep_timer", R.string.sleep_timer, R.drawable.ic_snooze) {
            SleepTimerBottomSheet().show(parentFragmentManager, null)
        },
        button("quality_selection", R.string.quality_selection, R.drawable.ic_high_quality) {
            QualitySelectionBottomSheet().show(parentFragmentManager, null)
        }
    ) else listOf()

    private fun getPlayButtons(
        item: EchoMediaItem, loaded: Boolean
    ) = if (item is Track) listOfNotNull(
        button("play", R.string.play, R.drawable.ic_play) {
            playerViewModel.play(origin, item, loaded)
        },
        if (playerViewModel.queue.isNotEmpty())
            button("next", R.string.add_to_next, R.drawable.ic_playlist_play) {
                playerViewModel.addToNext(origin, item, loaded)
            }
        else null,
        if (playerViewModel.queue.size > 1)
            button("queue", R.string.add_to_queue, R.drawable.ic_playlist_add) {
                playerViewModel.addToQueue(origin, item, loaded)
            }
        else null
    ) else listOf()

    fun getPlaylistEditButtons(
        state: MediaState<*>?, loaded: Boolean
    ) = run {
        val item = state?.item ?: item
        val isEditable = item is Playlist && item.isEditable
        listOfNotNull(
            if (item is Track && loaded) button(
                "save_to_playlist", R.string.save_to_playlist, R.drawable.ic_library_music
            ) {
                SaveToPlaylistBottomSheet.newInstance(origin, item)
                    .show(parentFragmentManager, null)
            } else null,
            if (isEditable) button(
                "edit_playlist", R.string.edit_playlist, R.drawable.ic_edit_note
            ) {
                openEditFragment<EditPlaylistFragment>(
                    origin, item, loaded
                )
            } else null,
            if (isEditable) button(
                "delete_playlist", R.string.delete_playlist, R.drawable.ic_delete
            ) {
                DeletePlaylistBottomSheet.show(requireActivity(), origin, item, loaded)
            } else null,
            if ((itemContext as? Playlist)?.isEditable == true && item is Track) button(
                "remove_from_playlist", R.string.remove, R.drawable.ic_cancel
            ) {
                EditPlaylistBottomSheet.newInstance(
                    origin, itemContext as Playlist, tabId, pos
                ).show(parentFragmentManager, null)
            } else null
        )
    }

    fun getDownloadButtons(
        state: MediaState<*>?, downloads: List<Downloader.Info>
    ) = run {
        val item = state?.item ?: item
        val shouldShowDelete = when (item) {
            is Track -> downloads.any { it.download.trackId == item.id }
            else -> downloads.any { it.context?.itemId == item.id }
        }
        val downloadable = item is Track

        listOfNotNull(
            if (downloadable) button(
                "download", R.string.download, R.drawable.ic_download_for_offline
            ) {
                val downloadViewModel by activityViewModel<DownloadViewModel>()
                downloadViewModel.addToDownload(requireActivity(), origin, item, itemContext)
            } else null,
            if (shouldShowDelete) button(
                "delete_download", R.string.delete_download, R.drawable.ic_scan_delete
            ) {
                val downloadViewModel by activityViewModel<DownloadViewModel>()
                downloadViewModel.deleteDownload(item)
            } else null
        )
    }

    fun getActionButtons(
        state: MediaState.Loaded<*>?,
    ) = listOfNotNull(
        if (state?.isFollowed != null) button(
            "follow", if (state.isFollowed) R.string.unfollow else R.string.follow,
            if (state.isFollowed) R.drawable.ic_check_circle_filled else R.drawable.ic_check_circle
        ) {
            vm.followItem(!state.isFollowed)
        } else null,
        if (state?.isSaved != null) button(
            "save_to_library",
            if (state.isSaved) R.string.remove_from_library else R.string.save_to_library,
            if (state.isSaved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_outline
        ) {
            vm.saveToLibrary(!state.isSaved)
        } else null,
        if (state?.isLiked != null) button(
            "like", if (state.isLiked) R.string.unlike else R.string.like,
            if (state.isLiked) R.drawable.ic_heart_filled_40dp else R.drawable.ic_heart_outline_40dp
        ) {
            vm.likeItem(!state.isLiked)
        } else null,
        if (state?.isHidden != null) button(
            "hide", if (state.isHidden) R.string.unhide else R.string.hide,
            if (state.isHidden) R.drawable.ic_unhide else R.drawable.ic_hide
        ) {
            vm.hideItem(!state.isHidden)
        } else null,
        if (state?.showRadio == true) button(
            "radio", R.string.radio, R.drawable.ic_sensors
        ) {
            playerViewModel.radio(origin, state.item, true)
        } else null,
        if (state?.showShare == true) button(
            "share", R.string.share, R.drawable.ic_share
        ) {
            vm.onShare()
        } else null
    )

    private fun getItemButtons(item: EchoMediaItem) = when (item) {
        is Track -> item.artists + listOfNotNull(item.album)
        is EchoMediaItem.Lists -> item.artists
        is Artist -> listOf()
    }.map {
        button(it.id, it.title, MediaViewHolder.getIcon(it)) {
            openItemFragment(origin, it)
        }
    }

    private fun openItemFragment(
        origin: String?, item: EchoMediaItem?, loaded: Boolean = false
    ) {
        origin ?: return
        item ?: return
        val fragment = parentFragmentManager.findFragmentById(contId) ?: return
        UiUtils.openFragment<MediaFragment>(fragment, null, MediaFragment.getBundle(origin, item, loaded))
        dismiss()
    }

    private inline fun <reified T : Fragment> openEditFragment(
        origin: String?, item: EchoMediaItem?, loaded: Boolean = false
    ) {
        origin ?: return
        item ?: return
        val fragment = parentFragmentManager.findFragmentById(contId) ?: return
        UiUtils.openFragment<T>(fragment, null, EditPlaylistFragment.getBundle(origin, item as Playlist, loaded))
        dismiss()
    }
}