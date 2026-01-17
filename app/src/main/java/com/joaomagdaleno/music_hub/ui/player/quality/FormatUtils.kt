package com.joaomagdaleno.music_hub.ui.player.quality

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.Streamable

object FormatUtils {
    @OptIn(UnstableApi::class)
    private fun getBitrate(format: Format) =
        (format.bitrate / 1000).takeIf { it > 0 }?.let { " • $it kbps" } ?: ""

    private fun getFrameRate(format: Format) =
        format.frameRate.toInt().takeIf { it > 0 }?.let { " • $it fps" } ?: ""

    private fun getMimeType(format: Format) = when (val mime = format.sampleMimeType?.replace("audio/", "")) {
        "mp4a-latm" -> "AAC"
        else -> mime?.uppercase()
    }

    private fun getHertz(format: Format) =
        format.sampleRate.takeIf { it > 0 }?.let { " • $it Hz" } ?: ""

    private fun getChannelCount(format: Format) =
        format.channelCount.takeIf { it > 0 }?.let { " • ${it}ch" } ?: ""

    @OptIn(UnstableApi::class)
    fun toAudioDetails(format: Format) =
        "${getMimeType(format)}${getHertz(format)}${getChannelCount(format)}${getBitrate(format)}"

    fun toVideoDetails(format: Format) = "${format.height}p${getFrameRate(format)}${getBitrate(format)}"
    fun toSubtitleDetails(format: Format) = format.label ?: format.language ?: "Unknown"

    private fun getSelectedFormat(groups: List<Tracks.Group>): Format? {
        return groups.firstNotNullOfOrNull { trackGroup ->
            val index = (0 until trackGroup.length).firstNotNullOfOrNull { i ->
                if (trackGroup.isTrackSelected(i)) i else null
            } ?: return@firstNotNullOfOrNull null
            trackGroup.getTrackFormat(index)
        }
    }

    fun getSelected(groups: List<Tracks.Group>): Pair<List<Pair<Tracks.Group, Int>>, Int?> {
        var selected: Pair<Tracks.Group, Int>? = null
        val trackGroups = groups.map { trackGroup ->
            (0 until trackGroup.length).map { i ->
                val pair = Pair(trackGroup, i)
                val isSelected = trackGroup.isTrackSelected(i)
                if (isSelected) selected = pair
                pair
            }
        }.flatten()
        val select = trackGroups.indexOf(selected).takeIf { it != -1 }
        return trackGroups to select
    }

    fun getDetails(
        tracks: Tracks, context: Context, server: Streamable.Media.Server?, index: Int?,
    ): List<String> {
        val groups = tracks.groups
        val audios = groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        // val videos = groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        val subtitles = groups.filter { it.type == C.TRACK_TYPE_TEXT }
        val sourceTitle = server?.run {
            if (merged) streams.mapNotNull { it.title }
            else listOfNotNull(streams.getOrNull(index ?: -1)?.title)
        }.orEmpty()
        return sourceTitle + listOfNotNull(
            getSelectedFormat(audios)?.let { toAudioDetails(it) },
            // getSelectedFormat(videos)?.let { toVideoDetails(it) },
            getSelectedFormat(subtitles)?.let { toSubtitleDetails(it) }
        ).ifEmpty { listOf(context.getString(R.string.unknown_quality)) }
    }
}
