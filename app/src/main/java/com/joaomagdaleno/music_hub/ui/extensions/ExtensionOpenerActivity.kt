package com.joaomagdaleno.music_hub.ui.extensions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toFile
import com.joaomagdaleno.music_hub.MainActivity.Companion.getMainActivity
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.extensions.InstallationUtils.getTempFile
import com.joaomagdaleno.music_hub.utils.ContextUtils.getTempFile
import java.io.File

class ExtensionOpenerActivity : Activity() {
    override fun onStart() {
        super.onStart()
        val uri = intent.data

        val file = runCatching {
            when (uri?.scheme) {
                "content" -> getTempFile(uri)
                "file" -> getTempFile(uri.toFile())
                else -> null
            }
        }.getOrNull()

        if (file == null) Toast.makeText(
            this, getString(R.string.could_not_find_the_file), Toast.LENGTH_SHORT
        ).show()

        finish()
        val startIntent = Intent(this, getMainActivity())
        startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startIntent.data = file?.let { Uri.fromFile(it) }
        startActivity(startIntent)
    }

    private fun getTempFile(file: File): File {
        val tempFile = getTempFile()
        file.copyTo(tempFile)
        return tempFile
    }
}
