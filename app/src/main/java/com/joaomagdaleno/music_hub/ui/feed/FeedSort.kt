package com.joaomagdaleno.music_hub.ui.feed

import android.content.Context
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.Album
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.Playlist
import com.joaomagdaleno.music_hub.common.models.Shelf
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.ui.media.MediaHeaderAdapter
import kotlinx.serialization.Serializable

fun getSorts(data: List<Shelf>): List<FeedSort> {
    return FeedSort.entries.filter { it.shouldBeVisible(data) != null }
}

object FeedSortUtils {
    fun getTitle(shelf: Shelf) = when (shelf) {
        is Shelf.Category -> shelf.title
        is Shelf.Item -> shelf.media.title
        is Shelf.Lists<*> -> null
    }

    fun getSubtitle(shelf: Shelf) = when (shelf) {
        is Shelf.Category -> shelf.subtitle
        is Shelf.Item -> shelf.media.subtitle
        is Shelf.Lists<*> -> null
    }

    fun getDuration(shelf: Shelf) = when (shelf) {
        is Shelf.Category -> null
        is Shelf.Item -> when (val item = shelf.media) {
            is Track -> item.duration
            is Album -> item.duration
            is Playlist -> item.duration
            else -> null
        }

        is Shelf.Lists<*> -> null
    }

    fun getDate(shelf: Shelf) = when (shelf) {
        is Shelf.Category -> null
        is Shelf.Item -> when (val item = shelf.media) {
            is Track -> item.releaseDate
            is Album -> item.releaseDate
            is Playlist -> item.creationDate
            else -> null
        }

        is Shelf.Lists<*> -> null
    }

    fun getDateAdded(shelf: Shelf) = when (shelf) {
        is Shelf.Category -> null
        is Shelf.Item -> when (val item = shelf.media) {
            is Track -> item.playlistAddedDate
            else -> null
        }

        is Shelf.Lists<*> -> null
    }

    fun getArtists(shelf: Shelf) = when (shelf) {
        is Shelf.Category -> null
        is Shelf.Item -> when (val item = shelf.media) {
            is Track -> item.artists
            is EchoMediaItem.Lists -> item.artists
            else -> null
        }

        is Shelf.Lists<*> -> null
    }

    fun getAlbum(shelf: Shelf) = when (shelf) {
        is Shelf.Category -> null
        is Shelf.Item -> when (val item = shelf.media) {
            is Track -> item.album
            else -> null
        }

        is Shelf.Lists<*> -> null
    }

    fun getTrackCount(shelf: Shelf) = when (shelf) {
        is Shelf.Category -> null
        is Shelf.Item -> when (val item = shelf.media) {
            is EchoMediaItem.Lists -> item.trackCount
            else -> null
        }

        is Shelf.Lists<*> -> null
    }

    fun ifListItem(shelf: Shelf): EchoMediaItem.Lists? = when (shelf) {
        is Shelf.Category -> null
        is Shelf.Item -> when (shelf.media) {
            is EchoMediaItem.Lists -> shelf.media as EchoMediaItem.Lists
            else -> null
        }

        is Shelf.Lists<*> -> null
    }

    fun copyShelf(shelf: Shelf, subtitle: String) = when (shelf) {
        is Shelf.Category -> shelf.copy(subtitle = subtitle)
        is Shelf.Item -> shelf.copy(media = shelf.media.copyMediaItem(subtitle = subtitle))
        is Shelf.Lists<*> -> throw IllegalStateException()
    }
}

enum class FeedSort(
    val title: Int,
    val sorter: Context.(List<Shelf>) -> List<Shelf>
) {
    Title(R.string.sort_title, { list -> list.sortedBy { FeedSortUtils.getTitle(it) } }),
    Subtitle(R.string.sort_subtitle, { list -> list.sortedBy { FeedSortUtils.getSubtitle(it) } }),
    Duration(R.string.sort_duration, { list ->
        list.sortedBy { FeedSortUtils.getDuration(it) }
    }),
    Date(R.string.sort_date, { list ->
        list.sortedBy { FeedSortUtils.getDate(it) }
            .filter { FeedSortUtils.getDate(it) != null }
            .map { FeedSortUtils.copyShelf(it, FeedSortUtils.getDate(it).toString()) }
    }),
    DateAdded(R.string.sort_date_added, { list ->
        list.sortedBy { FeedSortUtils.getDate(it) }
            .filter { FeedSortUtils.getDate(it) != null }
            .map { FeedSortUtils.copyShelf(it, FeedSortUtils.getDate(it).toString()) }
    }),
    Artists(R.string.artists, { list ->
        list.flatMap { shelf ->
            FeedSortUtils.getArtists(shelf).orEmpty().map {
                it to FeedSortUtils.copyShelf(shelf, it.name)
            }
        }.sortedBy { it.first.name.lowercase() }.groupBy { it.first.id }
            .values.flatMap { it -> it.map { it.second } }
    }),
    Album(R.string.albums, { list ->
        list.flatMap { shelf ->
            FeedSortUtils.getAlbum(shelf)?.let { album ->
                listOf(FeedSortUtils.copyShelf(shelf, album.title))
            } ?: emptyList()
        }.sortedBy { FeedSortUtils.getSubtitle(it) }
    }),
    Tracks(R.string.tracks, { list ->
        list.sortedBy { FeedSortUtils.getTrackCount(it) }
            .filter { FeedSortUtils.getTrackCount(it) != null }
            .map { FeedSortUtils.copyShelf(it, MediaHeaderAdapter.toTrackString(FeedSortUtils.ifListItem(it)!!, this) ?: "???") }
    })
    ;

    fun shouldBeVisible(data: List<Shelf>): FeedSort? {
        val take = when (this) {
            Title -> data.any { FeedSortUtils.getTitle(it)?.isNotEmpty() ?: false }
            Subtitle -> data.any { FeedSortUtils.getSubtitle(it)?.isNotEmpty() ?: false }
            Date -> data.any { FeedSortUtils.getDate(it) != null }
            DateAdded -> data.any { FeedSortUtils.getDateAdded(it) != null }
            Duration -> data.any { FeedSortUtils.getDuration(it) != null }
            Artists -> data.any { FeedSortUtils.getArtists(it)?.isNotEmpty() ?: false }
            Album -> data.mapNotNull { FeedSortUtils.getAlbum(it) }.toSet().size > 1
            Tracks -> data.any { FeedSortUtils.getTrackCount(it) != null }
        }
        return if (take) this else null
    }

    @Serializable
    data class State(
        val feedSort: FeedSort? = null,
        val reversed: Boolean = false,
        val save: Boolean = false,
    )
}