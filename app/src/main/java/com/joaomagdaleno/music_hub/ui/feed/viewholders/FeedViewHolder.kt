package com.joaomagdaleno.music_hub.ui.feed.viewholders

import android.view.View
import com.joaomagdaleno.music_hub.playback.PlayerState
import com.joaomagdaleno.music_hub.ui.feed.FeedType
import com.joaomagdaleno.music_hub.utils.ui.scrolling.ScrollAnimViewHolder

sealed class FeedViewHolder<T : FeedType>(view: View) : ScrollAnimViewHolder(view) {
    abstract fun bind(feed: T)
    open fun canBeSwiped(): Boolean = false
    open fun onSwipe(): T? = null
    open fun onCurrentChanged(current: PlayerState.Current?) {}
}