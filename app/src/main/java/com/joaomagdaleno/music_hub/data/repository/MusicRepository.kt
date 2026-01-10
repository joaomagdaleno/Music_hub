package com.joaomagdaleno.music_hub.data.repository

import com.joaomagdaleno.music_hub.common.models.Feed.Companion.pagedDataOfFirst
import com.joaomagdaleno.music_hub.common.models.Shelf
import com.joaomagdaleno.music_hub.common.models.Streamable
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.data.providers.LocalSource
import com.joaomagdaleno.music_hub.data.providers.YoutubeSource
import com.joaomagdaleno.music_hub.data.providers.LrcLibApi
import com.joaomagdaleno.music_hub.data.providers.PipedApi
import com.joaomagdaleno.music_hub.data.providers.SlavArtApi
import com.joaomagdaleno.music_hub.common.models.NetworkRequest
import com.joaomagdaleno.music_hub.common.models.ImageHolder
import com.joaomagdaleno.music_hub.common.models.Feed
import com.joaomagdaleno.music_hub.common.helpers.PagedData
import com.joaomagdaleno.music_hub.common.models.Tab
import com.joaomagdaleno.music_hub.common.models.Album
import com.joaomagdaleno.music_hub.common.models.Artist
import com.joaomagdaleno.music_hub.common.models.Playlist
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.utils.FileLogger
import kotlinx.coroutines.*

