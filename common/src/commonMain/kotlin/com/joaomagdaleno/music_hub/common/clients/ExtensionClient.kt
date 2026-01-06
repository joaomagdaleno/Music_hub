package com.joaomagdaleno.music_hub.common.clients

import com.joaomagdaleno.music_hub.common.Extension
import com.joaomagdaleno.music_hub.common.helpers.ContinuationCallback.Companion.await
import com.joaomagdaleno.music_hub.common.providers.SettingsProvider
import okhttp3.OkHttpClient

/**
 * Interface to be implemented by every [Extension], create your network client, database client, etc. here.
 *
 * If using [OkHttpClient], use the custom [await] function for suspending the network call.
 * ```kotlin
 * val request = Request.Builder().url("https://example.com").build()
 * val response = client.newCall(request).await()
 * ```
 *
 * This interface extends the [SettingsProvider] interface
 */
interface ExtensionClient : SettingsProvider {
    /**
     * Only called when an extension is selected by the user, not when the extension is loaded
     * Use the `onInitialize` for doing stuff to initialize the extension
     *
     * can be called multiple times, if the user re-selects the extension
     */
    suspend fun onExtensionSelected() {}

    /**
     * Called when the extension is loaded, called after all the injections are done.
     * Only called once
     */
    suspend fun onInitialize() {}
}