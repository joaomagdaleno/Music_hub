package com.joaomagdaleno.music_hub.ui.playlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.Artist
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.databinding.ItemMediaSelectableBinding
import com.joaomagdaleno.music_hub.databinding.ItemSelectableHeaderBinding
import com.joaomagdaleno.music_hub.ui.common.GridAdapter
import com.joaomagdaleno.music_hub.ui.feed.viewholders.MediaViewHolder
import com.joaomagdaleno.music_hub.utils.ui.scrolling.ScrollAnimListAdapter
import com.joaomagdaleno.music_hub.utils.ui.scrolling.ScrollAnimRecyclerAdapter
import com.joaomagdaleno.music_hub.utils.ui.scrolling.ScrollAnimViewHolder

class SelectableMediaAdapter(
    val listener: Listener
) : ScrollAnimListAdapter<Pair<EchoMediaItem, Boolean>, SelectableMediaAdapter.ViewHolder>(
    object : DiffUtil.ItemCallback<Pair<EchoMediaItem, Boolean>>() {
        override fun areItemsTheSame(
            oldItem: Pair<EchoMediaItem, Boolean>, newItem: Pair<EchoMediaItem, Boolean>
        ) = oldItem.first.id == newItem.first.id

        override fun areContentsTheSame(
            oldItem: Pair<EchoMediaItem, Boolean>, newItem: Pair<EchoMediaItem, Boolean>
        ) = oldItem == newItem
    }
), GridAdapter {

    fun interface Listener {
        fun onItemSelected(selected: Boolean, item: EchoMediaItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(parent, listener)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        holder.bind(getItem(position))
    }

    class ViewHolder(
        parent: ViewGroup,
        val listener: Listener,
        val binding: ItemMediaSelectableBinding = ItemMediaSelectableBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
    ) : ScrollAnimViewHolder(binding.root) {

        private var item: EchoMediaItem? = null

        init {
            binding.media.root.setOnClickListener {
                val item = item ?: return@setOnClickListener
                listener.onItemSelected(!binding.selected.isVisible, item)
            }
            binding.media.coverContainer.cover.clipToOutline = true
            binding.media.coverContainer.isPlaying.isVisible = false
        }

        fun bind(item: Pair<EchoMediaItem, Boolean>) {
            val mediaItem = item.first
            this.item = mediaItem
            // Manually bind because we have a different binding class than MediaViewHolder expects
            binding.media.title.text = mediaItem.title
            val subtitleText = MediaViewHolder.subtitle(mediaItem, binding.root.context)
            binding.media.subtitle.text = subtitleText
            binding.media.subtitle.isVisible = !subtitleText.isNullOrBlank()
            binding.media.coverContainer.run {
                MediaViewHolder.applyCover(mediaItem, cover, listBg1, listBg2, icon)
                isPlaying.setBackgroundResource(
                    if (mediaItem is Artist) R.drawable.rounded_rectangle_cover_profile
                    else R.drawable.rounded_rectangle_cover
                )
            }
            // binding.media.play.isVisible = mediaItem !is Track // 'play' not available in this binding
            binding.selected.isVisible = item.second
        }
    }

    private var header: Header? = null
    fun withHeader(selectAll: (Boolean) -> Unit): GridAdapter.Concat {
        val header = Header(selectAll)
        this.header = header
        return GridAdapter.Concat(header, this)
    }

    override fun onCurrentListChanged(
        previousList: MutableList<Pair<EchoMediaItem, Boolean>>,
        currentList: MutableList<Pair<EchoMediaItem, Boolean>>
    ) {
        header?.submitList(
            currentList.count { it.second },
            currentList.all { it.second }
        )
    }

    override val adapter = this
    override fun getSpanSize(position: Int, width: Int, count: Int) = 1

    class Header(
        private val onSelectAll: (Boolean) -> Unit
    ) : ScrollAnimRecyclerAdapter<Header.ViewHolder>(), GridAdapter {
        class ViewHolder(val binding: ItemSelectableHeaderBinding) :
            ScrollAnimViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = ItemSelectableHeaderBinding.inflate(inflater, parent, false)
            binding.root.setOnClickListener {
                onSelectAll(!binding.selectAll.isChecked)
            }
            return ViewHolder(binding)
        }

        override fun getItemCount() = 1
        private var count = 0
        private var selected = false

        fun submitList(count: Int, selected: Boolean) {
            this.count = count
            this.selected = selected
            notifyItemChanged(0)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            super.onBindViewHolder(holder, position)
            holder.binding.selected.run {
                text = context.getString(R.string.selected_n, count)
            }
            holder.binding.selectAll.isChecked = selected
        }

        override val adapter = this
        override fun getSpanSize(position: Int, width: Int, count: Int) = count
    }
}