package com.joaomagdaleno.music_hub.playback.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import com.joaomagdaleno.music_hub.common.models.ImageHolder
// Imports removed
import com.joaomagdaleno.music_hub.utils.CoroutineUtils
import com.joaomagdaleno.music_hub.utils.Serializer
import com.joaomagdaleno.music_hub.utils.image.ImageUtils
import kotlinx.coroutines.CoroutineScope

@UnstableApi
class PlayerBitmapLoader(
    val context: Context,
    private val scope: CoroutineScope
) : BitmapLoader {

    override fun supportsMimeType(mimeType: String) = true

    override fun decodeBitmap(data: ByteArray) = CoroutineUtils.futureCatching(scope) {
        BitmapFactory.decodeByteArray(data, 0, data.size) ?: error("Failed to decode bitmap")
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = CoroutineUtils.futureCatching(scope) {
        val json = uri.getQueryParameter("actual_data")!!
        val cover = Serializer.toData<ImageHolder>(json).getOrThrow()
        ImageUtils.loadDrawable(cover, context)?.toBitmapOrNull()
            ?: error("Failed to load bitmap of $cover")
    }
}