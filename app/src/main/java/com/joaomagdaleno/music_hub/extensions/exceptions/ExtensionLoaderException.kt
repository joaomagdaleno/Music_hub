package com.joaomagdaleno.music_hub.extensions.exceptions

class ExtensionLoaderException(
    val clazz: String,
    val source: String,
    override val cause: Throwable
) : Exception()