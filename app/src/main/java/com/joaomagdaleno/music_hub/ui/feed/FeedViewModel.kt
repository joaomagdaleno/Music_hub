package com.joaomagdaleno.music_hub.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joaomagdaleno.music_hub.common.models.Feed
import com.joaomagdaleno.music_hub.common.models.Shelf
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.extensions.ExtensionLoader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

class FeedViewModel(
    val app: App,
    private val extensionLoader: ExtensionLoader,
) : ViewModel() {
    val feedDataMap = hashMapOf<String, FeedData>()
    fun getFeedData(
        id: String,
        buttons: Feed.Buttons = Feed.Buttons(),
        noVideos: Boolean = false,
        vararg extraLoadFlow: Flow<*>,
        cached: suspend ExtensionLoader.() -> FeedData.State<Feed<Shelf>>? = { null },
        loader: suspend ExtensionLoader.() -> FeedData.State<Feed<Shelf>>?
    ): FeedData {
        return feedDataMap.getOrPut(id) {
            FeedData(
                feedId = id,
                scope = viewModelScope,
                app = app,
                extensionLoader = extensionLoader,
                cached = cached,
                load = loader,
                defaultButtons = buttons,
                noVideos = noVideos,
                extraLoadFlow = extraLoadFlow.toList().merge(),
            )
        }
    }
}