package com.joaomagdaleno.music_hub.extensions.builtin.test

import com.joaomagdaleno.music_hub.common.clients.ArtistClient
import com.joaomagdaleno.music_hub.common.clients.ExtensionClient
import com.joaomagdaleno.music_hub.common.clients.FollowClient
import com.joaomagdaleno.music_hub.common.clients.HideClient
import com.joaomagdaleno.music_hub.common.clients.HomeFeedClient
import com.joaomagdaleno.music_hub.common.clients.LibraryFeedClient
import com.joaomagdaleno.music_hub.common.clients.LikeClient
import com.joaomagdaleno.music_hub.common.clients.LoginClient
import com.joaomagdaleno.music_hub.common.clients.LoginClient.InputField
import com.joaomagdaleno.music_hub.common.clients.LyricsSearchClient
import com.joaomagdaleno.music_hub.common.clients.RadioClient
import com.joaomagdaleno.music_hub.common.clients.SaveClient
import com.joaomagdaleno.music_hub.common.clients.ShareClient
import com.joaomagdaleno.music_hub.common.clients.TrackClient
import com.joaomagdaleno.music_hub.common.clients.TrackerMarkClient
import com.joaomagdaleno.music_hub.common.helpers.ContinuationCallback.Companion.await
import com.joaomagdaleno.music_hub.common.helpers.PagedData
import com.joaomagdaleno.music_hub.common.helpers.WebViewClient
import com.joaomagdaleno.music_hub.common.helpers.WebViewRequest
import com.joaomagdaleno.music_hub.common.models.Artist
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.ExtensionType
import com.joaomagdaleno.music_hub.common.models.Feed
import com.joaomagdaleno.music_hub.common.models.Feed.Companion.toFeed
import com.joaomagdaleno.music_hub.common.models.Feed.Companion.toFeedData
import com.joaomagdaleno.music_hub.common.models.ImageHolder.Companion.toImageHolder
import com.joaomagdaleno.music_hub.common.models.ImportType
import com.joaomagdaleno.music_hub.common.models.Lyrics
import com.joaomagdaleno.music_hub.common.models.Metadata
import com.joaomagdaleno.music_hub.common.models.NetworkRequest
import com.joaomagdaleno.music_hub.common.models.NetworkRequest.Companion.toGetRequest
import com.joaomagdaleno.music_hub.common.models.Radio
import com.joaomagdaleno.music_hub.common.models.Shelf
import com.joaomagdaleno.music_hub.common.models.Streamable
import com.joaomagdaleno.music_hub.common.models.Streamable.Media.Companion.toBackgroundMedia
import com.joaomagdaleno.music_hub.common.models.Streamable.Media.Companion.toServerMedia
import com.joaomagdaleno.music_hub.common.models.Streamable.Media.Companion.toSubtitleMedia
import com.joaomagdaleno.music_hub.common.models.Streamable.Source.Companion.toSource
import com.joaomagdaleno.music_hub.common.models.Tab
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.common.models.TrackDetails
import com.joaomagdaleno.music_hub.common.models.User
import com.joaomagdaleno.music_hub.common.providers.WebViewClientProvider
import com.joaomagdaleno.music_hub.common.settings.Setting
import com.joaomagdaleno.music_hub.common.settings.Settings
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.random.Random

