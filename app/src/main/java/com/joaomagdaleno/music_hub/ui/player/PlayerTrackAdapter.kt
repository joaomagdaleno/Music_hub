package com.joaomagdaleno.music_hub.ui.player

import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.databinding.ItemClickPanelsBinding
import com.joaomagdaleno.music_hub.databinding.ItemPlayerTrackBinding
import com.joaomagdaleno.music_hub.playback.MediaItemUtils
import com.joaomagdaleno.music_hub.playback.PlayerState
import com.joaomagdaleno.music_hub.ui.common.UiViewModel
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import com.joaomagdaleno.music_hub.utils.ui.UiUtils.dpToPx
import com.joaomagdaleno.music_hub.utils.ui.UiUtils.isLandscape
import com.joaomagdaleno.music_hub.utils.ui.UiUtils.isRTL
import com.joaomagdaleno.music_hub.utils.image.ImageUtils
import com.joaomagdaleno.music_hub.utils.ui.GestureListener
import com.joaomagdaleno.music_hub.utils.ui.scrolling.ScrollAnimViewHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.max

class PlayerTrackAdapter(
    private val uiViewModel: UiViewModel,
    private val current: MutableStateFlow<PlayerState.Current?>,
    private val listener: Listener
) : ListAdapter<MediaItem, PlayerTrackAdapter.ViewHolder>(DiffCallback) {

    interface Listener {
        fun onClick()
        fun onLongClick() {}
        fun onStartDoubleClick() {}
        fun onEndDoubleClick() {}
    }

    object DiffCallback : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem) =
            oldItem.mediaId == newItem.mediaId

        override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem == newItem
        }
    }

    inner class ViewHolder(
        private val binding: ItemPlayerTrackBinding
    ) : ScrollAnimViewHolder(binding.root) {

        private val context = binding.root.context

        private val collapsedPadding = UiUtils.dpToPx(context, 8)
        private val targetZ = collapsedPadding.toFloat()
        private val size = binding.root.resources.getDimension(R.dimen.collapsed_cover_size).toInt()
        private var targetScale = 0f
        private var targetX = 0
        private var targetY = 0

        private val cover = binding.playerTrackCoverContainer
        private var currentCoverHeight = size
        private var currCoverRound = 0f
        private val isLandscape = UiUtils.isLandscape(context)
        fun updateCollapsed() = uiViewModel.run {
            val insets = if (!isLandscape) systemInsets.value else getCombined()
            val targetPosX = collapsedPadding + if (UiUtils.isRTL(context)) insets.end else insets.start
            val targetPosY = if (playerSheetState.value != STATE_EXPANDED) 0
            else collapsedPadding + systemInsets.value.top
            targetX = targetPosX - (binding.root.left + cover.left)
            targetY = targetPosY - cover.top
            currentCoverHeight = cover.height.takeIf { it > 0 } ?: currentCoverHeight
            targetScale = size.toFloat() / currentCoverHeight

            val collapsedYAndOffset: Pair<Int, Float> = if (playerSheetState.value == STATE_EXPANDED)
                Pair(systemInsets.value.top, if (isLandscape) 0f else moreSheetOffset.value)
            else Pair(-collapsedPadding, 1 - max(0f, playerSheetOffset.value))
            val collapsedY = collapsedYAndOffset.first
            val offset = collapsedYAndOffset.second

            val inv = 1 - offset
            binding.playerCollapsed.root.run {
                translationY = collapsedY - size * inv * 2
                alpha = offset
            }
            if (isLandscape) binding.clickPanel.root.scaleX = 0.5f + 0.5f * inv
            val extraY = if (!isPlayerVisible) 0f else {
                val toMoveY = binding.playerControlsPlaceholder.top - cover.top
                toMoveY * inv
            }
            val extraX = if (!isPlayerVisible) 0f else {
                val toMoveX = binding.playerControlsPlaceholder.left - cover.left
                toMoveX * inv
            }
            cover.run {
                scaleX = if (!isPlayerVisible) 1 + (targetScale - 1) * offset else targetScale
                scaleY = scaleX
                translationX = targetX * offset + extraX
                translationY = targetY * offset + extraY
                translationZ = targetZ * (1 - offset)
                currCoverRound = collapsedPadding / scaleX
                invalidateOutline()
            }
        }

        fun updateInsets() = uiViewModel.run {
            val (v, h) = if (!isLandscape) 64 to 0 else 0 to 24
            UiUtils.applyInsets(binding.constraintLayout, systemInsets.value, v, h)
            val insets = if (isLandscape) getCombined() else systemInsets.value
            UiUtils.applyHorizontalInsets(binding.playerCollapsed.root, insets)
            binding.playerControlsPlaceholder.run {
                updateLayoutParams {
                    height = playerControlsHeight.value
                }
                doOnLayout {
                    updateCollapsed()
                    cover.doOnLayout { updateCollapsed() }
                }
            }

            updateCollapsed()
        }

        fun updateColors() {
            binding.playerCollapsed.run {
                val colors = uiViewModel.playerColors.value ?: PlayerColors.defaultPlayerColors(context)
                collapsedTrackTitle.setTextColor(colors.onBackground)
                collapsedTrackArtist.setTextColor(colors.onBackground)
            }
        }

        private var coverDrawable: Drawable? = null
        fun applyDrawable() {
            val index = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return
            val item = getItem(index) ?: return
            val curr = current.value?.mediaItem
            if (curr != item) return
            val drawable = coverDrawable
            currentDrawableListener?.invoke(drawable)
        }

        fun bind(item: MediaItem?) {
            val track = item?.let { MediaItemUtils.getTrack(it) }
            binding.playerCollapsed.run {
                collapsedTrackTitle.text = track?.title
                collapsedTrackArtist.text = track?.artists?.joinToString(", ") { it.name }
            }
            val old = ImageUtils.getCachedDrawable(item?.let { MediaItemUtils.getUnloadedCover(it) } ?: return, binding.root.context)
            ImageUtils.loadWithThumb(track?.cover, binding.playerTrackCover, old) { imageView, drawable ->
                val image = drawable
                    ?: ResourcesCompat.getDrawable(binding.root.resources, R.drawable.art_music, context.theme)
                imageView.setImageDrawable(image)
                coverDrawable = drawable
                applyDrawable()
            }
            updateInsets()
            updateColors()
        }

        init {
            cover.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(
                        0, 0, currentCoverHeight, currentCoverHeight, currCoverRound
                    )
                }
            }
            cover.clipToOutline = true
            cover.doOnLayout { updateInsets() }
            configureClicking(binding.clickPanel, listener, uiViewModel)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemPlayerTrackBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    var recyclerView: RecyclerView? = null
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = null
    }

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        holder.updateInsets()
        holder.updateColors()
        holder.applyDrawable()
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        holder.updateInsets()
        holder.updateColors()
        holder.applyDrawable()
    }

    private fun onEachViewHolder(block: ViewHolder.() -> Unit) {
        val recyclerView = recyclerView ?: return
        recyclerView.run {
            for (it in 0 until childCount) {
                val viewHolder = getChildViewHolder(getChildAt(it)) as? ViewHolder
                viewHolder?.block()
            }
        }
    }

    fun moreOffsetUpdated() = onEachViewHolder { updateCollapsed() }
    fun playerOffsetUpdated() = onEachViewHolder { updateCollapsed() }
    fun playerSheetStateUpdated() = onEachViewHolder { updateInsets() }
    fun insetsUpdated() = onEachViewHolder { updateInsets() }
    fun playerControlsHeightUpdated() = onEachViewHolder { updateInsets() }
    fun onColorsUpdated() = onEachViewHolder { updateColors() }
    fun onCurrentUpdated() {
        onEachViewHolder { applyDrawable() }
        if (current.value == null) currentDrawableListener?.invoke(null)
    }

    private var isPlayerVisible = false
    fun updatePlayerVisibility(visible: Boolean) {
        isPlayerVisible = visible
        onEachViewHolder { updateInsets() }
    }

    var currentDrawableListener: ((Drawable?) -> Unit)? = null

    companion object {
        fun configureClicking(binding: ItemClickPanelsBinding, listener: Listener, uiViewModel: UiViewModel) {
            GestureListener.handleGestures(binding.start, object : GestureListener {
                override val onClick = listener::onClick
                override val onLongClick = listener::onLongClick
                override val onDoubleClick: (() -> Unit)?
                    get() = if (uiViewModel.playerSheetState.value != STATE_EXPANDED) null
                    else listener::onStartDoubleClick
            })
            GestureListener.handleGestures(binding.end, object : GestureListener {
                override val onClick = listener::onClick
                override val onLongClick = listener::onLongClick
                override val onDoubleClick: (() -> Unit)?
                    get() = if (uiViewModel.playerSheetState.value != STATE_EXPANDED) null
                    else listener::onEndDoubleClick
            })
        }
    }
}