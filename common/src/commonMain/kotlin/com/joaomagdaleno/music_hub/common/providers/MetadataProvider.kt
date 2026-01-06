package com.joaomagdaleno.music_hub.common.providers

import com.joaomagdaleno.music_hub.common.models.Metadata

interface MetadataProvider {
    fun setMetadata(metadata: Metadata)
}