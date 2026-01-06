package com.joaomagdaleno.music_hub.common.clients

import com.joaomagdaleno.music_hub.common.models.Chapter
import com.joaomagdaleno.music_hub.common.models.Track

/**
 * Used to load [Chapter]s for a track.
 *
 * @see Track
 */
interface TrackChapterClient {

    /**
     * Gets the chapters for a track.
     *
     * @param track the track to get the chapters of.
     * @return the list of chapters for the track.
     *
     * @see Chapter
     */
    suspend fun getChapters(track: Track) : List<Chapter>
}