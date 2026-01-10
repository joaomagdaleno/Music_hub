package com.joaomagdaleno.music_hub.data.providers

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import com.joaomagdaleno.music_hub.common.helpers.ContinuationCallback.Companion.await
import com.joaomagdaleno.music_hub.utils.FileLogger
import java.util.concurrent.TimeUnit

@Serializable
data class PipedSearchResult(
    val url: String,
    val type: String? = null,
    val title: String? = null,
    val thumbnail: String? = null,
    val uploaderName: String? = null,
    val uploaderUrl: String? = null,
    val duration: Long? = null,
    val isShort: Boolean = false
)

@Serializable
data class PipedStreamInfo(
    val audioStreams: List<PipedAudioStream> = emptyList(),
    val title: String? = null,
    val uploader: String? = null,
    val uploaderUrl: String? = null,
    val thumbnail: String? = null,
    val duration: Long? = null,
    val relatedStreams: List<PipedSearchResult> = emptyList()
)

@Serializable
data class PipedAudioStream(
    val url: String,
    val format: String,
    val quality: String,
    val bitrate: Int
)

class PipedApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    // Multiple Piped instances for fallback
    private val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://api.piped.yt",
        "https://pipedapi.moomoo.me"
    )
    private var currentInstanceIndex = 0
    private val baseUrl get() = pipedInstances[currentInstanceIndex]
    
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun <T> callWithFallback(
        path: String,
        queryParams: Map<String, String> = emptyMap(),
        parser: (String) -> T?
    ): T? {
        for (i in pipedInstances.indices) {
            val instance = pipedInstances[currentInstanceIndex]
            val urlBuilder = "$instance/$path".toHttpUrlOrNull()?.newBuilder() ?: continue
            queryParams.forEach { (name, value) -> urlBuilder.addQueryParameter(name, value) }
            val url = urlBuilder.build()

            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).await()

                if (response.isSuccessful) {
                    val body = response.body?.string() ?: continue
                    val result = parser(body)
                    if (result != null) {
                        FileLogger.log("PipedApi", "Success on $instance for $path")
                        return result
                    }
                } else {
                    FileLogger.log("PipedApi", "Failed on $instance for $path: ${response.code}")
                }
            } catch (e: Exception) {
                FileLogger.log("PipedApi", "Error on $instance for $path: ${e.message}")
            }
            currentInstanceIndex = (currentInstanceIndex + 1) % pipedInstances.size
        }
        return null
    }

    suspend fun search(query: String): List<PipedSearchResult> {
        FileLogger.log("PipedApi", "search() called with query='$query'")
        return callWithFallback(
            path = "search",
            queryParams = mapOf("q" to query, "filter" to "music_songs")
        ) { body ->
            try {
                val data = json.parseToJsonElement(body)
                if (data is JsonObject) {
                    val items = data["items"]?.jsonArray ?: return@callWithFallback null
                    items.mapNotNull { 
                        try {
                            json.decodeFromJsonElement(PipedSearchResult.serializer(), it)
                        } catch (e: Exception) {
                            null
                        }
                    }
                } else null
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()
    }

    suspend fun getStream(videoId: String): PipedStreamInfo? {
        FileLogger.log("PipedApi", "getStream() called for $videoId")
        return callWithFallback(
            path = "streams/$videoId"
        ) { body ->
            try {
                json.decodeFromString<PipedStreamInfo>(body)
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun getStreamUrl(videoId: String): String? {
        return getStream(videoId)?.audioStreams?.maxByOrNull { it.bitrate }?.url
    }

    suspend fun getChannelItems(channelId: String): List<PipedSearchResult> {
        FileLogger.log("PipedApi", "getChannelItems for $channelId")
        return callWithFallback(
            path = "channels/$channelId"
        ) { body ->
            try {
                val data = json.parseToJsonElement(body).jsonObject
                val items = data["relatedStreams"]?.jsonArray ?: return@callWithFallback null
                items.mapNotNull {
                    try {
                        json.decodeFromJsonElement(PipedSearchResult.serializer(), it)
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()
    }

    suspend fun getPlaylistTracks(playlistId: String): List<PipedSearchResult> {
        FileLogger.log("PipedApi", "getPlaylistTracks for $playlistId")
        return callWithFallback(
            path = "playlists/$playlistId"
        ) { body ->
            try {
                val data = json.parseToJsonElement(body).jsonObject
                val items = data["relatedStreams"]?.jsonArray ?: return@callWithFallback null
                items.mapNotNull {
                    try {
                        json.decodeFromJsonElement(PipedSearchResult.serializer(), it)
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()
    }

    suspend fun getPlaylistItems(playlistId: String): List<PipedSearchResult> = getPlaylistTracks(playlistId)

    suspend fun getTrending(region: String = "BR"): List<PipedSearchResult> {
        FileLogger.log("PipedApi", "getTrending for $region")
        return callWithFallback(
            path = "trending",
            queryParams = mapOf("region" to region)
        ) { body ->
            try {
                val items = json.parseToJsonElement(body).jsonArray
                items.mapNotNull {
                    try {
                        json.decodeFromJsonElement(PipedSearchResult.serializer(), it)
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()
    }

    suspend fun getSuggestions(query: String): List<String> {
        FileLogger.log("PipedApi", "getSuggestions for '$query'")
        return callWithFallback(
            path = "suggestions",
            queryParams = mapOf("query" to query)
        ) { body ->
            try {
                val array = json.parseToJsonElement(body).jsonArray
                array.map { it.jsonPrimitive.content }
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()
    }
}
