package com.joaomagdaleno.music_hub.common.clients

import com.joaomagdaleno.music_hub.common.models.Playlist
import java.io.File

/**
 * Used to allow editing the cover of a playlist.
 *
 * @see PlaylistEditClient
 */
interface PlaylistEditCoverClient : PlaylistEditClient {

    /**
     * Edit the cover of a playlist.
     *
     * @param playlist the playlist to edit the cover of.
     * @param cover the new cover of the playlist, or null if user removed the cover.
     */
    suspend fun editPlaylistCover(playlist: Playlist, cover: File?)
}