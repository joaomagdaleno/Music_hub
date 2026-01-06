package com.joaomagdaleno.music_hub.ui.main.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.joaomagdaleno.music_hub.common.models.QuickSearchItem
import com.joaomagdaleno.music_hub.databinding.ItemQuickSearchMediaBinding
import com.joaomagdaleno.music_hub.databinding.ItemQuickSearchQueryBinding
import com.joaomagdaleno.music_hub.ui.feed.viewholders.MediaViewHolder.Companion.placeHolder
import com.joaomagdaleno.music_hub.ui.feed.viewholders.MediaViewHolder.Companion.subtitle
import com.joaomagdaleno.music_hub.utils.image.ImageUtils.loadInto
import com.joaomagdaleno.music_hub.utils.ui.scrolling.ScrollAnimViewHolder

sealed class QuickSearchViewHolder(itemView: View) : ScrollAnimViewHolder(itemView) {
    abstract fun bind(item: QuickSearchAdapter.Item)
    abstract val insertView: View
    abstract val deleteView: View
    open val transitionView: View
        get() = this.insertView

    class Query(val binding: ItemQuickSearchQueryBinding) : QuickSearchViewHolder(binding.root) {
        override val insertView: View
            get() = binding.insert

        override val deleteView: View
            get() = binding.delete

        override fun bind(item:  QuickSearchAdapter.Item) {
            val item = item.actual as QuickSearchItem.Query
            binding.history.visibility = if (item.searched) View.VISIBLE else View.INVISIBLE
            binding.query.text = item.query
        }

        companion object {
            fun create(
                parent: ViewGroup
            ): QuickSearchViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                return Query(
                    ItemQuickSearchQueryBinding.inflate(layoutInflater, parent, false)
                )
            }
        }
    }

    class Media(val binding: ItemQuickSearchMediaBinding) : QuickSearchViewHolder(binding.root) {
        override val insertView: View
            get() = binding.insert

        override val deleteView: View
            get() = binding.delete

        override val transitionView: View
            get() = binding.coverContainer

        override fun bind(item:  QuickSearchAdapter.Item) {
            val item = item.actual as QuickSearchItem.Media
            binding.title.text = item.media.title
            val subtitle = item.media.subtitle(binding.root.context)
            binding.subtitle.text = subtitle
            binding.subtitle.isVisible = !subtitle.isNullOrEmpty()
            transitionView.transitionName = ("quick" + item.media.id).hashCode().toString()
            item.media.cover.loadInto(binding.cover, item.media.placeHolder)
        }

        companion object {
            fun create(
                parent: ViewGroup
            ): QuickSearchViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                return Media(
                    ItemQuickSearchMediaBinding.inflate(layoutInflater, parent, false)
                )
            }
        }
    }
}