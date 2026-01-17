package com.joaomagdaleno.music_hub.ui.media

import android.content.Context
import android.icu.text.CompactDecimalFormat
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.Album
import com.joaomagdaleno.music_hub.common.models.Album.Type.Book
import com.joaomagdaleno.music_hub.common.models.Album.Type.Compilation
import com.joaomagdaleno.music_hub.common.models.Album.Type.EP
import com.joaomagdaleno.music_hub.common.models.Album.Type.LP
import com.joaomagdaleno.music_hub.common.models.Album.Type.PreRelease
import com.joaomagdaleno.music_hub.common.models.Album.Type.Show
import com.joaomagdaleno.music_hub.common.models.Album.Type.Single
import com.joaomagdaleno.music_hub.common.models.Artist
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.Playlist
import com.joaomagdaleno.music_hub.common.models.Radio
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.databinding.ItemLineBinding
import com.joaomagdaleno.music_hub.databinding.ItemMediaHeaderBinding
import com.joaomagdaleno.music_hub.databinding.ItemShelfErrorBinding
import com.joaomagdaleno.music_hub.common.models.MediaState
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import com.joaomagdaleno.music_hub.ui.common.GridAdapter
import com.joaomagdaleno.music_hub.ui.media.MediaFragment.Companion.getBundle
import com.joaomagdaleno.music_hub.ui.player.PlayerViewModel
import com.joaomagdaleno.music_hub.utils.ui.SimpleItemSpan
import com.joaomagdaleno.music_hub.utils.ui.scrolling.ScrollAnimRecyclerAdapter
import com.joaomagdaleno.music_hub.utils.ui.scrolling.ScrollAnimViewHolder