class MusicRepository(
    private val app: com.joaomagdaleno.music_hub.di.App,
    private val localProvider: LocalSource,
    private val database: com.joaomagdaleno.music_hub.data.db.MusicDatabase,
) {
    private val piped = PipedApi()
    private val slavArt = SlavArtApi()
    private val lrcLib = LrcLibApi()
    private val youtubeProvider = YoutubeSource() // Integrated provider

    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        FileLogger.log("MusicRepository", "search() called with query='$query'")
        val results = mutableListOf<Track>()
        
        // 1. Local Search
        try {
            results.addAll(localProvider.search(query))
        } catch (e: Exception) { 
            FileLogger.log("MusicRepository", "Local search failed: ${e.message}")
        }

        // 2. Remote Search (SlavArt + YouTube) - Parallel
        val slavArtDef = async {
            try {
                slavArt.search(query).map { result ->
                    Track(
                        id = result.id,
                        title = result.title,
                        origin = "SLAVART",
                        album = result.album?.let { Album(id = "slavart_album:${result.id}", title = it, artists = listOf()) },
                        artists = listOf(Artist(id = "slavart_artist:${result.artist}", name = result.artist)),
                        cover = result.thumbnail?.let { ImageHolder.NetworkRequestImageHolder(NetworkRequest(it), false) },
                        isPlayable = Track.Playable.Yes,
                        duration = result.duration?.toLong()?.times(1000),
                        extras = mapOf("media_url" to result.url, "quality" to (result.quality ?: "FLAC"))
                    )
                }
            } catch (e: Exception) {
                FileLogger.log("MusicRepository", "SlavArt search failed: ${e.message}")
                emptyList<Track>()
            }
        }

        val youtubeDef = async {
            try {
                youtubeProvider.search(query)
            } catch (e: Exception) {
                FileLogger.log("MusicRepository", "YouTube search failed: ${e.message}")
                emptyList<Track>()
            }
        }

        results.addAll(slavArtDef.await())
        results.addAll(youtubeDef.await())

        FileLogger.log("MusicRepository", "search() complete, total results: ${results.size}")
        results
    }

    private val streamCache = mutableMapOf<String, String>()

    suspend fun getStreamUrl(track: Track): String {
        if (streamCache.containsKey(track.id)) {
            return streamCache[track.id]!!
        }

        val url = resolveStreamUrl(track)
        if (url.isNotEmpty()) {
            streamCache[track.id] = url
        }
        return url
    }

    private suspend fun resolveStreamUrl(track: Track): String {
        // 1. Internal Local Check
        if (track.origin == "LOCAL" || track.id.startsWith("content://") || track.originalUrl.startsWith("/")) {
             return localProvider.getStreamUrl(track)
        }

        // 2. Legacy/YouTube Mapping
        if (track.origin == "YOUTUBE" || 
            track.origin.contains("youtube", true) || 
            track.extras.containsKey("video_id")) {
             val videoId = track.extras["video_id"] ?: track.id
             return piped.getStreamUrl(videoId) ?: ""
        }
        
        // 3. SlavArt / Direct URL Fallback
        val url = track.extras["media_url"] ?: track.originalUrl
        if (url.contains(".slavart-api.")) return url
        
        return slavArt.getDownloadUrl(url) ?: ""
    }

    suspend fun getHomeFeed(): List<Shelf> {
        FileLogger.log("MusicRepository", "getHomeFeed start")
        // Combined Internal Home Feed
        val shelves = mutableListOf<Shelf>()
        
        // 0. Local Tracks (Instant)
        FileLogger.log("MusicRepository", "getHomeFeed: Loading local tracks fallback")
        val local = getLibraryFeed()
        if (local.isNotEmpty()) {
            shelves.addAll(local)
        }

        // Trending from YouTube
        try {
            FileLogger.log("MusicRepository", "getHomeFeed: Fetching Piped Trending")
            kotlinx.coroutines.withTimeout(5000L) {
                val trending = piped.getTrending("BR").take(10).map { res ->
                    val videoId = res.url.substringAfter("v=", "")
                    Track(
                        id = videoId,
                        title = res.title ?: "Unknown",
                        origin = "YOUTUBE",
                        artists = listOf(Artist(id = res.uploaderUrl?.substringAfterLast("/")?.let { "youtube_channel:$it" } ?: "unknown", name = res.uploaderName ?: "Unknown")),
                        cover = res.thumbnail?.let { ImageHolder.NetworkRequestImageHolder(NetworkRequest(it), false) },
                        duration = res.duration?.times(1000),
                        isPlayable = Track.Playable.Yes,
                        extras = mapOf("video_id" to videoId)
                    )
                }
                if (trending.isNotEmpty()) {
                    FileLogger.log("MusicRepository", "getHomeFeed: Piped Trending success, count: ${trending.size}")
                    shelves.add(Shelf.Lists.Tracks("trending", "Trending Now", trending, type = Shelf.Lists.Type.Grid))
                }
            }
        } catch (e: Exception) {
            FileLogger.log("MusicRepository", "getHomeFeed: Piped Trending fork failed: ${e.message}", e)
        }

        // Discovery from SlavArt
        try {
            FileLogger.log("MusicRepository", "getHomeFeed: Fetching SlavArt Discovery")
            kotlinx.coroutines.withTimeout(5000L) {
                val discovery = slavArt.search("top 2024").take(10).map { result ->
                    Track(
                        id = result.id,
                        title = result.title,
                        origin = "SLAVART",
                        album = result.album?.let { Album(id = "slavart_album:${result.id}", title = it, artists = listOf()) },
                        artists = listOf(Artist(id = "slavart_artist:${result.artist}", name = result.artist)),
                        cover = result.thumbnail?.let { ImageHolder.NetworkRequestImageHolder(NetworkRequest(it), false) },
                        isPlayable = Track.Playable.Yes,
                        duration = result.duration?.toLong()?.times(1000),
                        extras = mapOf("media_url" to result.url)
                    )
                }
                if (discovery.isNotEmpty()) {
                    FileLogger.log("MusicRepository", "getHomeFeed: SlavArt Discovery success, count: ${discovery.size}")
                    shelves.add(Shelf.Lists.Tracks("discovery", "High Quality Picks", discovery, type = Shelf.Lists.Type.Grid))
                }
            }
        } catch (e: Exception) {
            FileLogger.log("MusicRepository", "getHomeFeed: SlavArt Discovery failed: ${e.message}", e)
        }

        // 3. Static Discovery Categories (Echo Style)
        val categories = listOf(
            Shelf.Category("cat_hits", "Global Hits", backgroundColor = "#FF5722"),
            Shelf.Category("cat_chill", "Chill & Relax", backgroundColor = "#2196F3"),
            Shelf.Category("cat_focus", "Focus & Work", backgroundColor = "#4CAF50"),
            Shelf.Category("cat_workout", "Workout Boost", backgroundColor = "#E91E63"),
            Shelf.Category("cat_party", "Party Vibes", backgroundColor = "#9C27B0")
        )
        shelves.add(Shelf.Lists.Categories("home_discovery", "Discover by Mood", categories))

        FileLogger.log("MusicRepository", "getHomeFeed complete, total shelves: ${shelves.size}")
        return shelves
    }

    suspend fun getAlbumTracks(albumId: String): List<Track> {
        FileLogger.log("MusicRepository", "getAlbumTracks: $albumId")
        return when {
            albumId.startsWith("slavart_album:") -> {
                val realId = albumId.removePrefix("slavart_album:")
                slavArt.getAlbumTracks(realId).map { result ->
                    Track(
                        id = result.id,
                        title = result.title,
                        origin = "SLAVART",
                        artists = listOf(Artist(id = "slavart_artist:${result.artist}", name = result.artist)),
                        cover = result.thumbnail?.let { ImageHolder.NetworkRequestImageHolder(NetworkRequest(it), false) },
                        isPlayable = Track.Playable.Yes,
                        duration = result.duration?.toLong()?.times(1000),
                        extras = mapOf("media_url" to result.url)
                    )
                }
            }
            albumId.startsWith("youtube_playlist:") -> {
                youtubeProvider.getAlbumTracks(albumId)
            }
            else -> emptyList()
        }
    }

    suspend fun getArtistTracks(artistId: String): List<Track> {
        FileLogger.log("MusicRepository", "getArtistTracks: $artistId")
        return when {
            artistId.startsWith("youtube_channel:") -> {
                youtubeProvider.getArtistTracks(artistId)
            }
            else -> emptyList()
        }
    }

    suspend fun getTrack(id: String): Track? {
        FileLogger.log("MusicRepository", "getTrack: $id")
        return youtubeProvider.getTrack(id)
    }

    suspend fun getArtistAlbums(artistId: String): List<com.joaomagdaleno.music_hub.common.models.Album> {
        FileLogger.log("MusicRepository", "getArtistAlbums: $artistId")
        if (artistId.startsWith("youtube_channel:")) {
            val channelId = artistId.removePrefix("youtube_channel:")
            try {
                // Channel items can include playlists (albums)
                val items = piped.getChannelItems(channelId)
                return items.filter { it.type == "playlist" }.map { res ->
                    com.joaomagdaleno.music_hub.common.models.Album(
                        id = "youtube_playlist:${res.url.substringAfterLast("/")}",
                        title = res.title ?: "Unknown Album",
                        artists = listOf(Artist(id = artistId, name = res.uploaderName ?: "Unknown")),
                        cover = res.thumbnail?.let { ImageHolder.NetworkRequestImageHolder(NetworkRequest(it), false) }
                    )
                }
            } catch (e: Exception) {
                FileLogger.log("MusicRepository", "getArtistAlbums failed: ${e.message}")
            }
        }
        return emptyList()
    }

    suspend fun getRadio(trackId: String): List<Track> {
        FileLogger.log("MusicRepository", "getRadio: $trackId")
        return when {
            !trackId.all { it.isDigit() } -> youtubeProvider.getRadio(trackId)
            else -> emptyList()
        }
    }
    
    suspend fun getLyrics(track: Track): com.joaomagdaleno.music_hub.common.models.Lyrics.Lyric? {
        FileLogger.log("MusicRepository", "getLyrics for: ${track.title} - ${track.artists.firstOrNull()?.name}")
        val title = track.title
        val artist = track.artists.firstOrNull()?.name ?: "Unknown"
        val duration = (track.duration?.div(1000))?.toInt()
        
        val lrc = lrcLib.getLyrics(title, artist, duration)
        return lrc?.let { lrcLib.parseLrc(it) }
    }

    suspend fun getAlbum(id: String): com.joaomagdaleno.music_hub.common.models.Album {
        return youtubeProvider.getAlbum(id) ?: com.joaomagdaleno.music_hub.common.models.Album(id = id, title = "Unknown")
    }

    suspend fun getArtist(id: String): com.joaomagdaleno.music_hub.common.models.Artist {
        return youtubeProvider.getArtist(id) ?: com.joaomagdaleno.music_hub.common.models.Artist(id = id, name = "Unknown")
    }

    suspend fun getPlaylist(id: String): com.joaomagdaleno.music_hub.common.models.Playlist? {
        val fromDb = database.getPlaylistByActualId(id) ?: database.getPlaylist(id.toLongOrNull() ?: -1)
        if (fromDb != null) return fromDb
        return null
    }

    suspend fun isLiked(track: Track): Boolean {
        return database.isLiked(track)
    }

    suspend fun toggleLike(track: Track) {
        val liked = isLiked(track)
        val playlist = database.getLikedPlaylist(app.context)
        if (liked) {
            database.removeTracksFromPlaylist(playlist, listOf(track), listOf(0))
        } else {
            database.addTracksToPlaylist(playlist, 0, listOf(track))
        }
    }

    suspend fun isSaved(item: EchoMediaItem): Boolean {
        return database.isSaved(item)
    }

    suspend fun toggleSave(item: EchoMediaItem) {
        if (isSaved(item)) {
            database.deleteSaved(item)
        } else {
            database.save(item)
        }
    }

    suspend fun getLibraryFeed(): List<Shelf> {
        FileLogger.log("MusicRepository", "getLibraryFeed requested")
        val localTracks = localProvider.getAllTracks()
        return if (localTracks.isNotEmpty()) {
            listOf(Shelf.Lists.Tracks("local_tracks", "Local Music", localTracks))
        } else {
            emptyList()
        }
    }

    suspend fun getPlaylistTracks(playlistId: String): List<Track> {
        return youtubeProvider.getPlaylistTracks(playlistId)
    }

    suspend fun getFeed(id: String): List<Shelf> {
        return when {
            id == "home" -> getHomeFeed()
            id == "library" -> getLibraryFeed()
            id.startsWith("artist-") && id.endsWith("-albums") -> {
                val artistId = id.removePrefix("artist-").removeSuffix("-albums")
                val albums = getArtistAlbums(artistId)
                listOf(Shelf.Lists.Items(id, "Albums", albums, type = Shelf.Lists.Type.Grid))
            }
            id.startsWith("artist-") && id.endsWith("-tracks") -> {
                val artistId = id.removePrefix("artist-").removeSuffix("-tracks")
                val tracks = getArtistTracks(artistId)
                listOf(Shelf.Lists.Tracks(id, "Tracks", tracks))
            }
            else -> emptyList()
        }
    }

    suspend fun getSearchSuggestions(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        return piped.getSuggestions(query)
    }
}
