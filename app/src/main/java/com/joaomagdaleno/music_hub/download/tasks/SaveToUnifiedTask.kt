package com.joaomagdaleno.music_hub.download.tasks

import android.content.Context
import com.joaomagdaleno.music_hub.common.clients.PlaylistEditClient
import com.joaomagdaleno.music_hub.common.models.Feed.Companion.loadAll
import com.joaomagdaleno.music_hub.download.Downloader
import com.joaomagdaleno.music_hub.download.db.models.TaskType
import com.joaomagdaleno.music_hub.extensions.ExtensionUtils.getAs
import com.joaomagdaleno.music_hub.extensions.ExtensionUtils.getExtensionOrThrow
import com.joaomagdaleno.music_hub.extensions.builtin.unified.UnifiedExtension
import com.joaomagdaleno.music_hub.extensions.builtin.unified.UnifiedExtension.Companion.withExtensionId
import com.joaomagdaleno.music_hub.utils.Serializer.toJson
import com.joaomagdaleno.music_hub.utils.image.ImageUtils.loadDrawable

class SaveToUnifiedTask(
    private val app: Context,
    downloader: Downloader,
    override val trackId: Long,
) : BaseTask(app, downloader, trackId) {

    override val type = TaskType.Saving

    override suspend fun work(trackId: Long) {
        val old = getDownload()
        if (old.finalFile == null) return

        val download = old.copy(
            data = old.track.getOrThrow().withExtensionId(old.extensionId, false).toJson(),
        )
        dao.insertDownloadEntity(download)

        val downloadContext = getDownloadContext()
        val context = downloadContext.context
        val allDownloads = dao.getDownloadsForContext(download.contextId)

        val unifiedExtension =
            downloader.extensionLoader.music.getExtensionOrThrow(UnifiedExtension.metadata.id)

        if (context != null) unifiedExtension.getAs<PlaylistEditClient, Unit> {
            val db = (this as UnifiedExtension).db
            val playlist = db.getOrCreate(app, context)
            val tracks = loadTracks(playlist).loadAll()
            if (allDownloads.all { it.finalFile != null }) {
                removeTracksFromPlaylist(playlist, tracks, tracks.indices.toList())
                val sorted = allDownloads.sortedBy { it.sortOrder }
                addTracksToPlaylist(playlist, listOf(), 0, sorted.map { it.track.getOrThrow() })
            } else addTracksToPlaylist(
                playlist, tracks, tracks.size, listOf(download.track.getOrThrow())
            )
        }.getOrThrow()

        dao.insertDownloadEntity(download.copy(fullyDownloaded = true))

        val item = if (context == null) download.track.getOrThrow() else {
            if (allDownloads.all { it.finalFile != null }) context else null
        } ?: return
        createCompleteNotification(app, item.title, item.cover.loadDrawable(app))
    }
}