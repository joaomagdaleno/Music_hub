package com.joaomagdaleno.music_hub.ui.compose.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.lerp
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlin.math.abs

@Composable
fun ShelfRow(
    shelf: Shelf.Lists<out Any>,
    onItemClick: (EchoMediaItem) -> Unit
) {
    val scrollState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shelf.title,
                    style = MaterialTheme.typography.titleLarge
                )
                shelf.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (shelf.more != null) {
                IconButton(onClick = { /* TODO: Open More */ }) {
                    Icon(painterResource(R.drawable.ic_arrow_forward_24dp), contentDescription = "More")
                }
            }
        }

        if (shelf.type == Shelf.Lists.Type.Grid) {
            LazyHorizontalGrid(
                rows = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(240.dp) 
            ) {
                items(shelf.list) { item ->
                    when (item) {
                        is EchoMediaItem -> MediaItemCard(item = item, onClick = onItemClick)
                        is Shelf.Category -> CategoryCard(category = item, onClick = { /* TODO */ })
                    }
                }
            }
        } else {
            LazyRow(
                state = scrollState,
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                if (shelf is Shelf.Lists.Tracks) {
                    val chunks = shelf.list.chunked(3)
                    itemsIndexed(chunks) { index, chunk ->
                        Column(
                            modifier = Modifier
                                .width(320.dp)
                                .graphicsLayer {
                                    val layoutInfo = scrollState.layoutInfo
                                    val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                                    if (itemInfo != null) {
                                        val center = layoutInfo.viewportEndOffset / 2f
                                        val itemCenter = itemInfo.offset + itemInfo.size / 2f
                                        val dist = abs(center - itemCenter)
                                        val fraction = (dist / center).coerceIn(0f, 1f)
                                        
                                        scaleX = 1f - fraction * 0.05f
                                        scaleY = 1f - fraction * 0.05f
                                        rotationY = (center - itemCenter) / center * 10f
                                    }
                                }
                        ) {
                            chunk.forEach { track ->
                                TrackItem(track = track, onClick = { onItemClick(track) })
                            }
                        }
                    }
                } else {
                    itemsIndexed(shelf.list) { index, item ->
                        Box(
                            modifier = Modifier.graphicsLayer {
                                val layoutInfo = scrollState.layoutInfo
                                val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                                if (itemInfo != null) {
                                    val center = layoutInfo.viewportEndOffset / 2f
                                    val itemCenter = itemInfo.offset + itemInfo.size / 2f
                                    val dist = abs(center - itemCenter)
                                    val fraction = (dist / center).coerceIn(0f, 1f)
                                    
                                    scaleX = 1f - fraction * 0.1f
                                    scaleY = 1f - fraction * 0.1f
                                    rotationY = (center - itemCenter) / center * 15f
                                }
                            }
                        ) {
                            when (item) {
                                is com.joaomagdaleno.music_hub.common.models.Track -> {
                                    TrackItem(track = item, onClick = { onItemClick(item) })
                                }
                                is EchoMediaItem -> {
                                    MediaItemCard(item = item, onClick = onItemClick)
                                }
                                is Shelf.Category -> {
                                    CategoryCard(category = item, onClick = { /* TODO */ })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryShelf(
    shelf: Shelf.Lists.Categories,
    onClick: (Shelf.Category) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = shelf.title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            items(shelf.list) { category ->
                CategoryCard(category = category, onClick = { onClick(category) })
            }
        }
    }
}
