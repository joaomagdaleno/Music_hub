package com.joaomagdaleno.music_hub.extensions.builtin.internal

import com.joaomagdaleno.music_hub.BuildConfig
import com.joaomagdaleno.music_hub.common.clients.AlbumClient
import com.joaomagdaleno.music_hub.common.clients.ArtistClient
import com.joaomagdaleno.music_hub.common.clients.ExtensionClient
import com.joaomagdaleno.music_hub.common.clients.HomeFeedClient
import com.joaomagdaleno.music_hub.common.clients.LyricsClient
import com.joaomagdaleno.music_hub.common.clients.SearchFeedClient
import com.joaomagdaleno.music_hub.common.clients.TrackClient
import com.joaomagdaleno.music_hub.common.models.*
import com.joaomagdaleno.music_hub.common.models.Streamable.Media.Companion.toServerMedia
import com.joaomagdaleno.music_hub.common.helpers.PagedData
import com.joaomagdaleno.music_hub.common.settings.Setting
import com.joaomagdaleno.music_hub.common.settings.SettingCategory
import com.joaomagdaleno.music_hub.common.settings.SettingSwitch
import com.joaomagdaleno.music_hub.common.settings.SettingTextInput
import com.joaomagdaleno.music_hub.common.settings.Settings

/**
 * Built-in extension that provides internal music sources.
 * This represents the "Fake Extension" pattern to transition to a built-in logic model.
 */
class InternalMusicSource : ExtensionClient, SearchFeedClient, TrackClient, AlbumClient, ArtistClient, HomeFeedClient, LyricsClient {

    companion object {
        const val INTERNAL_ID = "internal_source"
        const val PREF_QUALITY = "pref_high_quality"
        const val PREF_REGION = "pref_trending_region"
        val metadata = Metadata(
            className = "com.joaomagdaleno.music_hub.extensions.builtin.internal.InternalMusicSource",
            path = "",
            importType = ImportType.BuiltIn,
            type = ExtensionType.MUSIC,
            id = INTERNAL_ID,
            name = "Music Hub Base",
            version = "v${BuildConfig.VERSION_CODE}",
            description = "Official built-in source for Music Hub",
            author = "João Magdaleno",
            isEnabled = true
        )
    }

    private lateinit var settings: Settings

    // --- ExtensionClient ---
    override suspend fun onInitialize() {
        // Initialization logic here
    }

    override suspend fun onExtensionSelected() {
        // Selection logic here
    }

    // --- SettingsProvider ---
    override suspend fun getSettingItems(): List<Setting> = listOf(
        SettingCategory(
            title = "Home Content",
            key = "home_group",
            items = listOf(
                SettingTextInput(
                    title = "Trending Region",
                    key = PREF_REGION,
                    summary = "Country code for YouTube trends (e.g. BR, US, PT)",
                    defaultValue = "BR"
                )
            )
        ),
        SettingCategory(
            title = "Audio Quality",
            key = "audio_group",
            items = listOf(
                SettingSwitch(
                    title = "Force High Quality (FLAC/320)",
                    key = PREF_QUALITY,
                    summary = "Prioritize lossless sources whenever available",
                    defaultValue = true
                )
            )
        )
    )

    override fun setSettings(settings: Settings) {
        this.settings = settings
    }

    private val slavArt = SlavArtApi()
    private val piped = PipedApi()
    private val lrcLib = LrcLibApi()

