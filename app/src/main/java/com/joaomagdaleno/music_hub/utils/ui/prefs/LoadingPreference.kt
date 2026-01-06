package com.joaomagdaleno.music_hub.utils.ui.prefs

import android.content.Context
import androidx.preference.Preference
import com.joaomagdaleno.music_hub.R

class LoadingPreference(context: Context) : Preference(context) {
    init {
        layoutResource = R.layout.item_loading
    }
}