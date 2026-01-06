package com.joaomagdaleno.music_hub.download.tasks

import android.content.Context
import com.joaomagdaleno.music_hub.download.Downloader
import com.joaomagdaleno.music_hub.download.db.models.TaskType
import com.joaomagdaleno.music_hub.utils.Serializer.toJson

class DownloadingTask(
    context: Context,
    downloader: Downloader,
    override val trackId: Long,
    val index: Int,
) : BaseTask(context, downloader, trackId) {

    override val type = TaskType.Downloading
    override suspend fun work(trackId: Long) {
        var download = getDownload()
        val server = downloader.getServer(trackId, download)
        val source = server.sources[index]
        val downloadContext = getDownloadContext()
        val file = withDownloadExtension { download(progressFlow, downloadContext, source) }
        download = getDownload()
        download =
            download.copy(toMergeFilesData = (download.toMergeFiles + file.toString()).toJson())
        dao.insertDownloadEntity(download)
    }
}