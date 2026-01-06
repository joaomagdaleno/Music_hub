package com.joaomagdaleno.music_hub.extensions.exceptions

class ExtensionNotFoundException(val id: String?) : Exception("Extension not found: $id")