package com.joaomagdaleno.music_hub.common.clients

import com.joaomagdaleno.music_hub.common.MusicExtension
import com.joaomagdaleno.music_hub.common.models.Feed
import com.joaomagdaleno.music_hub.common.models.Shelf

/**
 * Used to show the home feed and get its tabs.
 *
 * @see Feed
 * @see MusicExtension
 */
interface HomeFeedClient {

    /**
     * Gets the home feed.
     * Checkout [Feed] for more information.
     */
    suspend fun loadHomeFeed(): Feed<Shelf>
}
