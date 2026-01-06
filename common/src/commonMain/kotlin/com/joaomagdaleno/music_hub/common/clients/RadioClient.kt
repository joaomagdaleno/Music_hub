package com.joaomagdaleno.music_hub.common.clients

import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.Feed
import com.joaomagdaleno.music_hub.common.models.Radio
import com.joaomagdaleno.music_hub.common.models.Track

/**
 * Used to load the radio for track that is currently playing
 * and to show radio buttons for albums, artists, users and playlists.
 *
 * @see Radio
 */
interface RadioClient {

    /**
     * Loads a radio.
     *
     * @param radio the radio to load.
     * @return the loaded radio.
     *
     * @see Radio
     */
    suspend fun loadRadio(radio: Radio): Radio

    /**
     * Loads the tracks for a radio.
     *
     * @param radio the radio to load the tracks of.
     * @return the paged tracks.
     *
     * @see Feed
     */
    suspend fun loadTracks(radio: Radio): Feed<Track>

    /**
     * Creates a radio for a media item with [EchoMediaItem.isRadioSupported] set to true.
     *
     * If [item] is a [Track]. Make sure the radio tracks does not include this track.
     *
     * @param item the media item to create the radio for.
     * @param context the context of the item. Only available for [Track].
     * @return the created radio.
     *
     * @see EchoMediaItem
     * @see Radio
     * @see Track
     */
    suspend fun radio(item: EchoMediaItem, context: EchoMediaItem?): Radio
}