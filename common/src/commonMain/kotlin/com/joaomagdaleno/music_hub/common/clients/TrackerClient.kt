package com.joaomagdaleno.music_hub.common.clients

import com.joaomagdaleno.music_hub.common.MusicExtension
import com.joaomagdaleno.music_hub.common.TrackerExtension
import com.joaomagdaleno.music_hub.common.models.TrackDetails

/**
 * Used to track the playback of a track.
 *
 * You can override the following methods to get the track details:
 * - [onTrackChanged]
 * - [onPlayingStateChanged]
 *
 * Can be implemented by both:
 * - [MusicExtension]
 * - [TrackerExtension]
 *
 * @see TrackerMarkClient
 */
interface TrackerClient : ExtensionClient {

    /**
     * Called when the player changes its current track.
     *
     *  Note: This method will be called again if the track was played again from the beginning.
     *
     * @param details the details of the track that is playing, or null if player is empty.
     */
    suspend fun onTrackChanged(details: TrackDetails?)

    /**
     * Called when the player changes its playing state or when the position changes.
     *
     * @param details the details of the track that is playing, or null if the player is empty.
     */
    suspend fun onPlayingStateChanged(details: TrackDetails?, isPlaying: Boolean)
}