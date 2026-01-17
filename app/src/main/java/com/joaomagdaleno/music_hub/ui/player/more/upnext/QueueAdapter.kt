package com.joaomagdaleno.music_hub.ui.player.more.upnext

import android.annotation.SuppressLint
import android.graphics.drawable.Animatable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.databinding.ItemPlaylistTrackBinding
import com.joaomagdaleno.music_hub.playback.MediaItemUtils
import com.joaomagdaleno.music_hub.ui.feed.viewholders.MediaViewHolder
import com.joaomagdaleno.music_hub.utils.image.ImageUtils
import com.joaomagdaleno.music_hub.utils.ui.AnimationUtils
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import com.joaomagdaleno.music_hub.utils.ui.scrolling.ScrollAnimViewHolder

class QueueAdapter(
    private val listener: Listener,
    private val inactive: Boolean = false
) : ListAdapter<Pair<Boolean?, MediaItem>, QueueAdapter.ViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<Pair<Boolean?, MediaItem>>() {
        override fun areItemsTheSame(
            oldItem: Pair<Boolean?, MediaItem>,
            newItem: Pair<Boolean?, MediaItem>
        ) = oldItem.second.mediaId == newItem.second.mediaId

        override fun areContentsTheSame(
            oldItem: Pair<Boolean?, MediaItem>,
            newItem: Pair<Boolean?, MediaItem>
        ) = oldItem == newItem

    }

    open class Listener {
        open fun onItemClicked(position: Int) {}
        open fun onItemClosedClicked(position: Int) {}
        open fun onDragHandleTouched(viewHolder: RecyclerView.ViewHolder) {}
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class ViewHolder(
        val binding: ItemPlaylistTrackBinding
    ) : ScrollAnimViewHolder(binding.root) {

        init {
            binding.playlistItemClose.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                listener.onItemClosedClicked(pos)
            }

            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                listener.onItemClicked(pos)
            }

            binding.playlistItemDrag.setOnTouchListener { _, event ->
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnTouchListener false
                if (event.actionMasked != MotionEvent.ACTION_DOWN) return@setOnTouchListener false
                listener.onDragHandleTouched(this)
                true
            }
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(ItemPlaylistTrackBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        onBind(holder, position)
        AnimationUtils.applyTranslationYAnimation(holder.itemView, scrollAmount)
    }

    private fun onBind(holder: ViewHolder, position: Int) {
        val (current, item) = getItem(position)
        val isCurrent = current != null
        val isPlaying = current == true
        val track = MediaItemUtils.getTrack(item)
        bind(holder.binding, track)
        isPlaying(holder.binding, isPlaying)
        holder.binding.playlistItemClose.isVisible = !inactive
        holder.binding.playlistItemDrag.isVisible = !inactive
        holder.binding.playlistCurrentItem.isVisible = isCurrent
        holder.binding.playlistProgressBar.isVisible = isCurrent && !MediaItemUtils.isLoaded(item)
        holder.binding.playlistItem.alpha = if (inactive) 0.5f else 1f
    }

    private var scrollAmount: Int = 0
    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            scrollAmount = dy
        }
    }

    var recyclerView: RecyclerView? = null
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
        recyclerView.addOnScrollListener(scrollListener)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        recyclerView.removeOnScrollListener(scrollListener)
        this.recyclerView = null
    }

    companion object {
        fun bind(binding: ItemPlaylistTrackBinding, track: Track) {
            binding.playlistItemTitle.run {
                text = track.title
                UiUtils.marquee(this)
            }

            ImageUtils.loadInto(track.cover, binding.playlistItemImageView, R.drawable.art_music)
            val subtitle = MediaViewHolder.subtitle(track, binding.root.context)
            binding.playlistItemAuthor.run {
                isVisible = !subtitle.isNullOrEmpty()
                text = subtitle
                UiUtils.marquee(this)
            }
        }

        fun isPlaying(binding: ItemPlaylistTrackBinding, isPlaying: Boolean) {
            binding.playlistItemNowPlaying.isVisible = isPlaying
            (binding.playlistItemNowPlaying.drawable as Animatable).start()
        }
    }
}
