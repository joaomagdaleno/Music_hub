package com.joaomagdaleno.music_hub.ui.settings

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.joaomagdaleno.music_hub.MainActivity
import com.joaomagdaleno.music_hub.MainActivity.Companion.AMOLED_KEY
import com.joaomagdaleno.music_hub.MainActivity.Companion.BACK_ANIM
import com.joaomagdaleno.music_hub.MainActivity.Companion.BIG_COVER
import com.joaomagdaleno.music_hub.MainActivity.Companion.COLOR_KEY
import com.joaomagdaleno.music_hub.MainActivity.Companion.CUSTOM_THEME_KEY
import com.joaomagdaleno.music_hub.MainActivity.Companion.THEME_KEY
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.ImageHolder
import com.joaomagdaleno.music_hub.playback.MediaItemUtils
import com.joaomagdaleno.music_hub.ui.common.UiViewModel
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.BACKGROUND_GRADIENT
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.NAVBAR_GRADIENT
import com.joaomagdaleno.music_hub.ui.player.PlayerFragment
import com.joaomagdaleno.music_hub.utils.ContextUtils
import com.joaomagdaleno.music_hub.utils.ui.AnimationUtils
import com.joaomagdaleno.music_hub.utils.ui.FastScrollerHelper
import com.joaomagdaleno.music_hub.utils.ui.prefs.ColorListPreference
import com.joaomagdaleno.music_hub.utils.ui.prefs.MaterialListPreference

class SettingsLookFragment : BaseSettingsFragment() {
    override val title get() = getString(R.string.look_and_feel)
    override val icon get() = ImageHolder.toResourceImageHolder(R.drawable.ic_palette)
    override val creator = { LookPreference() }

    class LookPreference : PreferenceFragmentCompat() {
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            BaseSettingsFragment.configure(this)
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val context = preferenceManager.context
            preferenceManager.sharedPreferencesName = ContextUtils.SETTINGS_NAME
            preferenceManager.sharedPreferencesMode = Context.MODE_PRIVATE
            val preferences = preferenceManager.sharedPreferences ?: return

            val screen = preferenceManager.createPreferenceScreen(context)
            preferenceScreen = screen

            PreferenceCategory(context).apply {
                title = getString(R.string.colors)
                key = "colors"
                isIconSpaceReserved = false
                layoutResource = R.layout.preference_category
                screen.addPreference(this)

                MaterialListPreference(context).apply {
                    key = MainActivity.THEME_KEY
                    title = getString(R.string.theme)
                    summary = getString(R.string.theme_summary)
                    layoutResource = R.layout.preference
                    isIconSpaceReserved = false

                    entries = context.resources.getStringArray(R.array.themes)
                    entryValues = arrayOf("light", "dark", "system")
                    value = preferences.getString(MainActivity.THEME_KEY, "system")
                    addPreference(this)
                }

                SwitchPreferenceCompat(context).apply {
                    key = MainActivity.CUSTOM_THEME_KEY
                    title = getString(R.string.custom_theme_color)
                    summary = getString(R.string.custom_theme_color_summary)
                    layoutResource = R.layout.preference_switch
                    isIconSpaceReserved = false
                    setDefaultValue(true)
                    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, it ->
                        screen.findPreference<Preference>(MainActivity.COLOR_KEY)?.isEnabled = it as Boolean
                        true
                    }
                    addPreference(this)
                }

                ColorListPreference(this@LookPreference).apply {
                    key = MainActivity.COLOR_KEY
                    setDefaultValue(MainActivity.defaultColor(context))
                    isEnabled = preferences.getBoolean(MainActivity.CUSTOM_THEME_KEY, true)
                    addPreference(this)
                }

                SwitchPreferenceCompat(context).apply {
                    key = MainActivity.AMOLED_KEY
                    title = getString(R.string.amoled)
                    summary = getString(R.string.amoled_summary)
                    layoutResource = R.layout.preference_switch
                    isIconSpaceReserved = false
                    setDefaultValue(false)
                    addPreference(this)
                }

                SwitchPreferenceCompat(context).apply {
                    key = UiViewModel.NAVBAR_GRADIENT
                    title = getString(R.string.navbar_gradient)
                    summary = getString(R.string.navbar_gradient_summary)
                    layoutResource = R.layout.preference_switch
                    isIconSpaceReserved = false
                    setDefaultValue(true)
                    addPreference(this)
                }

