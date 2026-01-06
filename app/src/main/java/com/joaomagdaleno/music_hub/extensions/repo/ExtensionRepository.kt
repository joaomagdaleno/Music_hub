package com.joaomagdaleno.music_hub.extensions.repo

import com.joaomagdaleno.music_hub.common.clients.ExtensionClient
import com.joaomagdaleno.music_hub.common.models.Metadata
import kotlinx.coroutines.flow.Flow

interface ExtensionRepository {
    val flow: Flow<List<Result<Pair<Metadata, Lazy<ExtensionClient>>>>?>
    suspend fun loadExtensions(): List<Result<Pair<Metadata, Lazy<ExtensionClient>>>>
}