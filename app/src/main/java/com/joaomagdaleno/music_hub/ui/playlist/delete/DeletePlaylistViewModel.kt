package com.joaomagdaleno.music_hub.ui.playlist.delete

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaomagdaleno.music_hub.common.clients.PlaylistEditClient
import com.joaomagdaleno.music_hub.common.models.Playlist
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.extensions.ExtensionLoader
import com.joaomagdaleno.music_hub.extensions.ExtensionUtils.getAs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DeletePlaylistViewModel(
    extensionLoader: ExtensionLoader,
    private val app: App,
    private val extensionId: String,
    private val item: Playlist,
    private val loaded: Boolean,
) : ViewModel() {

    val extensionFlow = extensionLoader.music.map { list ->
        list.find { it.id == extensionId }
    }.stateIn(viewModelScope, Eagerly, null)

    val playlistFlow = extensionFlow.transformLatest {
        emit(null)
        if (it == null) return@transformLatest
        if (loaded) emit(Result.success(item))
        else emit(it.getAs<PlaylistEditClient, Playlist> { loadPlaylist(item) })
    }.stateIn(viewModelScope, Eagerly, null)

    private val deleteFlow = MutableSharedFlow<Unit>()
    fun delete() = viewModelScope.launch { deleteFlow.emit(Unit) }

    val deleteStateFlow = deleteFlow.transformLatest {
        emit(DeleteState.Deleting)
        val result = runCatching {
            val extension = extensionFlow.value!!
            val playlist = playlistFlow.value!!.getOrThrow()
            extension.getAs<PlaylistEditClient, Unit> { deletePlaylist(playlist) }.getOrThrow()
        }
        result.getOrElse { app.throwFlow.emit(it) }
        emit(DeleteState.Deleted(result))
    }.stateIn(viewModelScope, Eagerly, DeleteState.Initial)

    init {
        viewModelScope.launch {
            playlistFlow.first { it != null }
            delete()
        }
    }
}