package com.joaomagdaleno.music_hub.data.providers

import com.joaomagdaleno.music_hub.common.models.Album
import com.joaomagdaleno.music_hub.common.models.Artist
import com.joaomagdaleno.music_hub.common.models.ImageHolder
import com.joaomagdaleno.music_hub.common.models.NetworkRequest
import com.joaomagdaleno.music_hub.common.models.Shelf
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.data.providers.PipedApi
import com.joaomagdaleno.music_hub.data.providers.PipedSearchResult

class YoutubeSource : MusicSource {
    override val name = "YOUTUBE"
    private val api = PipedApi()

    override suspend fun search(query: String): List<Track> {
        val results = api.search(query)
        return results.map { it.toTrack() }
    }

    override suspend fun getStreamUrl(track: Track): String {
        return api.getStreamUrl(track.id) ?: throw Exception("Stream not found")
    }

    override suspend fun getRecommendations(): List<Track> {
        return getHomeFeed().filterIsInstance<Shelf.Lists.Tracks>().flatMap { it.list }
    }

    override suspend fun getHomeFeed(): List<Shelf> {
        val shelves = mutableListOf<Shelf>()
        try {
            val trending = api.getTrending("BR")
            if (trending.isNotEmpty()) {
                val tracks = trending.map { it.toTrack() }
                shelves.add(Shelf.Lists.Tracks("trending_youtube", "Trending on YouTube", tracks))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return shelves
    }

    override suspend fun getAlbumTracks(albumId: String): List<Track> {
        val results = api.getPlaylistTracks(albumId.removePrefix("youtube_playlist:"))
        return results.map { it.toTrack() }
    }

    override suspend fun getArtistTracks(artistId: String): List<Track> {
        val items = api.getChannelItems(artistId.removePrefix("youtube_channel:"))
        return items.map { it.toTrack() }
    }

    override suspend fun getTrack(trackId: String): Track? {
        return try {
            val result = api.getStream(trackId) ?: return null
            Track(
                id = trackId,
                title = result.title ?: "Unknown",
                artists = listOf(Artist(id = "youtube_channel:${result.uploaderUrl?.substringAfterLast("/")}", name = result.uploader ?: "Unknown")),
                cover = result.thumbnail?.let { ImageHolder.NetworkRequestImageHolder(NetworkRequest(it), false) },
                duration = result.duration,
                origin = name,
                originalUrl = "https://youtube.com/watch?v=$trackId"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    override suspend fun getRadio(trackId: String): List<Track> {
        return try {
            val result = api.getStream(trackId)
            result?.relatedStreams?.map { it.toTrack() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getPlaylistTracks(playlistId: String): List<Track> {
        return try {
            val items = api.getPlaylistItems(playlistId)
            items.map { it.toTrack() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getAlbum(albumId: String): Album? {
        // Piped doesn't have a specific "Album" endpoint, but we can treat a playlist as an album
        val id = albumId.removePrefix("youtube_playlist:")
        // We could fetch playlist details here if Piped returned them (it does in getPlaylistItems usually)
        return Album(id = albumId, title = "YouTube Album") 
    }

    suspend fun getArtist(artistId: String): Artist? {
        val id = artistId.removePrefix("youtube_channel:")
        // Fetching channel info
        return Artist(id = artistId, name = "YouTube Artist")
    }

    private fun PipedSearchResult.toTrack(): Track {
        val videoId = url.substringAfter("v=", "")
        return Track(
            id = videoId,
            title = title ?: "Unknown",
            origin = name,
            originalUrl = url,
            artists = listOf(Artist(id = "youtube_channel:${uploaderUrl?.substringAfterLast("/")}", name = uploaderName ?: "Unknown")),
            cover = thumbnail?.let { ImageHolder.NetworkRequestImageHolder(NetworkRequest(it), false) },
            duration = duration?.times(1000),
            isPlayable = Track.Playable.Yes,
            extras = mapOf("video_id" to videoId)
        )
    }
}
