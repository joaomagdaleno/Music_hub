package com.joaomagdaleno.music_hub.ui.playlist.create

import com.joaomagdaleno.music_hub.common.models.Playlist

sealed class CreateState {
    data object CreatePlaylist : CreateState()
    data object Creating : CreateState()
    data class PlaylistCreated(val extensionId: String, val playlist: Playlist?) : CreateState()
}