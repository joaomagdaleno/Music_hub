package com.joaomagdaleno.music_hub.ui.extensions.manage

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.Extension
import com.joaomagdaleno.music_hub.common.models.ExtensionType
import com.joaomagdaleno.music_hub.databinding.ItemExtensionBinding
import com.joaomagdaleno.music_hub.extensions.ExtensionLoader.Companion.priorityKey
import com.joaomagdaleno.music_hub.ui.feed.EmptyAdapter
import com.joaomagdaleno.music_hub.utils.image.ImageUtils.loadAsCircle
import com.joaomagdaleno.music_hub.utils.ui.scrolling.ScrollAnimViewHolder

class
ExtensionAdapter(
    val listener: Listener
) : PagingDataAdapter<Extension<*>, ExtensionAdapter.ViewHolder>(DiffCallback) {

    interface Listener {
        fun onClick(extension: Extension<*>, view: View)
        fun onDragHandleTouched(viewHolder: ViewHolder)
        fun onOpenClick(extension: Extension<*>)
    }

    object DiffCallback : DiffUtil.ItemCallback<Extension<*>>() {
        override fun areItemsTheSame(oldItem: Extension<*>, newItem: Extension<*>) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Extension<*>, newItem: Extension<*>) =
            oldItem == newItem
    }

    private val empty = EmptyAdapter()
    fun withEmptyAdapter() = ConcatAdapter(empty, this)

    class ViewHolder(val binding: ItemExtensionBinding, val listener: Listener) :
        ScrollAnimViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(extension: Extension<*>) {
            val metadata = extension.metadata
            binding.root.transitionName = metadata.id
            binding.root.setOnClickListener {
                listener.onClick(extension, binding.root)
            }
            binding.extensionName.apply {
                text = if (metadata.isEnabled) metadata.name
                else context.getString(R.string.x_disabled, metadata.name)
            }
            binding.extensionVersion.text = "${metadata.version} • ${metadata.importType.name}"
            binding.itemExtension.apply {
                metadata.icon.loadAsCircle(this, R.drawable.ic_extension_32dp) {
                    setImageDrawable(it)
                }
            }

            binding.extensionDrag.setOnTouchListener { v, _ ->
                v.performClick()
                listener.onDragHandleTouched(this)
                true
            }

            binding.extensionUse.isVisible = extension.type == ExtensionType.MUSIC
            binding.extensionUse.setOnClickListener {
                listener.onOpenClick(extension)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(ItemExtensionBinding.inflate(inflater, parent, false), listener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val download = runCatching { getItem(position) }.getOrNull() ?: return
        holder.bind(download)
    }

    suspend fun submit(list: List<Extension<*>>, selectedIndex: Int, settings: SharedPreferences) {
        submitData(PagingData.from(list))
        empty.loadState = if (list.isEmpty()) LoadState.Loading
        else LoadState.NotLoading(true)
        // Update priority map of extensions
        val key = ExtensionType.entries[selectedIndex].priorityKey()
        val extIds = list.joinToString(",") { it.id }
        settings.edit { putString(key, extIds) }
    }
}
