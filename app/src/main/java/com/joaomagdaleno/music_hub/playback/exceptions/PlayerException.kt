package com.joaomagdaleno.music_hub.playback.exceptions

import androidx.media3.common.MediaItem

class PlayerException(
    val mediaItem: MediaItem?,
    override val cause: Throwable
) : Exception()