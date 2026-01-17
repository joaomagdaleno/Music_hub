package com.joaomagdaleno.music_hub.ui.compose.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.Feed
import com.joaomagdaleno.music_hub.common.models.Feed.Buttons.Companion.EMPTY
import com.joaomagdaleno.music_hub.common.models.Feed.Companion.loadAll
import com.joaomagdaleno.music_hub.common.models.Feed.Companion.toFeed
import com.joaomagdaleno.music_hub.common.models.Shelf
import com.joaomagdaleno.music_hub.ui.compose.feed.ShelfRow
import com.joaomagdaleno.music_hub.ui.feed.FeedData
import com.joaomagdaleno.music_hub.ui.feed.FeedViewModel
import com.joaomagdaleno.music_hub.utils.FileLogger
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeed(
    viewModel: FeedViewModel = koinViewModel(),
    onItemClick: (EchoMediaItem) -> Unit
) {
    FileLogger.log("HomeScreen", "HomeFeed composable entered")
    
    val feedData = remember(viewModel) {
        FileLogger.log("HomeScreen", "Creating FeedData for home")
        viewModel.getFeedData(
            id = "home",
            buttons = EMPTY,
            loader = {
                FileLogger.log("HomeScreen", "loader lambda invoked - calling getHomeFeed()")
                val feed = it.getHomeFeed().toFeed()
                FileLogger.log("HomeScreen", "getHomeFeed() returned, converting to FeedData.State")
                FeedData.State("internal", null, feed)
            }
        )
    }

    // Use dataFlow instead of private stateFlow
    val dataState by feedData.dataFlow.collectAsState(initial = null to null)
    val isRefreshing by feedData.isRefreshingFlow.collectAsState(initial = false)

    val pullRefreshState = rememberPullToRefreshState()
    
    var shelves by remember { mutableStateOf(emptyList<Shelf>()) }

    // Load shelves when data changes
    LaunchedEffect(dataState) {
        FileLogger.log("HomeScreen", "LaunchedEffect(dataState) triggered. cached=${dataState.first != null}, loaded=${dataState.second != null}")
        val currentData = dataState.second?.getOrNull() ?: dataState.first?.getOrNull()
        if (currentData != null) {
            FileLogger.log("HomeScreen", "currentData found, loading all shelves from pagedData")
            shelves = currentData.feed.pagedData.loadAll()
            FileLogger.log("HomeScreen", "shelves loaded, count=${shelves.size}")
        } else {
            FileLogger.log("HomeScreen", "currentData is null, no shelves to display")
            dataState.second?.exceptionOrNull()?.let { ex ->
                FileLogger.log("HomeScreen", "dataState.second exception: ${ex.message}", ex)
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { 
            FileLogger.log("HomeScreen", "onRefresh triggered")
            feedData.refresh() 
        },
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        val cached = dataState.first?.getOrNull()
        val loaded = dataState.second?.getOrNull()
        val hasData = cached != null || loaded != null
        
        FileLogger.log("HomeScreen", "Render: hasData=$hasData, isRefreshing=$isRefreshing, shelvesCount=${shelves.size}")

        if (!hasData && isRefreshing) {
            FileLogger.log("HomeScreen", "Showing loading indicator")
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (hasData) {
            FileLogger.log("HomeScreen", "Rendering LazyColumn with ${shelves.size} shelves")
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(shelves) { shelf ->
                    when (shelf) {
                        is Shelf.Lists<*> -> {
                            if (shelf.list.isNotEmpty()) {
                                ShelfRow(
                                    shelf = shelf,
                                    onItemClick = onItemClick
                                )
                            }
                        }
                        else -> {}
                    }
                }
            }
        } else {
            FileLogger.log("HomeScreen", "No data and not refreshing - empty state")
        }
    }
}