                SwitchPreferenceCompat(context).apply {
                    key = UiViewModel.BACKGROUND_GRADIENT
                    title = getString(R.string.background_gradient)
                    summary = getString(R.string.background_gradient_summary)
                    layoutResource = R.layout.preference_switch
                    isIconSpaceReserved = false
                    setDefaultValue(true)
                    addPreference(this)
                }

                SwitchPreferenceCompat(context).apply {
                    key = PlayerFragment.DYNAMIC_PLAYER
                    title = getString(R.string.dynamic_player)
                    summary = getString(R.string.dynamic_player_summary)
                    layoutResource = R.layout.preference_switch
                    isIconSpaceReserved = false
                    setDefaultValue(true)
                    addPreference(this)
                }
            }

            PreferenceCategory(context).apply {
                title = getString(R.string.ui)
                key = "ui"
                isIconSpaceReserved = false
                layoutResource = R.layout.preference_category
                screen.addPreference(this)

                SwitchPreferenceCompat(context).apply {
                    key = MainActivity.BIG_COVER
                    title = getString(R.string.big_cover)
                    summary = getString(R.string.big_cover_summary)
                    layoutResource = R.layout.preference_switch
                    isIconSpaceReserved = false
                    setDefaultValue(false)
                    addPreference(this)
                }

                SwitchPreferenceCompat(context).apply {
                    key = FastScrollerHelper.SCROLL_BAR
                    title = getString(R.string.scroll_bar)
                    summary = getString(R.string.scroll_bar_summary)
                    layoutResource = R.layout.preference_switch
                    isIconSpaceReserved = false
                    setDefaultValue(false)
                    addPreference(this)
                }

                SwitchPreferenceCompat(context).apply {
                    key = MediaItemUtils.SHOW_BACKGROUND
                    title = getString(R.string.show_background)
                    summary = getString(R.string.show_background_summary)
                    layoutResource = R.layout.preference_switch
                    isIconSpaceReserved = false
                    setDefaultValue(true)
                    addPreference(this)
                }
            }

            PreferenceCategory(context).apply {
                title = getString(R.string.animation)
                key = "animation"
                isIconSpaceReserved = false
                layoutResource = R.layout.preference_category
                screen.addPreference(this)

                SwitchPreferenceCompat(context).apply {
                    key = MainActivity.BACK_ANIM
                    title = getString(R.string.back_animations)
                    summary = getString(R.string.back_animations_summary)
                    layoutResource = R.layout.preference_switch
                    isIconSpaceReserved = false
                    setDefaultValue(false)
                    addPreference(this)
                }

                SwitchPreferenceCompat(context).apply {
                    key = AnimationUtils.ANIMATIONS_KEY
                    title = getString(R.string.animations)
                    summary = getString(R.string.animations_summary)
                    layoutResource = R.layout.preference_switch
                    isIconSpaceReserved = false
                    setDefaultValue(true)
                    addPreference(this)
                }

                SwitchPreferenceCompat(context).apply {
                    key = AnimationUtils.SCROLL_ANIMATIONS_KEY
                    title = getString(R.string.scroll_animations)
                    summary = getString(R.string.scroll_animations_summary)
                    layoutResource = R.layout.preference_switch
                    isIconSpaceReserved = false
                    setDefaultValue(false)
                    addPreference(this)
                }
            }
        }

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                MainActivity.THEME_KEY, MainActivity.CUSTOM_THEME_KEY, MainActivity.COLOR_KEY, MainActivity.AMOLED_KEY,
                MainActivity.BIG_COVER, UiViewModel.NAVBAR_GRADIENT, UiViewModel.BACKGROUND_GRADIENT,
                    -> {
                    requireActivity().recreate()
                }

                MainActivity.BACK_ANIM -> {
                    val pref = preferenceScreen.findPreference<SwitchPreferenceCompat>(key)
                    val enabled = pref?.isChecked == true
                    val backActivity = MainActivity.Back::class.java.name
                    val mainActivity = MainActivity::class.java.name
                    changeEnabledComponent(
                        requireActivity(),
                        if (enabled) backActivity else mainActivity,
                        if (enabled) mainActivity else backActivity
                    )
                }
            }
        }

        override fun onResume() {
            super.onResume()
            preferenceManager.sharedPreferences!!
                .registerOnSharedPreferenceChangeListener(listener)
        }

        override fun onPause() {
            super.onPause()
            preferenceManager.sharedPreferences!!
                .unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    companion object {

        fun changeEnabledComponent(activity: Activity, enabled: String, disabled: String) {
            activity.packageManager.setComponentEnabledSetting(
                ComponentName(activity, enabled),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            activity.packageManager.setComponentEnabledSetting(
                ComponentName(activity, disabled),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}