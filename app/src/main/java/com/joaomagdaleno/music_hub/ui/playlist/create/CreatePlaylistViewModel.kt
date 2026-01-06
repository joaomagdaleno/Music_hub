package com.joaomagdaleno.music_hub.ui.playlist.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaomagdaleno.music_hub.common.clients.PlaylistEditClient
import com.joaomagdaleno.music_hub.common.models.Playlist
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.extensions.ExtensionLoader
import com.joaomagdaleno.music_hub.extensions.ExtensionUtils.getIf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class CreatePlaylistViewModel(
    val app: App,
    val extensionLoader: ExtensionLoader,
) : ViewModel() {
    val createPlaylistStateFlow =
        MutableStateFlow<CreateState>(CreateState.CreatePlaylist)
    fun createPlaylist(title: String, desc: String?) {
        val extension = extensionLoader.current.value ?: return
        createPlaylistStateFlow.value = CreateState.Creating
        viewModelScope.launch(Dispatchers.IO) {
            val playlist = extension.getIf<PlaylistEditClient, Playlist>(app.throwFlow) {
                createPlaylist(title, desc)
            }
            createPlaylistStateFlow.value = CreateState.PlaylistCreated(extension.id, playlist)
        }
    }
}