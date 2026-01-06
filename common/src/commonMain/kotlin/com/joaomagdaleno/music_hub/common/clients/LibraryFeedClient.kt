package com.joaomagdaleno.music_hub.common.clients

import com.joaomagdaleno.music_hub.common.MusicExtension
import com.joaomagdaleno.music_hub.common.models.Feed
import com.joaomagdaleno.music_hub.common.models.Shelf

/**
 * Used to show the library feed and get its tabs.
 *
 * @see Feed
 * @see MusicExtension
 */
interface LibraryFeedClient {

    /**
     * Gets the library feed.
     * Checkout [Feed] for more information.
     */
    suspend fun loadLibraryFeed(): Feed<Shelf>

}