package com.joaomagdaleno.music_hub.common.models

import com.joaomagdaleno.music_hub.common.helpers.PagedData
import kotlinx.serialization.Serializable

data class Feed<T : Any>(
    val tabs: List<Tab>,
    val getPagedData: suspend (Tab?) -> Data<T>
) {

    val notSortTabs = tabs.filterNot { it.isSort }

    data class Data<T : Any>(
        val pagedData: PagedData<T>,
        val buttons: Buttons? = null,
        val background: ImageHolder? = null
    )

    @Serializable
    data class Buttons(
        val showSearch: Boolean = true,
        val showSort: Boolean = true,
        val showPlayAndShuffle: Boolean = false,
        val customTrackList: List<Track>? = null,
    ) {
        companion object {
            val EMPTY = Buttons(
                showSearch = false,
                showSort = false,
                showPlayAndShuffle = false,
                customTrackList = null
            )
        }
    }

    companion object {

        fun <T : Any> toFeedData(
            pagedData: PagedData<T>, buttons: Buttons? = null, background: ImageHolder? = null
        ) = Data(pagedData, buttons, background)

        fun <T : Any> toFeed(
            pagedData: PagedData<T>, buttons: Buttons? = null, background: ImageHolder? = null
        ) = Feed<T>(listOf()) { toFeedData(pagedData, buttons, background) }

        fun <T : Any> toFeedDataFromList(
            list: List<T>, buttons: Buttons? = null, background: ImageHolder? = null
        ) = Data(PagedData.Single { list }, buttons, background)

        fun <T : Any> toFeedFromList(
            list: List<T>, buttons: Buttons? = null, background: ImageHolder? = null
        ) = Feed<T>(listOf()) { toFeedData(PagedData.Single { list }, buttons, background) }

        suspend fun <T : Any> loadAll(feed: Feed<T>): List<T> {
            if (feed.tabs.isEmpty()) return pagedDataOfFirst(feed).loadAll()
            return feed.notSortTabs.flatMap { feed.getPagedData(it).pagedData.loadAll() }
        }

        suspend fun <T : Any> pagedDataOfFirst(feed: Feed<T>): PagedData<T> {
            return feed.getPagedData(feed.notSortTabs.firstOrNull()).pagedData
        }
        
        fun <T : Any> getPagedData(feed: Feed<T>, tab: Tab?): suspend () -> Data<T> {
            return { feed.getPagedData(tab) }
        }

        fun <T : Any> List<T>.toFeed(
            buttons: Buttons? = null, background: ImageHolder? = null
        ) = toFeedFromList(this, buttons, background)
    }
}