package com.joaomagdaleno.music_hub.download.db.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.utils.Serializer.toData

@Entity
data class ContextEntity(
    @PrimaryKey(true)
    val id: Long,
    val itemId: String,
    val data: String,
) {
    val mediaItem by lazy { data.toData<EchoMediaItem>() }
}
