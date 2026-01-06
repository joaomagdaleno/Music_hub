package com.joaomagdaleno.music_hub.ui.main.search

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaomagdaleno.music_hub.common.Extension
import com.joaomagdaleno.music_hub.common.clients.QuickSearchClient
import com.joaomagdaleno.music_hub.common.models.QuickSearchItem
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.extensions.ExtensionLoader
import com.joaomagdaleno.music_hub.extensions.ExtensionUtils.getExtension
import com.joaomagdaleno.music_hub.extensions.ExtensionUtils.getIf
import com.joaomagdaleno.music_hub.extensions.ExtensionUtils.prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(
    private val app: App,
    extensionLoader: ExtensionLoader
) : ViewModel() {
    val queryFlow = MutableStateFlow("")
    private val music = extensionLoader.music
    val quickFeed = MutableStateFlow<List<QuickSearchItem>>(emptyList())
    fun quickSearch(extensionId: String, query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val extension = music.getExtension(extensionId)
            val list = extension?.getIf<QuickSearchClient, List<QuickSearchItem>>(app.throwFlow) {
                quickSearch(query)
            } ?: defaultQuickSearch(extension, app.context, query)
            quickFeed.value = list
        }
    }

    fun deleteSearch(extensionId: String, item: QuickSearchItem, query: String) {
        viewModelScope.launch {
            val extension = music.getExtension(extensionId)
            extension?.getIf<QuickSearchClient, Unit>(app.throwFlow) {
                deleteQuickSearch(item)
            } ?: defaultDeleteSearch(extension, app.context, item)
            quickSearch(extensionId, query)
        }
    }

    companion object {
        fun defaultQuickSearch(
            extension: Extension<*>?, context: Context, query: String
        ): List<QuickSearchItem> {
            val setting = extension?.prefs(context) ?: return emptyList()
            if (query.isNotBlank()) return emptyList()
            return getHistory(setting).map { QuickSearchItem.Query(it, true) }
        }

        fun defaultDeleteSearch(extension: Extension<*>?, context: Context, item: QuickSearchItem) {
            val setting = extension?.prefs(context) ?: return
            val history = getHistory(setting).toMutableList()
            history.remove(item.title)
            setting.edit { putString("search_history", history.joinToString(",")) }
        }

        private fun getHistory(setting: SharedPreferences): List<String> {
            return setting.getString("search_history", "")
                ?.split(",")?.mapNotNull {
                    it.takeIf { it.isNotBlank() }
                }?.distinct()?.take(5)
                ?: emptyList()
        }

        fun Extension<*>.saveInHistory(context: Context, query: String) {
            if (query.isBlank()) return
            val setting = prefs(context)
            val history = getHistory(setting).toMutableList()
            history.add(0, query)
            setting.edit { putString("search_history", history.joinToString(",")) }
        }
    }
}