class MediaHeaderAdapter(
    private val listener: Listener,
    private val fromPlayer: Boolean,
) : ScrollAnimRecyclerAdapter<MediaHeaderAdapter.ViewHolder>(), GridAdapter {

    interface Listener {
        fun onRetry(view: View)
        fun onError(view: View, error: Throwable?)
        fun onDescriptionClicked(view: View, origin: String?, item: EchoMediaItem?)
        fun openMediaItem(origin: String, item: EchoMediaItem)
        fun onFollowClicked(view: View, follow: Boolean)
        fun onSavedClicked(view: View, saved: Boolean)
        fun onLikeClicked(view: View, liked: Boolean)
        fun onPlayClicked(view: View)
        fun onRadioClicked(view: View)
        fun onShareClicked(view: View)
        fun onHideClicked(view: View, hidden: Boolean)
    }

    override val adapter = this
    override fun getSpanSize(position: Int, width: Int, count: Int) = count
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
        0 -> Success(parent, listener, fromPlayer)
        1 -> Error(parent, listener)
        2 -> Loading(parent)
        else -> throw IllegalArgumentException("Unknown view type: $viewType")
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        when (holder) {
            is Success -> {
                val state = result?.getOrNull() ?: return
                holder.bind(state)
            }

            is Error -> {
                val error = result?.exceptionOrNull() ?: return
                holder.bind(error)
            }

            is Loading -> {}
        }
    }

    override fun getItemCount() = 1
    override fun getItemViewType(position: Int) = when (result?.isSuccess) {
        true -> 0
        false -> 1
        null -> 2
    }

    var result: Result<MediaState.Loaded<*>>? = null
        set(value) {
            field = value
            notifyItemChanged(0)
        }

    sealed class ViewHolder(itemView: View) : ScrollAnimViewHolder(itemView)
    class Success(
        parent: ViewGroup,
        private val listener: Listener,
        private val fromPlayer: Boolean,
        private val binding: ItemMediaHeaderBinding = ItemMediaHeaderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        ),
    ) : ViewHolder(binding.root) {
        val buttons = binding.run {
            listOf(
                followButton, playButton, savedButton, likeButton, hideButton,
                radioButton, shareButton
            )
        }

        init {
            binding.followButton.setOnClickListener {
                listener.onFollowClicked(it, binding.followButton.isChecked)
                it.isEnabled = false
            }
            binding.savedButton.setOnClickListener {
                listener.onSavedClicked(it, binding.savedButton.isChecked)
                it.isEnabled = false
            }
            binding.likeButton.setOnClickListener {
                listener.onLikeClicked(it, binding.likeButton.isChecked)
                it.isEnabled = false
            }
            binding.hideButton.setOnClickListener {
                listener.onHideClicked(it, binding.hideButton.isChecked)
                it.isEnabled = false
            }
            binding.playButton.setOnClickListener {
                listener.onPlayClicked(it)
            }
            binding.radioButton.setOnClickListener {
                listener.onRadioClicked(it)
            }
            binding.shareButton.setOnClickListener {
                listener.onShareClicked(it)
                it.isEnabled = false
            }
        }


        fun configureButtons() {
            val visible = buttons.filter { it.isVisible }
            binding.buttonGroup.isVisible = visible.isNotEmpty()
            val isNotOne = visible.size > 1
            visible.forEachIndexed { index, button ->
                button.isEnabled = true
                if (index == 0 && isNotOne) button.run {
                    updatePaddingRelative(
                        start = if (icon != null) UiUtils.dpToPx(context, 16) else UiUtils.dpToPx(context, 24),
                        end = UiUtils.dpToPx(context, 24)
                    )
                    iconPadding = UiUtils.dpToPx(context, 8)
                    text = contentDescription
                } else button.run {
                    updatePaddingRelative(start = UiUtils.dpToPx(context, 12), end = UiUtils.dpToPx(context, 12))
                    iconPadding = 0
                    text = null
                }
            }
            binding.buttonGroup.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                width = if (isNotOne) ViewGroup.LayoutParams.MATCH_PARENT
                else ViewGroup.LayoutParams.WRAP_CONTENT
                bottomMargin = if (isNotOne) 0 else UiUtils.dpToPx(binding.root.context, -56)
            }
            binding.description.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginEnd = if (isNotOne) 0 else UiUtils.dpToPx(binding.root.context, 48)
            }
        }

        var clickEnabled = true
        var state: MediaState.Loaded<*>? = null

        fun bind(state: MediaState.Loaded<*>) = with(binding) {
            this@Success.state = state
            followButton.isVisible = state.isFollowed != null
            followButton.isChecked = state.isFollowed ?: false
            followButton.contentDescription = root.context.getString(
                if (state.isFollowed == true) R.string.unfollow else R.string.follow
            )

            savedButton.isVisible = state.isSaved != null
            savedButton.isChecked = state.isSaved ?: false
            savedButton.contentDescription = root.context.getString(
                if (state.isSaved == true) R.string.unsave else R.string.save
            )

            likeButton.isVisible = state.isLiked != null && !fromPlayer
            likeButton.isChecked = state.isLiked ?: false
            likeButton.contentDescription = root.context.getString(
                if (state.isLiked == true) R.string.unlike else R.string.like
            )

            hideButton.isVisible = state.isHidden != null
            hideButton.isChecked = state.isHidden ?: false
            hideButton.contentDescription = root.context.getString(
                if (state.isHidden == true) R.string.unhide else R.string.hide
            )

            playButton.isVisible = state.item is Track && !fromPlayer && state.item.isPlayable == Track.Playable.Yes
            radioButton.isVisible = state.showRadio
            shareButton.isVisible = state.showShare
            configureButtons()

            explicit.isVisible = state.item.isExplicit
            followers.isVisible = state.followers != null
            followers.text = state.followers?.let {
                val formatter = CompactDecimalFormat.getInstance()
                root.context.getString(R.string.x_followers, formatter.format(it))
            }
            val span =
                getSpan(root.context, true, state.origin, state.item) { id, item ->
                    clickEnabled = false
                    listener.openMediaItem(id, item)
                    description.post { clickEnabled = true }
                }
            description.text = span
            description.isVisible = span.isNotEmpty()
        }

        init {
            binding.run {
                description.movementMethod = LinkMovementMethod.getInstance()
                description.setOnClickListener {
                    if (clickEnabled) listener.onDescriptionClicked(
                        it,
                        state?.origin,
                        state?.item
                    )
                }
            }
        }
    }

    class Error(
        parent: ViewGroup,
        listener: Listener,
        private val binding: ItemShelfErrorBinding = ItemShelfErrorBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        ),
    ) : ViewHolder(binding.root) {
        var throwable: Throwable? = null

        init {
            binding.errorView.setOnClickListener {
                listener.onError(binding.error, throwable)
            }
            binding.retry.setOnClickListener {
                listener.onRetry(it)
            }
        }

        fun bind(throwable: Throwable) {
            this.throwable = throwable
            binding.error.run {
                transitionName = throwable.hashCode().toString()
                text = UiUtils.getFinalExceptionTitle(context, throwable)
            }
        }
    }

    class Loading(
        parent: ViewGroup,
        binding: ItemLineBinding = ItemLineBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        ),
    ) : ViewHolder(binding.root) {
        init {
            itemView.alpha = 0f
        }
    }

    companion object {
        private const val MAX_DESC_TEXT = 144
        private fun ellipsize(text: String) = if (text.length > MAX_DESC_TEXT) {
            text.substring(0, MAX_DESC_TEXT) + "..."
        } else text

        private const val DIVIDER = " • "
        fun getSpan(
            context: Context,
            compact: Boolean,
            origin: String,
            item: EchoMediaItem,
            openMediaItem: (String, EchoMediaItem) -> Unit = { a, b -> },
        ): SpannableString = when (item) {
            is EchoMediaItem.Lists -> {
                val madeBy = item.artists.joinToString(", ") { it.name }
                val span = SpannableString(buildString {
                    val firstRow = listOfNotNull(
                        context.getString(getTypeInt(item)),
                        item.date?.toString(),
                    ).joinToString(DIVIDER)
                    val secondRow = listOfNotNull(
                        toTrackString(item, context),
                        item.duration?.let { UiUtils.toTimeString(it) }
                    ).joinToString(DIVIDER)
                    if (firstRow.isNotEmpty()) appendLine(firstRow)
                    if (secondRow.isNotEmpty()) appendLine(secondRow)
                    val desc = item.description
                    if (desc != null) {
                        appendLine()
                        appendLine(if (compact) ellipsize(desc) else desc)
                    }
                    if (madeBy.isNotEmpty()) {
                        appendLine()
                        appendLine(context.getString(R.string.by_x, madeBy))
                    }
                    if (item.label != null) {
                        appendLine()
                        appendLine(item.label)
                    }
                }.trimEnd('\n').trimStart('\n'))
                val madeByIndex = span.indexOf(madeBy)
                item.artists.forEach {
                    val start = span.indexOf(it.name, madeByIndex)
                    if (start != -1) {
                        val end = start + it.name.length
                        // The provided code edit was syntactically incorrect and semantically out of place.
                        // Assuming the intent was to replace the clickableSpan creation and setting,
                        // but the replacement itself was not valid for this context.
                        // Reverting to original logic for clickableSpan to maintain functionality.
                        // If the intention was to add image loading, it needs to be done in a different context.
                        val clickableSpan = SimpleItemSpan(context) {
                            openMediaItem(origin, it)
                        }
                        span.setSpan(
                            clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
                span
            }

            is Artist -> {
                val desc = if (compact) item.bio?.let { ellipsize(it) } else item.bio
                SpannableString(desc ?: "")
            }

            is Track -> {
                SpannableString(buildString {
                    val firstRow = listOfNotNull(
                        context.getString(
                            when (item.type) {
                                Track.Type.Song -> R.string.song
                                Track.Type.Podcast -> R.string.podcast
                            }
                        ),
                        item.releaseDate
                    ).joinToString(DIVIDER)
                    val secondRow = listOfNotNull(
                        item.duration?.let { UiUtils.toTimeString(it) },
                        if (item.plays != null) {
                            val formatter = CompactDecimalFormat.getInstance()
                            context.getString(R.string.x_plays, formatter.format(item.plays))
                        } else null
                    ).joinToString(DIVIDER)
                    if (firstRow.isNotEmpty()) appendLine(firstRow)
                    if (secondRow.isNotEmpty()) appendLine(secondRow)
                    val notPlayable = playableString(item, context)
                    if (!notPlayable.isNullOrEmpty()) {
                        appendLine()
                        appendLine(notPlayable)
                    }
                    val desc = item.description
                    if (desc != null) {
                        appendLine()
                        appendLine(if (compact) ellipsize(desc) else desc)
                        appendLine()
                    }
                    val genres = item.genres.joinToString(", ")
                    if (genres.isNotEmpty()) {
                        appendLine(context.getString(R.string.genres_x, genres))
                    }
                    val isrc = item.isrc
                    if (isrc != null) {
                        appendLine(context.getString(R.string.isrc_x, isrc))
                    }
                    val label = item.album?.label
                    if (label != null) {
                        appendLine()
                        appendLine(label)
                    }
                    val lastRow = listOfNotNull(
                        item.albumDiscNumber?.let {
                            context.getString(R.string.disc_number_n, it)
                        },
                        item.albumOrderNumber?.let {
                            context.getString(R.string.album_order_n, it)
                        }
                    ).joinToString(DIVIDER)
                    if (lastRow.isNotEmpty()) {
                        appendLine()
                        appendLine(lastRow)
                    }
                }.trimStart('\n').trimEnd('\n'))
            }
        }

        fun unfuckedString(
            context: Context, numberStringId: Int, nStringId: Int, count: Int,
        ) = runCatching {
            context.resources.getQuantityString(numberStringId, count, count)
        }.getOrNull() ?: context.getString(nStringId, count)

        fun getMediaHeaderListener(fragment: Fragment, viewModel: MediaDetailsViewModel) = object : Listener {
            override fun onRetry(view: View) {
                viewModel.refresh()
            }

            override fun onError(view: View, error: Throwable?) {
                error ?: return
                UiUtils.getExceptionMessage(fragment.requireActivity(), error, view).action?.handler?.invoke()
            }

            override fun openMediaItem(origin: String, item: EchoMediaItem) {
                UiUtils.openFragment<MediaFragment>(fragment, null, getBundle(origin, item, false))
            }

            override fun onFollowClicked(view: View, follow: Boolean) {
                viewModel.followItem(follow)
            }

            override fun onSavedClicked(view: View, saved: Boolean) {
                viewModel.saveToLibrary(saved)
            }

            override fun onLikeClicked(view: View, liked: Boolean) {
                viewModel.likeItem(liked)
            }

            override fun onHideClicked(view: View, hidden: Boolean) {
                viewModel.hideItem(hidden)
            }

            override fun onPlayClicked(view: View) {
                val (origin, item, loaded) = viewModel.getItem() ?: return
                val vm by fragment.activityViewModels<PlayerViewModel>()
                vm.play(origin, item, loaded)
            }

            override fun onRadioClicked(view: View) {
                val (origin, item, loaded) = viewModel.getItem() ?: return
                val vm by fragment.activityViewModels<PlayerViewModel>()
                vm.radio(origin, item, loaded)
            }

            override fun onShareClicked(view: View) {
                viewModel.onShare()
            }

            override fun onDescriptionClicked(
                view: View, origin: String?, item: EchoMediaItem?,
            ) {
                item ?: return
                origin ?: return
                val context = fragment.requireContext()
                var dialog: AlertDialog? = null
                val builder = MaterialAlertDialogBuilder(context)
                builder.setTitle(item.title)
                builder.setMessage(getSpan(context, false, origin, item) { m, n ->
                    openMediaItem(m, n)
                    dialog?.dismiss()
                })
                builder.setPositiveButton(context.getString(R.string.okay)) { d, _ ->
                    d.dismiss()
                }
                dialog = builder.create()
                dialog.show()
                val text = dialog.findViewById<TextView>(android.R.id.message)!!
                text.movementMethod = LinkMovementMethod.getInstance()
            }
        }

        fun getTypeInt(item: EchoMediaItem.Lists) = when (item) {
            is Album -> when (item.type) {
                PreRelease -> R.string.pre_release
                Single -> R.string.single
                EP -> R.string.ep
                LP -> R.string.lp
                Compilation -> R.string.compilation
                Show -> R.string.show
                Book -> R.string.book
                null -> R.string.album
            }

            is Playlist -> R.string.playlist
            is Radio -> R.string.radio
        }

        fun toTrackString(item: EchoMediaItem.Lists, context: Context) = context.run {
            val tracks = item.trackCount?.toInt()
            if (tracks != null) {
                when (item.type) {
                    PreRelease, Single, EP, LP, Compilation -> unfuckedString(
                        context, R.plurals.number_songs, R.string.n_songs, tracks
                    )

                    Show -> unfuckedString(
                        context, R.plurals.number_episodes, R.string.n_episodes, tracks
                    )

                    Book -> unfuckedString(
                        context, R.plurals.number_chapters, R.string.n_chapters, tracks
                    )

                    null -> unfuckedString(
                        context, R.plurals.number_tracks, R.string.n_tracks, tracks
                    )
                }
            } else null
        }

        fun playableString(item: Track, context: Context) = when (val play = item.isPlayable) {
            is Track.Playable.No -> context.getString(R.string.not_playable_x, play.reason)
            Track.Playable.Yes -> null
            Track.Playable.RegionLocked -> context.getString(R.string.unavailable_in_your_region)
            Track.Playable.Unreleased -> if (item.releaseDate != null) context.getString(
                R.string.releases_on_x, item.releaseDate.toString()
            ) else context.getString(R.string.not_yet_released)
        }
    }
}