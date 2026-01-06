package com.joaomagdaleno.music_hub.download.tasks

import android.content.Context
import com.joaomagdaleno.music_hub.download.Downloader
import com.joaomagdaleno.music_hub.download.db.models.TaskType
import java.io.File

class MergingTask(
    context: Context,
    downloader: Downloader,
    override val trackId: Long,
) : BaseTask(context, downloader, trackId) {
    override val type = TaskType.Merging

    override suspend fun work(trackId: Long) {
        val downloadContext = getDownloadContext()
        val download = getDownload()
        val file = withDownloadExtension {
            merge(progressFlow, downloadContext, download.toMergeFiles.map { File(it) })
        }
        dao.insertDownloadEntity(download.copy(toTagFile = file.toString()))
    }

}