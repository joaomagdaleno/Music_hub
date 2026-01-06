package com.joaomagdaleno.music_hub.common.providers

import com.joaomagdaleno.music_hub.common.helpers.WebViewClient

interface WebViewClientProvider {
    fun setWebViewClient(webViewClient: WebViewClient)
}