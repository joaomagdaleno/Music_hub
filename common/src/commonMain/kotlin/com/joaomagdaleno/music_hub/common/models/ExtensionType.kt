package com.joaomagdaleno.music_hub.common.models

import com.joaomagdaleno.music_hub.common.Extension

/**
 * Enum class to define the type of extension
 * @param feature the string used to identify the extension in the AndroidManifest uses-feature tag
 *
 * @see [Extension]
 */
enum class ExtensionType(val feature: String) {
    MUSIC("music"),
    TRACKER("tracker"),
    LYRICS("lyrics"),
    MISC("misc");
}