    // --- SearchFeedClient ---
    override suspend fun loadSearchFeed(query: String): Feed<Shelf> {
        // 1. Try SlavArt first (High Quality)
        val slavArtResults = try {
            slavArt.search(query)
        } catch (e: Exception) {
            emptyList()
        }

        if (slavArtResults.isNotEmpty()) {
            val tracks = slavArtResults.map { result ->
                Track(
                    id = result.id,
                    title = result.title,
                    album = result.album?.let { Album(id = "slavart_album:${result.id}", title = it, artists = listOf()) },
                    artists = listOf(Artist(id = "slavart_artist:${result.artist}", name = result.artist)),
                    cover = result.thumbnail?.let { ImageHolder.NetworkRequestImageHolder(NetworkRequest(it), false) },
                    isPlayable = Track.Playable.Yes,
                    duration = result.duration?.toLong()?.times(1000), // ms
                    extras = mapOf(
                        "source_url" to result.url,
                        "quality" to (result.quality ?: if (settings.getBoolean(PREF_QUALITY) != false) "FLAC" else "320kbps"),
                        "source" to "slavart"
                    )
                )
            }

            val shelf = Shelf.Lists.Tracks(
                id = "slavart_results",
                title = "SlavArt Results",
                list = tracks
            )

            return Feed(tabs = emptyList(), getPagedData = { Feed.Data(PagedData.Single { listOf(shelf) }) })
        }

        // 2. Try YouTube Cascade
        val youtubeTracks = try {
            youtubeCascadeSearch(query)
        } catch (e: Exception) {
            emptyList()
        }

        val shelfTitle = if (youtubeTracks.isNotEmpty()) "YouTube Results" else "No results found"
        val shelf = Shelf.Lists.Tracks(
            id = "youtube_results",
            title = shelfTitle,
            list = youtubeTracks
        )

        return Feed(tabs = emptyList(), getPagedData = { Feed.Data(PagedData.Single { listOf(shelf) }) })
    }

    private suspend fun youtubeCascadeSearch(query: String): List<Track> {
        // Attempt search with different terms for better results
        val searchTerms = listOf(query, "$query topic", "$query official audio")
        val allResults = mutableListOf<PipedSearchResult>()
        val seenIds = mutableSetOf<String>()

        for (term in searchTerms) {
            val results = piped.search(term)
            for (res in results) {
                val videoId = res.url.substringAfter("v=", "")
                if (videoId.isNotBlank() && videoId !in seenIds) {
                    // Filter: Ignore short clips (< 60s)
                    if (res.duration != null && res.duration < 60) continue
                    
                    allResults.add(res)
                    seenIds.add(videoId)
                }
            }
            if (allResults.size >= 10) break
        }

        // Prioritization logic: Topic > Official > Others
        val topicResults = allResults.filter { it.uploaderName?.endsWith(" - Topic") == true }
        val officialResults = allResults.filter { it.title?.contains("Official", ignoreCase = true) == true && it !in topicResults }
        val remainingResults = allResults.filter { it !in topicResults && it !in officialResults }

        val finalResults = (topicResults + officialResults + remainingResults).take(15)

        return finalResults.map { res ->
            val videoId = res.url.substringAfter("v=", "")
            Track(
                id = videoId,
                title = res.title ?: "Unknown",
                artists = listOf(Artist(id = res.uploaderUrl?.substringAfterLast("/")?.let { "youtube_channel:$it" } ?: "unknown", name = res.uploaderName ?: "Unknown")),
                cover = res.thumbnail?.let { ImageHolder.NetworkRequestImageHolder(NetworkRequest(it), false) },
                duration = res.duration?.times(1000),
                isPlayable = Track.Playable.Yes,
                extras = mapOf(
                    "video_id" to videoId,
                    "source" to "youtube"
                )
            )
        }
    }

    // --- TrackClient ---
    override suspend fun loadTrack(track: Track, isDownload: Boolean): Track {
        return track
    }

    override suspend fun loadStreamableMedia(streamable: Streamable, isDownload: Boolean): Streamable.Media {
        val source = streamable.extras["source"] ?: "slavart"
        
        val url = if (source == "youtube") {
            val videoId = streamable.extras["video_id"] ?: ""
            piped.getStreamUrl(videoId) ?: ""
        } else {
            val trackUrl = streamable.extras["source_url"] ?: ""
            if (trackUrl.contains(".slavart-api.")) {
                trackUrl
            } else {
                slavArt.getDownloadUrl(trackUrl) ?: ""
            }
        }

        return url.toServerMedia()
    }

    override suspend fun loadFeed(track: Track): Feed<Shelf>? = null

    // --- AlbumClient ---
    override suspend fun loadAlbum(album: Album): Album = album

