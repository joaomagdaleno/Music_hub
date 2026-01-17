package com.joaomagdaleno.music_hub.ui.media.more

import android.graphics.drawable.Animatable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.Artist
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.databinding.ItemMoreHeaderBinding
import com.joaomagdaleno.music_hub.playback.PlayerState
import com.joaomagdaleno.music_hub.playback.PlayerState.Current.Companion.isPlaying
import com.joaomagdaleno.music_hub.ui.common.GridAdapter
import com.joaomagdaleno.music_hub.ui.feed.viewholders.MediaViewHolder.Companion.applyCover
import com.joaomagdaleno.music_hub.ui.media.MediaHeaderAdapter
import com.joaomagdaleno.music_hub.utils.ui.scrolling.ScrollAnimViewHolder

class MoreHeaderAdapter(
    private val onCloseClicked: () -> Unit,
    private val onItemClicked: () -> Unit
) : RecyclerView.Adapter<MoreHeaderAdapter.ViewHolder>(), GridAdapter {
    override val adapter = this
    override fun getSpanSize(position: Int, width: Int, count: Int) = count
    override fun getItemCount() = 1
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val holder = ViewHolder(parent)
        holder.binding.run {
            coverContainer.cover.clipToOutline = true
            coverContainer.root.setOnClickListener { onItemClicked() }
            closeButton.setOnClickListener { onCloseClicked() }
        }
        return holder
    }

    var item: EchoMediaItem? = null
        set(value) {
            field = value
            notifyItemChanged(0)
        }

    private var viewHolder: ViewHolder? = null
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = with(holder.binding) {
        viewHolder = holder
        val item = item
        holder.bind(item)
        holder.onCurrentChanged(item, current)
    }

    class ViewHolder(
        parent: ViewGroup,
        val binding: ItemMoreHeaderBinding = ItemMoreHeaderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
    ) : ScrollAnimViewHolder(binding.root) {

        fun bind(item: EchoMediaItem?) = with(binding) {
            if (item == null) return@with
            title.text = item.title
            type.text = when (item) {
                is Artist -> ""
                is EchoMediaItem.Lists -> root.context.getString(MediaHeaderAdapter.getTypeInt(item))
                is Track -> root.context.getString(R.string.track)
            }
            coverContainer.run { applyCover(item, cover, listBg1, listBg2, icon) }
        }

        fun onCurrentChanged(item: EchoMediaItem?, current: PlayerState.Current?) {
            binding.coverContainer.isPlaying.run {
                val isPlaying = current?.let { PlayerState.Current.isPlaying(it, item?.id) } == true
                isVisible = isPlaying
                (icon as Animatable).start()
            }
        }
    }

    var current: PlayerState.Current? = null
    fun onCurrentChanged(current: PlayerState.Current?) {
        this.current = current
        viewHolder?.onCurrentChanged(item, current)
    }
}