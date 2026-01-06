package com.joaomagdaleno.music_hub.common.clients

import com.joaomagdaleno.music_hub.common.LyricsExtension
import com.joaomagdaleno.music_hub.common.MusicExtension
import com.joaomagdaleno.music_hub.common.helpers.PagedData
import com.joaomagdaleno.music_hub.common.models.Feed
import com.joaomagdaleno.music_hub.common.models.Lyrics
import com.joaomagdaleno.music_hub.common.models.Track

/**
 * Used to get the lyrics for track.
 *
 * Can be implemented by both:
 * - [MusicExtension]
 * - [LyricsExtension]
 *
 * To support lyrics search from user query. Use [LyricsSearchClient] instead.
 *
 * @see Lyrics
 * @see Track
 * @see PagedData
 */
interface LyricsClient : ExtensionClient {

    /**
     * Searches for the unloaded lyrics of a track.
     *
     * @param clientId the client id to use for the search.
     * @param track the track to search the lyrics for.
     * @return the paged lyrics.
     *
     * @see Lyrics
     * @see Track
     * @see PagedData
     */
    suspend fun searchTrackLyrics(clientId: String, track: Track): Feed<Lyrics>

    /**
     * Loads the unloaded lyrics.
     *
     * @param lyrics the lyrics to load.
     * @return the loaded lyrics.
     *
     * @see Lyrics
     */
    suspend fun loadLyrics(lyrics: Lyrics): Lyrics
}