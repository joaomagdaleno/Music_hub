package com.joaomagdaleno.music_hub.extensions.db.models

import androidx.room.Entity
import com.joaomagdaleno.music_hub.common.models.ExtensionType

@Entity(primaryKeys = ["type", "extId"])
data class CurrentUser(
    val type : ExtensionType,
    val extId: String,
    val userId: String?
)