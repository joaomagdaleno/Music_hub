package com.joaomagdaleno.music_hub.download.exceptions

import com.joaomagdaleno.music_hub.download.db.models.DownloadEntity
import com.joaomagdaleno.music_hub.download.db.models.TaskType

data class DownloadException(
    val type: TaskType,
    val downloadEntity: DownloadEntity,
    override val cause: Throwable
) : Exception()