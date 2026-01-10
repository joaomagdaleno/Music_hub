package com.joaomagdaleno.music_hub.ui.main.search

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaomagdaleno.music_hub.common.models.QuickSearchItem
import com.joaomagdaleno.music_hub.di.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(
    private val app: App,
    private val repository: com.joaomagdaleno.music_hub.data.repository.MusicRepository
) : ViewModel() {
    val queryFlow = MutableStateFlow("")
    val quickFeed = MutableStateFlow<List<QuickSearchItem>>(emptyList())
    val searchResults = MutableStateFlow<List<com.joaomagdaleno.music_hub.common.models.Track>>(emptyList())

    fun search(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val results = repository.search(query)
            searchResults.value = results
        }
    }

    private var lastSuggestionQuery = ""
    fun quickSearch(query: String) {
        if (query == lastSuggestionQuery) return
        lastSuggestionQuery = query
        viewModelScope.launch {
            if (query.isBlank()) {
                val history = getHistory(app.context.getSharedPreferences("search", Context.MODE_PRIVATE))
                    .map { QuickSearchItem.Query(it, true) }
                quickFeed.value = history
            } else {
                delay(300) // Debounce
                if (query != lastSuggestionQuery) return@launch
                val suggestions = repository.getSearchSuggestions(query)
                quickFeed.value = suggestions.map { QuickSearchItem.Query(it, false) }
            }
        }
    }

    fun deleteSearch(item: QuickSearchItem) {
        val prefs = app.context.getSharedPreferences("search", Context.MODE_PRIVATE)
        val history = getHistory(prefs).toMutableList()
        history.remove(item.title)
        prefs.edit { putString("search_history", history.joinToString(",")) }
        quickSearch("")
    }

    fun saveQuery(query: String) {
        if (query.isBlank()) return
        val prefs = app.context.getSharedPreferences("search", Context.MODE_PRIVATE)
        val history = getHistory(prefs).toMutableList()
        history.remove(query)
        history.add(0, query)
        prefs.edit { putString("search_history", history.joinToString(",")) }
    }

    companion object {
        private fun getHistory(prefs: SharedPreferences): List<String> {
            return prefs.getString("search_history", "")
                ?.split(",")?.mapNotNull {
                    it.takeIf { it.isNotBlank() }
                }?.distinct()?.take(5)
                ?: emptyList()
        }
    }
}