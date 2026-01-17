package com.joaomagdaleno.music_hub.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.PreferenceFragmentCompat
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.ImageHolder
import com.joaomagdaleno.music_hub.utils.ContextUtils
import com.joaomagdaleno.music_hub.utils.PermsUtils
import com.joaomagdaleno.music_hub.utils.PrefsUtils
import com.joaomagdaleno.music_hub.utils.ui.prefs.TransitionPreference

class SettingsOtherFragment : BaseSettingsFragment() {
    override val title get() = getString(R.string.other_settings)
    override val icon get() = ImageHolder.toResourceImageHolder(R.drawable.ic_more_horiz)
    override val creator = { OtherPreference() }

    class OtherPreference : PreferenceFragmentCompat() {

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            BaseSettingsFragment.configure(this)
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val context = preferenceManager.context
            preferenceManager.sharedPreferencesName = ContextUtils.SETTINGS_NAME
            preferenceManager.sharedPreferencesMode = Context.MODE_PRIVATE
            val screen = preferenceManager.createPreferenceScreen(context)
            preferenceScreen = screen



            TransitionPreference(context).apply {
                key = "export"
                title = getString(R.string.export_settings)
                summary = getString(R.string.export_settings_summary)
                layoutResource = R.layout.preference
                isIconSpaceReserved = false
                screen.addPreference(this)
                setOnPreferenceClickListener {
                    val contract = ActivityResultContracts.CreateDocument("application/json")
                    PermsUtils.registerActivityResultLauncher(requireActivity(), contract) { uri ->
                        uri?.let { PrefsUtils.exportSettings(context, it) }
                    }.launch("echo-settings.json")
                    true
                }
            }

            TransitionPreference(context).apply {
                key = "import"
                title = getString(R.string.import_settings)
                summary = getString(R.string.import_settings_summary)
                layoutResource = R.layout.preference
                isIconSpaceReserved = false
                screen.addPreference(this)
                setOnPreferenceClickListener {
                    val contract = ActivityResultContracts.OpenDocument()
                    PermsUtils.registerActivityResultLauncher(requireActivity(), contract) { uri ->
                        uri?.let {
                            PrefsUtils.importSettings(context, it)
                            requireActivity().recreate()
                        }
                    }.launch(arrayOf("application/json"))
                    true
                }
            }
        }
    }
}