package com.joaomagdaleno.music_hub.extensions

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.joaomagdaleno.music_hub.MainActivity.Companion.getMainActivity
import com.joaomagdaleno.music_hub.common.helpers.WebViewClient
import com.joaomagdaleno.music_hub.common.helpers.WebViewRequest
import com.joaomagdaleno.music_hub.common.models.Metadata
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first

class WebViewClientFactory(
    private val context: Context,
) {

    val requests = mutableMapOf<Int, Wrapper>()
    val responseFlow = MutableSharedFlow<Pair<Wrapper, Result<String?>?>>()

    suspend fun await(
        ext: Metadata, showWebView: Boolean, reason: String, request: WebViewRequest<String>,
    ): Result<String?> {
        val wrapper = Wrapper(ext, showWebView, reason, request)
        val id = wrapper.hashCode()
        requests[id] = wrapper
        startWebView(id)
        val res = responseFlow.first { it.first == wrapper && it.second != null }.second!!
        requests.remove(id)
        return res
    }

    private fun startWebView(id: Int) {
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, context.getMainActivity()).apply {
                putExtra("webViewRequest", id)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ).send()
    }

    fun createFor(metadata: Metadata) = object : WebViewClient {
        override suspend fun await(
            showWebView: Boolean,
            reason: String,
            request: WebViewRequest<String>,
        ): Result<String?> = await(metadata, showWebView, reason, request)
    }

    data class Wrapper(
        val extension: Metadata,
        val showWebView: Boolean,
        val reason: String,
        val request: WebViewRequest<String>,
    )

}