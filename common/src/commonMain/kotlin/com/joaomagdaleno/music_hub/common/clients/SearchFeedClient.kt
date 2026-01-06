package com.joaomagdaleno.music_hub.common.clients

import com.joaomagdaleno.music_hub.common.MusicExtension
import com.joaomagdaleno.music_hub.common.models.Feed
import com.joaomagdaleno.music_hub.common.models.Shelf

/**
 * Used to show the search the feed.
 *
 * @see Feed
 * @see MusicExtension
 */
interface SearchFeedClient {

    /**
     * Gets the search feed.
     *
     * @param query the query to search for, will be empty if the user hasn't typed anything.
     * @return the feed.
     *
     * @see Feed
     */
    suspend fun loadSearchFeed(query: String): Feed<Shelf>
}