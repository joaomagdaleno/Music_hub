package com.joaomagdaleno.music_hub.extensions.builtin.internal

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
    val audioStreams: List<PipedAudioStream> = emptyList()
)

@Serializable
data class PipedAudioStream(
    val url: String,
    val format: String,
    val quality: String,
    val bitrate: Int
)

class PipedApi(private val client: OkHttpClient = OkHttpClient()) {
    private val baseUrl = "https://pipedapi.kavin.rocks"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String): List<PipedSearchResult> {
        val url = "$baseUrl/search".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("q", query)
            ?.addQueryParameter("filter", "music_songs")
            ?.build() ?: return emptyList()

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).await()

        if (!response.isSuccessful) return emptyList()

        val responseBody = response.body?.string() ?: return emptyList()
        return try {
            val data = json.parseToJsonElement(responseBody)
            when (data) {
                is JsonObject -> {
                    val items = data["items"]?.jsonArray ?: return emptyList()
                    items.mapNotNull { 
                        try {
                            json.decodeFromJsonElement(PipedSearchResult.serializer(), it)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getStreamUrl(videoId: String): String? {
        val url = "$baseUrl/streams/$videoId".toHttpUrlOrNull() ?: return null

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).await()

        if (!response.isSuccessful) return null

        val responseBody = response.body?.string() ?: return null
        return try {
            val data = json.decodeFromString<PipedStreamInfo>(responseBody)
            // Pick highest bitrate audio stream
            data.audioStreams.maxByOrNull { it.bitrate }?.url
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getChannelItems(channelId: String): List<PipedSearchResult> {
        val url = "$baseUrl/channels/$channelId".toHttpUrlOrNull() ?: return emptyList()
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).await()

        if (!response.isSuccessful) return emptyList()

        val responseBody = response.body?.string() ?: return emptyList()
        return try {
            val data = json.parseToJsonElement(responseBody).jsonObject
            val items = data["relatedStreams"]?.jsonArray ?: return emptyList()
            items.mapNotNull {
                try {
                    json.decodeFromJsonElement(PipedSearchResult.serializer(), it)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getPlaylistTracks(playlistId: String): List<PipedSearchResult> {
        val url = "$baseUrl/playlists/$playlistId".toHttpUrlOrNull() ?: return emptyList()
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).await()

        if (!response.isSuccessful) return emptyList()

        val responseBody = response.body?.string() ?: return emptyList()
        return try {
            val data = json.parseToJsonElement(responseBody).jsonObject
            val items = data["relatedStreams"]?.jsonArray ?: return emptyList()
            items.mapNotNull {
                try {
                    json.decodeFromJsonElement(PipedSearchResult.serializer(), it)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getTrending(region: String = "BR"): List<PipedSearchResult> {
        val url = "$baseUrl/trending".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("region", region)
            ?.build() ?: return emptyList()
        
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).await()

        if (!response.isSuccessful) return emptyList()

        val responseBody = response.body?.string() ?: return emptyList()
        return try {
            val items = json.parseToJsonElement(responseBody).jsonArray
            items.mapNotNull {
                try {
                    json.decodeFromJsonElement(PipedSearchResult.serializer(), it)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
