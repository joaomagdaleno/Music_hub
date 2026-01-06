package com.joaomagdaleno.music_hub.common.clients

import com.joaomagdaleno.music_hub.common.helpers.PagedData
import com.joaomagdaleno.music_hub.common.models.Feed
import com.joaomagdaleno.music_hub.common.models.Lyrics

/**
 * Used to search for lyrics.
 *
 * @see Lyrics
 * @see PagedData
 */
interface LyricsSearchClient : LyricsClient {
    /**
     * Searches for lyrics.
     *
     * @param query the query to search for.
     * @return the paged lyrics.
     *
     * @see Lyrics
     * @see PagedData
     */
    suspend fun searchLyrics(query: String): Feed<Lyrics>
}