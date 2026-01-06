package com.joaomagdaleno.music_hub.extensions.exceptions

class InvalidExtensionListException(
    val link: String, override val cause: Throwable
) : Exception()