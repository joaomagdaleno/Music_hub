package com.joaomagdaleno.music_hub.download.tasks

import android.content.Context
import com.joaomagdaleno.music_hub.common.clients.TrackClient
import com.joaomagdaleno.music_hub.common.models.Progress
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.download.Downloader
import com.joaomagdaleno.music_hub.download.db.models.TaskType
import com.joaomagdaleno.music_hub.download.tasks.TaskManager.Companion.toQueueItem
import com.joaomagdaleno.music_hub.extensions.ExtensionUtils.getAs
import com.joaomagdaleno.music_hub.extensions.ExtensionUtils.getExtensionOrThrow
import com.joaomagdaleno.music_hub.utils.Serializer.toJson

class LoadingTask(
    private val context: Context,
    downloader: Downloader,
    override val trackId: Long,
) : BaseTask(context, downloader, trackId) {

    override val type = TaskType.Loading

    private val manager = downloader.taskManager
    private val extensionsList = downloader.extensionLoader.music

    private val totalSize = 3L

    override suspend fun work(trackId: Long) {
        progressFlow.value = Progress(totalSize, 0)
        var download = dao.getDownloadEntity(trackId)!!
        val extension = extensionsList.getExtensionOrThrow(download.extensionId)
        if (!download.loaded) {
            val track = extension.getAs<TrackClient, Track> {
                loadTrack(download.track.getOrThrow(), true)
            }.getOrThrow()
            track.servers.ifEmpty { throw Exception("${track.title}: No servers found") }
            download = download.copy(data = track.toJson(), loaded = true)
            dao.insertDownloadEntity(download)
        }

        progressFlow.value = Progress(totalSize, 1)
        val downloadContext = getDownloadContext()
        if (download.streamableId == null) {
            val selected = withDownloadExtension { selectServer(downloadContext) }
            download = download.copy(streamableId = selected.id)
            dao.insertDownloadEntity(download)
        }
        progressFlow.value = Progress(totalSize, 2)

        val server = downloader.getServer(trackId, download)

        val indexes = download.indexes.ifEmpty {
            val sources = withDownloadExtension { selectSources(downloadContext, server) }
            sources.map { server.sources.indexOf(it) }
        }
        if (indexes.isEmpty()) throw Exception("No files to download")
        download = download.copy(indexesData = indexes.toJson())
        dao.insertDownloadEntity(download)

        progressFlow.value = Progress(totalSize, 3)

        val requests = indexes.map { index ->
            DownloadingTask(context, downloader, trackId, index)
        }.toQueueItem()
        val mergeRequest = MergingTask(context, downloader, trackId).toQueueItem()
        val taggingRequest = TaggingTask(context, downloader, trackId).toQueueItem()
        val saveToUnified = SaveToUnifiedTask(context, downloader, trackId).toQueueItem()

        manager.enqueue(trackId, listOf(requests, mergeRequest, taggingRequest, saveToUnified))
    }
}