    override suspend fun loadTracks(album: Album): Feed<Track>? {
        if (album.id.startsWith("slavart_album:")) {
            val realId = album.id.removePrefix("slavart_album:")
            val results = slavArt.getAlbumTracks(realId)
            val tracks = results.map { result ->
                Track(
                    id = result.id,
                    title = result.title,
                    album = album,
                    artists = listOf(Artist(id = "slavart_artist:${result.artist}", name = result.artist)),
                    cover = result.thumbnail?.let { ImageHolder.NetworkRequestImageHolder(NetworkRequest(it), false) },
                    isPlayable = Track.Playable.Yes,
                    duration = result.duration?.toLong()?.times(1000),
                    extras = mapOf(
                        "source_url" to result.url,
                        "quality" to (result.quality ?: "FLAC"),
                        "source" to "slavart"
                    )
                )
            }
            return Feed(tabs = emptyList(), getPagedData = { Feed.Data(PagedData.Single { tracks }) })
        } else if (album.id.startsWith("youtube_playlist:")) {
            val realId = album.id.removePrefix("youtube_playlist:")
            val results = piped.getPlaylistTracks(realId)
            val tracks = results.map { res ->
                val videoId = res.url.substringAfter("v=", "")
                Track(
                    id = videoId,
                    title = res.title ?: "Unknown",
                    artists = listOf(Artist(id = res.uploaderUrl?.substringAfterLast("/")?.let { "youtube_channel:$it" } ?: "unknown", name = res.uploaderName ?: "Unknown")),
                    cover = res.thumbnail?.let { ImageHolder.NetworkRequestImageHolder(NetworkRequest(it), false) },
                    duration = res.duration?.times(1000),
                    isPlayable = Track.Playable.Yes,
                    extras = mapOf(
                        "video_id" to videoId,
                        "source" to "youtube"
                    )
                )
            }
            return Feed(tabs = emptyList(), getPagedData = { Feed.Data(PagedData.Single { tracks }) })
        }
        return null
    }

    override suspend fun loadFeed(album: Album): Feed<Shelf>? = null

    // --- ArtistClient ---
    override suspend fun loadArtist(artist: Artist): Artist = artist

    override suspend fun loadFeed(artist: Artist): Feed<Shelf> {
        val shelves = mutableListOf<Shelf>()
        
        if (artist.id.startsWith("slavart_artist:")) {
            val realId = artist.id.removePrefix("slavart_artist:")
            val info = slavArt.getArtistInfo(realId)

            if (info.topTracks.isNotEmpty()) {
                val tracks = info.topTracks.map { result ->
                    Track(
                        id = result.id,
                        title = result.title,
                        album = result.album?.let { Album(id = "slavart_album:${result.id}", title = it, artists = listOf()) },
                        artists = listOf(artist),
                        cover = result.thumbnail?.let { ImageHolder.NetworkRequestImageHolder(NetworkRequest(it), false) },
                        isPlayable = Track.Playable.Yes,
                        duration = result.duration?.toLong()?.times(1000),
                        extras = mapOf(
                            "source_url" to result.url,
                            "quality" to (result.quality ?: "FLAC"),
                            "source" to "slavart"
                        )
                    )
                }
                shelves.add(Shelf.Lists.Tracks("popular_tracks", "Popular Tracks", tracks))
            }

            if (info.albums.isNotEmpty()) {
                val albums = info.albums.map { result ->
                    Album(
                        id = "slavart_album:${result.id}",
                        title = result.title ?: result.name ?: "Unknown",
                        cover = result.cover?.let { ImageHolder.NetworkRequestImageHolder(NetworkRequest(it), false) },
                        artists = listOf(artist)
                    )
                }
                shelves.add(Shelf.Lists.Items("albums", "Albums", albums, type = Shelf.Lists.Type.Grid))
            }
        } else if (artist.id.startsWith("youtube_channel:")) {
            val realId = artist.id.removePrefix("youtube_channel:")
            val items = piped.getChannelItems(realId)
            
            val tracks = items.map { res ->
                val videoId = res.url.substringAfter("v=", "")
                Track(
                    id = videoId,
                    title = res.title ?: "Unknown",
                    artists = listOf(artist),
                    cover = res.thumbnail?.let { ImageHolder.NetworkRequestImageHolder(NetworkRequest(it), false) },
                    duration = res.duration?.times(1000),
                    isPlayable = Track.Playable.Yes,
                    extras = mapOf(
                        "video_id" to videoId,
                        "source" to "youtube"
                    )
                )
            }
            if (tracks.isNotEmpty()) {
                shelves.add(Shelf.Lists.Tracks("latest_uploads", "Latest Uploads", tracks))
            }
        }

        return Feed(tabs = emptyList(), getPagedData = { Feed.Data(PagedData.Single { shelves }) })
    }