@Suppress("unused")
class TestExtension : ExtensionClient, LoginClient.CustomInput, TrackClient, LoginClient.WebView,
    HomeFeedClient, FollowClient, RadioClient, WebViewClientProvider, ArtistClient,
    LyricsSearchClient, LibraryFeedClient,
    SaveClient, LikeClient, HideClient, TrackerMarkClient, ShareClient {

    companion object {
        val metadata = Metadata(
            "TestExtension",
            "",
            ImportType.BuiltIn,
            ExtensionType.MUSIC,
            "test",
            "Test Extension",
            "1.0.0",
            "Test extension for offline testing",
            "Test",
            icon = "https://yt3.googleusercontent.com/UMGZZMPQkM3kGtyW4jNE1GtpSrydfNJdbG1UyWTp5zeqUYc6-rton70Imm7B11RulRRuK521NQ=s160-c-k-c0x00ffffff-no-rj".toImageHolder()
        )

        const val FUN =
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4"

        const val BUNNY =
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

        const val M3U8 =
            "https://fl3.moveonjoy.com/CNBC/index.m3u8"

        const val SUBTITLE =
            "https://raw.githubusercontent.com/brenopolanski/html5-video-webvtt-example/master/MIB2-subtitles-pt-BR.vtt"

        private fun createTrack(id: String, title: String, streamables: List<Streamable>) = Track(
            id,
            title,
            cover = "https://picsum.photos/seed/$id/300".toImageHolder(),
            isExplicit = Random.nextBoolean(),
            streamables = streamables
        ).toShelf()
    }

    override suspend fun getSettingItems() = listOf<Setting>()
    override fun setSettings(settings: Settings) {}

    override val forms = listOf(
        LoginClient.Form(
            "bruh",
            "Test Form",
            InputField.Type.Username,
            listOf(
                InputField(InputField.Type.Username, "name", "Name", true),
                InputField(InputField.Type.Password, "password", "Password", false),
                InputField(InputField.Type.Email, "email", "EMail", false),
                InputField(InputField.Type.Misc, "text", "Text", false),
                InputField(InputField.Type.Number, "number", "Number", false),
                InputField(InputField.Type.Url, "url", "Url", false),
            )
        )
    )

    override suspend fun onLogin(key: String, data: Map<String, String?>): List<User> {
        return listOf(
            User(
                key,
                "$key User",
                "https://picsum.photos/seed/$key/200".toImageHolder(),
            )
        )
    }

    override val webViewRequest = object : WebViewRequest.Cookie<List<User>> {
        override suspend fun onStop(url: NetworkRequest, cookie: String): List<User> {
            return listOf(
                User(
                    "test_user",
                    "WebView User",
                    "https://picsum.photos/seed/test_user/200".toImageHolder(),
                )
            )
        }

        override val initialUrl = "https://www.example.com/".toGetRequest()
        override val stopUrlRegex = "https://www\\.iana\\.org/.*".toRegex()
    }

    override fun setLoginUser(user: User?) {
//        println("setLoginUser: $user")
    }

    override suspend fun getCurrentUser(): User? = null

    override suspend fun loadStreamableMedia(
        streamable: Streamable, isDownload: Boolean,
    ): Streamable.Media {
        if (streamable.quality == 3) throw Exception("Test exception for quality 3")
        return when (streamable.type) {
            Streamable.MediaType.Background -> streamable.id.toBackgroundMedia()
            Streamable.MediaType.Server -> {
                val srcs = Srcs.valueOf(streamable.id)
                when (srcs) {
                    Srcs.Single -> FUN.toServerMedia()
                    Srcs.Merged -> Streamable.Media.Server(
                        listOf(
                            BUNNY.toSource(),
                            FUN.toSource(),
                        ), false
                    )

                    Srcs.M3U8 -> M3U8.toServerMedia(type = Streamable.SourceType.HLS)
                }
            }

            Streamable.MediaType.Subtitle -> streamable.id.toSubtitleMedia(Streamable.SubtitleType.VTT)
        }
    }

    override suspend fun loadFeed(track: Track): Feed<Shelf>? = null

    enum class Srcs {
        Single, Merged, M3U8;

        fun createTrack() = createTrack(
            name, name, listOf(Streamable.subtitle(SUBTITLE)) + (0..5).map { i ->
                Streamable.server(this.name, i)
            }
        )
    }

    private lateinit var webViewClient: WebViewClient
    override fun setWebViewClient(webViewClient: WebViewClient) {
        this.webViewClient = webViewClient
    }

    override suspend fun loadHomeFeed(): Feed<Shelf> = Feed(
        listOf("All", "Music", "Podcasts").map { Tab(it, it) }
    ) { tab ->
        if (tab?.id == "Podcasts") {
            throw Exception("Test exception for Podcasts tab")
//            return@Feed PagedData.empty<Shelf>().toFeedData()
        }
        PagedData.Single {
            if (tab?.id == "Music") delay(5000)
            OkHttpClient().newCall(
                Request.Builder().url("https://example.com").build()
            ).await().body
            listOf(
                Shelf.Lists.Categories(
                    "bruh",
                    "Bruh ${tab?.title}",
                    listOf(
                        "Burhhhhhhh", "brjdksls", "sbajkxclllll", "a", "b", " b"
                    ).map { Shelf.Category(it, it, loadHomeFeed()) }
                ),
                Shelf.Lists.Categories(
                    "bruh",
                    "Bruh ${tab?.title}",
                    listOf(
                        "Burhhhhhhh", "brjdksls", "sbajkxclllll", "a", "b", " b"
                    ).map { Shelf.Category(it, it, loadHomeFeed()) },
                    type = Shelf.Lists.Type.Grid
                ),
                Artist(
                    "bruh",
                    "Bruh",
                    cover = "https://www.easygifanimator.net/images/samples/video-to-gif-sample.gif"
                        .toImageHolder()
                ).toShelf(),
                Track("not", "Not Playable", isPlayable = Track.Playable.Unreleased).toShelf(),
                createTrack(
                    "all", "All", listOf(
                        Streamable.server(Srcs.Single.name, 0),
                        Streamable.server(Srcs.Merged.name, 0),
                        Streamable.server(Srcs.M3U8.name, 0),
                        Streamable.subtitle(SUBTITLE)
                    )
                ),
                Srcs.Single.createTrack(),
                Srcs.Merged.createTrack(),
                Srcs.M3U8.createTrack()
            )
        }.toFeedData(background = "https://picsum.photos/id/21/400/300".toImageHolder())
    }

    private val radio = Radio("empty", "empty")
    override suspend fun loadRadio(radio: Radio) = radio
    override suspend fun loadTracks(radio: Radio) = listOf(
        Track("", "Bruh")
    ).toFeed()

    override suspend fun radio(item: EchoMediaItem, context: EchoMediaItem?) = radio


    override suspend fun loadFeed(artist: Artist) = PagedData.Single<Shelf> {
        listOf(
            Srcs.Single.createTrack(),
            artist.toShelf(),
        )
    }.toFeed()

    override suspend fun loadArtist(artist: Artist) = artist

    private var isSaved = false
    override suspend fun saveToLibrary(item: EchoMediaItem, shouldSave: Boolean) {
        println(item.extras["loaded"])
        isSaved = shouldSave
        println("save $shouldSave")
    }

    override suspend fun isItemSaved(item: EchoMediaItem): Boolean {
        println(item.extras["loaded"])
        println("isSaved : $isSaved")
        return isSaved
    }


    private var isLiked = false
    override suspend fun likeItem(item: EchoMediaItem, shouldLike: Boolean) {
        println(item.extras["loaded"])
        isLiked = shouldLike
        println("like $shouldLike")
    }

    override suspend fun isItemLiked(item: EchoMediaItem): Boolean {
        println(item.extras["loaded"])
        println("isLiked : $isLiked")
        return isLiked
    }

    private var isHidden = false
    override suspend fun hideItem(item: EchoMediaItem, shouldHide: Boolean) {
        println("hide")
        this.isHidden = shouldHide
    }

    override suspend fun isItemHidden(item: EchoMediaItem): Boolean {
        println("isHidden : $isHidden")
        return isHidden
    }

    override suspend fun loadTrack(track: Track, isDownload: Boolean): Track = track.copy(
        extras = mapOf("loaded" to "Loaded bro")
    )

    override suspend fun onTrackChanged(details: TrackDetails?) {
        println("onTrackChanged : $details")
    }

    override suspend fun onPlayingStateChanged(details: TrackDetails?, isPlaying: Boolean) {
        println("onPlayingStateChanged $isPlaying : $details")
    }

    override suspend fun getMarkAsPlayedDuration(details: TrackDetails) = 10000L
    override suspend fun onMarkAsPlayed(details: TrackDetails) {
        println("onMarkAsPlayed : $details")
    }

    private var isFollowing = false
    override suspend fun isFollowing(item: EchoMediaItem) = isFollowing
    override suspend fun getFollowersCount(item: EchoMediaItem): Long = 1000L
    override suspend fun followItem(item: EchoMediaItem, shouldFollow: Boolean) {
        println("followItem: $item, follow: $shouldFollow")
        isFollowing = shouldFollow
    }

    override suspend fun onShare(item: EchoMediaItem): String {
        return "https://example.com/${item.id}"
    }

    val lyrics = listOf(
        Lyrics("1", "Test Lyrics 1", lyrics = Lyrics.Simple("First line\nSecond line\nThird line")),
        Lyrics(
            "2", "Test Lyrics 2", lyrics = Lyrics.Timed(
                listOf(
                    Lyrics.Item("First line", 0, 1000),
                    Lyrics.Item("Second line", 1000, 2000),
                    Lyrics.Item("Third line", 2000, 3000)
                )
            )
        ),
        Lyrics(
            "3", "Test Lyrics 3", lyrics = Lyrics.WordByWord(
                listOf(
                    listOf(Lyrics.Item("First", 0, 500), Lyrics.Item("line", 500, 1000)),
                    listOf(Lyrics.Item("Second", 1000, 1500), Lyrics.Item("line", 1500, 2000)),
                    listOf(Lyrics.Item("Third", 2000, 2500), Lyrics.Item("line", 2500, 3000))
                )
            )
        ),
    )

    override suspend fun searchLyrics(query: String): Feed<Lyrics> = Feed(
        listOf("Simple", "Timed", "WordByWord", "All").map { Tab(it, it) }
    ) { tab ->
        delay(3000)
        val burh = when (tab?.id) {
            "Simple" -> lyrics.filter { it.lyrics is Lyrics.Simple }
            "Timed" -> lyrics.filter { it.lyrics is Lyrics.Timed }
            "WordByWord" -> lyrics.filter { it.lyrics is Lyrics.WordByWord }
            else -> lyrics
        }
        burh.toFeedData()
    }

    override suspend fun searchTrackLyrics(clientId: String, track: Track) = run {
        delay(5000)
        lyrics.toFeed()
    }

    override suspend fun loadLyrics(lyrics: Lyrics) = lyrics
    override suspend fun loadLibraryFeed() = (0..10).map {
        createTrack("lib_$it", "Library Track $it", listOf(Streamable.server(Srcs.Single.name, 0)))
    }.toFeed<Shelf>(Feed.Buttons(showPlayAndShuffle = true))

}