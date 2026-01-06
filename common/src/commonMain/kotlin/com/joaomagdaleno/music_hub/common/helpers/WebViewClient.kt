package com.joaomagdaleno.music_hub.common.helpers

interface WebViewClient {
    suspend fun await(
        showWebView: Boolean, reason: String, request: WebViewRequest<String>
    ): Result<String?>
}