    // --- HomeFeedClient ---
    override suspend fun loadHomeFeed(): Feed<Shelf> = Feed(
        listOf(
            Tab("trending", "Trending"),
            Tab("discovery", "Discovery")
        )
    ) { tab ->
        val shelves = mutableListOf<Shelf>()
        when (tab?.id) {
            "trending" -> {
                val region = settings.getString(PREF_REGION) ?: "BR"
                val trending = try { piped.getTrending(region) } catch (e: Exception) { emptyList() }
                if (trending.isNotEmpty()) {
                    val tracks = trending.map { res ->
                        val videoId = res.url.substringAfter("v=", "")
                        Track(
                            id = videoId,
                            title = res.title ?: "Unknown",
                            artists = listOf(Artist(id = res.uploaderUrl?.substringAfterLast("/")?.let { "youtube_channel:$it" } ?: "unknown", name = res.uploaderName ?: "Unknown")),
                            cover = res.thumbnail?.let { ImageHolder.NetworkRequestImageHolder(NetworkRequest(it), false) },
                            duration = res.duration?.times(1000),
                            isPlayable = Track.Playable.Yes,
                            extras = mapOf(
                                "video_id" to videoId,
                                "source" to "youtube"
                            )
                        )
                    }
                    shelves.add(Shelf.Lists.Tracks("trending_youtube", "Popular on YouTube", tracks, type = Shelf.Lists.Type.Grid))
                }
            }
            "discovery" -> {
                val results = try { slavArt.search("top 2024") } catch (e: Exception) { emptyList() }
                if (results.isNotEmpty()) {
                    val tracks = results.map { result ->
                        Track(
                            id = result.id,
                            title = result.title,
                            album = result.album?.let { Album(id = "slavart_album:${result.id}", title = it, artists = listOf()) },
                            artists = listOf(Artist(id = "slavart_artist:${result.artist}", name = result.artist)),
                            cover = result.thumbnail?.let { ImageHolder.NetworkRequestImageHolder(NetworkRequest(it), false) },
                            isPlayable = Track.Playable.Yes,
                            duration = result.duration?.toLong()?.times(1000),
                            extras = mapOf(
                                "source_url" to result.url,
                                "quality" to (result.quality ?: "FLAC"),
                                "source" to "slavart"
                            )
                        )
                    }
                    shelves.add(Shelf.Lists.Tracks("discovery_slavart", "High Quality Picks", tracks, type = Shelf.Lists.Type.Grid))
                }
            }
        }
        Feed.Data(PagedData.Single { shelves })
    }

    // --- LyricsClient ---
    override suspend fun searchTrackLyrics(clientId: String, track: Track): Feed<Lyrics> {
        val title = track.title
        val artist = track.artists.firstOrNull()?.name ?: "Unknown"
        val duration = track.duration?.div(1000)?.toInt()

        val rawLrc = lrcLib.getLyrics(title, artist, duration)
        if (rawLrc == null) return Feed(tabs = emptyList(), getPagedData = { Feed.Data(PagedData.Single { emptyList<Lyrics>() }) })

        val lyrics = Lyrics(
            id = "lrclib:${track.id}",
            title = title,
            subtitle = artist,
            lyrics = lrcLib.parseLrc(rawLrc)
        )
        return Feed(tabs = emptyList(), getPagedData = { Feed.Data(PagedData.Single { listOf(lyrics) }) })
    }

    override suspend fun loadLyrics(lyrics: Lyrics): Lyrics {
        // Since we fetch the full LRC in searchTrackLyrics, nothing more to load
        return lyrics
    }
}
