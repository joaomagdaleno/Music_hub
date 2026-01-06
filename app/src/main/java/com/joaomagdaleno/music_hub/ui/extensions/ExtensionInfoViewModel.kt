package com.joaomagdaleno.music_hub.ui.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaomagdaleno.music_hub.common.Extension
import com.joaomagdaleno.music_hub.common.clients.LoginClient
import com.joaomagdaleno.music_hub.common.clients.SettingsChangeListenerClient
import com.joaomagdaleno.music_hub.common.models.ExtensionType
import com.joaomagdaleno.music_hub.common.providers.SettingsProvider
import com.joaomagdaleno.music_hub.common.settings.Setting
import com.joaomagdaleno.music_hub.common.settings.Settings
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.extensions.ExtensionLoader
import com.joaomagdaleno.music_hub.extensions.ExtensionUtils.get
import com.joaomagdaleno.music_hub.extensions.ExtensionUtils.getIf
import com.joaomagdaleno.music_hub.extensions.ExtensionUtils.getOrThrow
import com.joaomagdaleno.music_hub.extensions.ExtensionUtils.isClient
import com.joaomagdaleno.music_hub.extensions.ExtensionUtils.runIf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

class ExtensionInfoViewModel(
    val app: App,
    val extensionLoader: ExtensionLoader,
    val type: ExtensionType,
    val id: String,
) : ViewModel() {

    private val reload = MutableSharedFlow<Unit>(1).also {
        viewModelScope.launch { it.emit(Unit) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val stateFlow = extensionLoader.getFlow(type).combine(reload) { a, _ -> a }
        .transformLatest { list ->
            emit(null)
            val ext = list.find { it.id == id } ?: return@transformLatest
            emit(
                State(
                    ext,
                    ext.isClient<LoginClient>(),
                    ext.isClient<SettingsChangeListenerClient>(),
                    ext.getIf<SettingsProvider, List<Setting>>(app.throwFlow) {
                        getSettingItems()
                    }.orEmpty()
                )
            )
        }.stateIn(viewModelScope, Eagerly, null)

    data class State(
        val extension: Extension<*>?,
        val isLoginClient: Boolean,
        val isPlaylistEditClient: Boolean,
        val settings: List<Setting>,
    )

    fun onSettingsChanged(settings: Settings, key: String?) = viewModelScope.launch {
        stateFlow.value?.extension?.runIf<SettingsChangeListenerClient>(app.throwFlow) {
            onSettingsChanged(settings, key)
        }
    }

    fun onSettingsClick(onClick: suspend () -> Unit) = viewModelScope.launch {
        stateFlow.value?.extension?.get { onClick() }?.getOrThrow(app.throwFlow)
    }
}