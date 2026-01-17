package com.joaomagdaleno.music_hub.ui.download

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.Progress
import com.joaomagdaleno.music_hub.databinding.ItemDownloadBinding
import com.joaomagdaleno.music_hub.databinding.ItemDownloadTaskBinding
import com.joaomagdaleno.music_hub.download.Downloader
import com.joaomagdaleno.music_hub.download.db.models.ContextEntity
import com.joaomagdaleno.music_hub.download.db.models.DownloadEntity
import com.joaomagdaleno.music_hub.download.db.models.TaskType
import com.joaomagdaleno.music_hub.download.tasks.BaseTask
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import com.joaomagdaleno.music_hub.utils.ui.ExceptionData
import com.joaomagdaleno.music_hub.ui.common.GridAdapter
import com.joaomagdaleno.music_hub.utils.image.ImageUtils
import com.joaomagdaleno.music_hub.utils.ui.scrolling.ScrollAnimListAdapter
import com.joaomagdaleno.music_hub.utils.ui.scrolling.ScrollAnimViewHolder

class DownloadsAdapter(
    private val listener: Listener,
) : ScrollAnimListAdapter<DownloadsAdapter.Item, DownloadsAdapter.ViewHolder>(
    object : DiffUtil.ItemCallback<Item>() {
        override fun areContentsTheSame(oldItem: Item, newItem: Item) = oldItem == newItem
        override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean {
            return when (oldItem) {
                is Download -> if (newItem !is Download) false
                else oldItem.downloadEntity.id == newItem.downloadEntity.id

                is Task -> if (newItem !is Task) false
                else oldItem.id == newItem.id
            }
        }
    }
), GridAdapter {

    interface Listener {
        fun onExceptionClicked(data: ExceptionData)
        fun onCancel(trackId: Long)
        fun onRestart(trackId: Long)
    }

    sealed class ViewHolder(itemView: View) : ScrollAnimViewHolder(itemView)

    class DownloadViewHolder(
        parent: ViewGroup,
        private val listener: Listener,
        private val binding: ItemDownloadBinding = ItemDownloadBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        ),
    ) : ViewHolder(binding.root) {
        init {
            binding.imageView.clipToOutline = true
        }

        fun bind(item: Download) {
            val entity = item.downloadEntity
            binding.apply {
                val track = entity.track.getOrNull()
                title.text = track?.title
                ImageUtils.loadInto(track?.cover, imageView, R.drawable.art_music)
                val sub = item.context?.mediaItem?.getOrNull()?.title
                subtitle.text = sub
                subtitle.isVisible = !sub.isNullOrEmpty()

                exception.text = entity.exception?.title
                exception.isVisible = exception.text.isNotEmpty()

                remove.setOnClickListener {
                    listener.onCancel(entity.id)
                }

                retry.isVisible = entity.exception != null
                retry.setOnClickListener {
                    listener.onRestart(entity.id)
                }
                root.setOnClickListener {
                    val data = entity.exception ?: return@setOnClickListener
                    listener.onExceptionClicked(data)
                }
            }
        }
    }

    class TaskViewHolder(
        parent: ViewGroup,
        private val binding: ItemDownloadTaskBinding = ItemDownloadTaskBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        ),
    ) : ViewHolder(binding.root) {
        fun bind(item: Task) = binding.apply {
            progressBar.isIndeterminate = item.progress.size == 0L
            progressBar.max = item.progress.size.toInt()
            progressBar.progress = item.progress.progress.toInt()
            subtitle.text = toText(item.progress)
            title.text = BaseTask.getTitle(root.context, item.taskType, root.context.getString(R.string.download))
        }
    }


    sealed interface Item
    data class Download(
        val context: ContextEntity?,
        val downloadEntity: DownloadEntity,
        val origin: String?,
    ) : Item

    data class Task(val taskType: TaskType, val progress: Progress, val id: Long) : Item

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is Download -> 0
            is Task -> 1
        }
    }

    override val adapter = this
    override fun getSpanSize(position: Int, width: Int, count: Int) = 2.coerceAtLeast(count)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
        0 -> DownloadViewHolder(parent, listener)
        1 -> TaskViewHolder(parent)
        else -> throw IllegalArgumentException("Invalid view type")
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        when (holder) {
            is DownloadViewHolder -> {
                val item = getItem(position) as Download
                holder.bind(item)
            }

            is TaskViewHolder -> {
                val item = getItem(position) as Task
                holder.bind(item)
            }
        }
    }


    companion object {
        fun toItems(list: List<Downloader.Info>) = list.filter {
            it.download.finalFile == null
        }.flatMap { info ->
            val download = info.download
            listOf(Download(info.context, download, download.origin)) + info.workers.map {
                Task(it.first, it.second, download.id)
            }
        }

        private val SPEED_UNITS = arrayOf("", "KB", "MB", "GB")
        fun toText(progress: Progress) = buildString {
            if (progress.size > 0) append("%.2f%% • ".format(progress.progress.toFloat() / progress.size * 100))
            append(
                if (progress.size > 0) "${convertBytes(progress.progress)} / ${convertBytes(progress.size)}"
                else convertBytes(progress.progress)
            )
            if (progress.speed > 0) append(" • ${convertBytes(progress.speed)}/s")
        }

        private fun convertBytes(bytes: Long): String {
            var value = bytes.toFloat()
            var unitIndex = 0

            while (value >= 500 && unitIndex < SPEED_UNITS.size - 1) {
                value /= 1024
                unitIndex++
            }
            return "%.2f %s".format(value, SPEED_UNITS[unitIndex])
        }
    }
}