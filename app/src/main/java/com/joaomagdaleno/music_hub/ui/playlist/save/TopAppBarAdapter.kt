package com.joaomagdaleno.music_hub.ui.playlist.save

import android.view.LayoutInflater
import android.view.ViewGroup
import com.joaomagdaleno.music_hub.databinding.ItemPlaylistSaveHeaderBinding
import com.joaomagdaleno.music_hub.ui.common.GridAdapter
import com.joaomagdaleno.music_hub.utils.ui.scrolling.ScrollAnimRecyclerAdapter
import com.joaomagdaleno.music_hub.utils.ui.scrolling.ScrollAnimViewHolder

class TopAppBarAdapter(
    private val onClose: () -> Unit,
    private val onCreateClicked: () -> Unit
) : ScrollAnimRecyclerAdapter<TopAppBarAdapter.ViewHolder>(), GridAdapter {
    inner class ViewHolder(
        val binding: ItemPlaylistSaveHeaderBinding
    ) : ScrollAnimViewHolder(binding.root) {
        init {
            binding.root.setNavigationOnClickListener {
                onClose()
            }

            binding.root.setOnMenuItemClickListener {
                onCreateClicked()
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(ItemPlaylistSaveHeaderBinding.inflate(inflater, parent, false))

    }

    override fun getItemCount() = 1
    override val adapter = this
    override fun getSpanSize(position: Int, width: Int, count: Int) = count
}