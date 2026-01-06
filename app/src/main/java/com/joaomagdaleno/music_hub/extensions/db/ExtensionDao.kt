package com.joaomagdaleno.music_hub.extensions.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.joaomagdaleno.music_hub.extensions.db.models.ExtensionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExtensionDao {

    @Query("SELECT * FROM ExtensionEntity")
    fun getExtensionFlow(): Flow<List<ExtensionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setExtension(extensionEntity: ExtensionEntity)
}