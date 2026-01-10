package com.joaomagdaleno.music_hub.utils

import android.app.Activity
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.joaomagdaleno.music_hub.utils.PermsUtils.registerActivityResultLauncher
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

object InstallationUtils {

    suspend fun installApp(activity: FragmentActivity, file: File) {
        val contentUri = FileProvider.getUriForFile(
            activity, "${activity.packageName}.provider", file
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
            data = contentUri
        }
        val it = activity.waitForResult(installIntent)
        if (it.resultCode == Activity.RESULT_OK) return
        val result = it.data?.extras?.getInt("android.intent.extra.INSTALL_RESULT")
        if (result != null && result != 0) {
             throw Exception("Installation failed. Error Code: $result")
        }
    }

    private suspend fun FragmentActivity.waitForResult(
        intent: Intent
    ) = suspendCancellableCoroutine { cont ->
        val contract = ActivityResultContracts.StartActivityForResult()
        val launcher = registerActivityResultLauncher(contract) { cont.resume(it) }
        cont.invokeOnCancellation { launcher.unregister() }
        launcher.launch(intent)
    }
}
