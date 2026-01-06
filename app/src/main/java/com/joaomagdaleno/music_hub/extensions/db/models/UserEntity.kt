package com.joaomagdaleno.music_hub.extensions.db.models

import androidx.room.Entity
import com.joaomagdaleno.music_hub.common.models.ExtensionType
import com.joaomagdaleno.music_hub.common.models.User
import com.joaomagdaleno.music_hub.utils.Serializer.toData
import com.joaomagdaleno.music_hub.utils.Serializer.toJson

@Entity(primaryKeys = ["id", "type", "extId"])
data class UserEntity(
    val type: ExtensionType,
    val extId: String,
    val id: String,
    val data: String
) {
    val user by lazy { data.toData<User>() }

    companion object {
        fun User.toEntity(type: ExtensionType, clientId: String) =
            UserEntity(type, clientId, id, toJson())

        fun UserEntity.toCurrentUser() =
            CurrentUser(type, extId, id)
    }
}
