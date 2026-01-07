package com.joaomagdaleno.music_hub.extensions.builtin.internal

import android.content.Context
import android.os.Environment
import com.joaomagdaleno.music_hub.common.clients.DownloadClient
import com.joaomagdaleno.music_hub.common.models.DownloadContext
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.ExtensionType
import com.joaomagdaleno.music_hub.common.models.ImportType
import com.joaomagdaleno.music_hub.common.models.Metadata
import com.joaomagdaleno.music_hub.common.models.Progress
import com.joaomagdaleno.music_hub.common.models.Streamable
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.common.models.ImageHolder
import com.joaomagdaleno.music_hub.common.settings.Setting
import com.joaomagdaleno.music_hub.common.settings.Settings
import com.joaomagdaleno.music_hub.common.helpers.ContinuationCallback.Companion.await
import com.joaomagdaleno.music_hub.utils.TagInjector
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class InternalDownloadSource(
    private val context: Context
) : DownloadClient {

    companion object {
        const val INTERNAL_DOWNLOAD_ID = "internal_downloader"
        val metadata = Metadata(
            className = "com.joaomagdaleno.music_hub.extensions.builtin.internal.InternalDownloadSource",
            path = "",
            importType = ImportType.BuiltIn,
            type = ExtensionType.MISC,
            id = INTERNAL_DOWNLOAD_ID,
            name = "Music Hub Downloader",
            version = "1.0.0",
            description = "Internal downloader with metadata tagging support",
            author = "João Magdaleno",
            isEnabled = true
        )
    }

    private val client = OkHttpClient()
    private val lrcLib = LrcLibApi(client)

    override val concurrentDownloads = 3

    override suspend fun getDownloadTracks(
        extensionId: String,
        item: EchoMediaItem,
        context: EchoMediaItem?
    ): List<DownloadContext> {
        return when (item) {
            is Track -> listOf(DownloadContext(extensionId, item))
            else -> emptyList()
        }
    }

    override suspend fun selectServer(context: DownloadContext): Streamable {
        return context.track.servers.first()
    }

    override suspend fun selectSources(
        context: DownloadContext,
        server: Streamable.Media.Server
    ): List<Streamable.Source> {
        return server.sources
    }

    override suspend fun download(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        source: Streamable.Source
    ): File {
        val request = Request.Builder().url(source.id).build()
        val response = client.newCall(request).await()
        if (!response.isSuccessful) throw Exception("Download failed: ${response.code}")

        val totalBytes = response.body?.contentLength() ?: -1L
        val destination = File(this.context.cacheDir, "download_${System.currentTimeMillis()}")
        
        response.body?.byteStream()?.use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalBytes > 0) {
                        progressFlow.value = Progress(totalBytes, totalRead)
                    }
                }
            }
        }
        return destination
    }

    override suspend fun merge(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        files: List<File>
    ): File {
        // Simple merge: if multiple files, just append (works for some formats like TS)
        // But for most audio, it should be just one file.
        if (files.size == 1) return files[0]
        
        val mergedFile = File(this.context.cacheDir, "merged_${System.currentTimeMillis()}")
        FileOutputStream(mergedFile).use { output ->
            files.forEach { file ->
                file.inputStream().use { input ->
                    input.copyTo(output)
                }
                file.delete()
            }
        }
        return mergedFile
    }

    override suspend fun tag(
        progressFlow: MutableStateFlow<Progress>,
        context: DownloadContext,
        file: File
    ): File {
        val track = context.track
        val lyrics = try {
            lrcLib.getLyrics(
                title = track.title,
                artist = track.artists.joinToString(", ") { it.name },
                duration = track.duration?.div(1000)?.toInt()
            )
        } catch (e: Exception) {
            null
        }

        TagInjector.writeMetadata(
            file = file,
            title = track.title,
            artist = track.artists.joinToString(", ") { it.name },
            album = track.album?.title,
            coverUrl = (track.cover as? ImageHolder.NetworkRequestImageHolder)?.request?.url,
            lyrics = lyrics
        )
        
        // After tagging, move to public music folder
        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Music Hub"
        )
        if (!publicDir.exists()) publicDir.mkdirs()
        
        val extension = if (file.extension.isNotEmpty()) file.extension else "mp3"
        val fileName = "${track.title} - ${track.artists.firstOrNull()?.name}.$extension"
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            
        val finalFile = File(publicDir, fileName)
        file.renameTo(finalFile)
        
        return finalFile
    }

    override suspend fun onInitialize() {}
    override suspend fun onExtensionSelected() {}
    override suspend fun getSettingItems(): List<Setting> = emptyList()
    override fun setSettings(settings: Settings) {}
}
