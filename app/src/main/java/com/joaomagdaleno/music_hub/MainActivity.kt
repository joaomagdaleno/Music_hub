package com.joaomagdaleno.music_hub

import android.content.Context
import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.add
import androidx.fragment.app.commit
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.navigation.NavigationBarView
import com.joaomagdaleno.music_hub.databinding.ActivityMainBinding
import com.joaomagdaleno.music_hub.ui.common.UiViewModel
import com.joaomagdaleno.music_hub.ui.main.MainFragment
import com.joaomagdaleno.music_hub.ui.player.PlayerFragment
import com.joaomagdaleno.music_hub.ui.player.PlayerFragment.Companion.PLAYER_COLOR
import com.joaomagdaleno.music_hub.utils.ContextUtils
import com.joaomagdaleno.music_hub.utils.PermsUtils
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import com.joaomagdaleno.music_hub.utils.FileLogger
import org.koin.androidx.viewmodel.ext.android.viewModel

open class MainActivity : AppCompatActivity() {

    class Back : MainActivity()

    val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val uiViewModel by viewModel<UiViewModel>()
    private val feedViewModel by viewModel<com.joaomagdaleno.music_hub.ui.feed.FeedViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FileLogger.log("MainActivity", "onCreate() called. savedInstanceState=${savedInstanceState != null}")
        setTheme(getAppTheme(this))
        DynamicColors.applyToActivityIfAvailable(
            this, applyUiChanges(this, uiViewModel)
        )

        setContentView(binding.root)

        enableEdgeToEdge(
            SystemBarStyle.auto(TRANSPARENT, TRANSPARENT),
            if (UiUtils.isNightMode(this)) SystemBarStyle.dark(TRANSPARENT)
            else SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
        )

        UiUtils.setupNavBarAndInsets(this, uiViewModel, binding.root, binding.navView as NavigationBarView)
        UiUtils.setupPlayerBehavior(this, uiViewModel, binding.playerFragmentContainer)
        UiUtils.setupExceptionHandler(this, UiUtils.setupSnackBar(this, uiViewModel, binding.root))
        
        // Auto-refresh home feed when storage permission is granted
        PermsUtils.checkAppPermissions(this) {
            FileLogger.log("MainActivity", "Storage permission granted - refreshing all feeds")
            // Refresh all existing feed data
            feedViewModel.feedDataMap.values.forEach { it.refresh() }
            // Broadcast to fragments that may listen for this
            supportFragmentManager.setFragmentResult("permissionGranted", Bundle.EMPTY)
        }
        UiViewModel.configureAppUpdater(this)
        
        supportFragmentManager.commit {
            if (savedInstanceState != null) return@commit
            FileLogger.log("MainActivity", "Adding MainFragment and PlayerFragment")
            add<MainFragment>(R.id.navHostFragment, "main")
            add<PlayerFragment>(R.id.playerFragmentContainer, "player")
        }
        UiUtils.setupIntents(this, uiViewModel)
        FileLogger.log("MainActivity", "onCreate() complete")
    }

    companion object {
        const val THEME_KEY = "theme"
        const val AMOLED_KEY = "amoled"
        const val BIG_COVER = "big_cover"
        const val CUSTOM_THEME_KEY = "custom_theme"
        const val COLOR_KEY = "color"

        fun getAppTheme(context: Context): Int {
            val settings = ContextUtils.getSettings(context)
            val bigCover = settings.getBoolean(BIG_COVER, false)
            val amoled = settings.getBoolean(AMOLED_KEY, false)
            return when {
                amoled && bigCover -> R.style.AmoledBigCover
                amoled -> R.style.Amoled
                bigCover -> R.style.BigCover
                else -> R.style.Default
            }
        }

        fun defaultColor(context: Context) =
            ContextCompat.getColor(context, R.color.app_color)

        fun isAmoled(context: Context) = ContextUtils.getSettings(context).getBoolean(AMOLED_KEY, false)

        fun applyUiChanges(context: Context, uiViewModel: UiViewModel): DynamicColorsOptions {
            val settings = ContextUtils.getSettings(context)
            val mode = when (settings.getString(THEME_KEY, "system")) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(mode)

            val custom = settings.getBoolean(CUSTOM_THEME_KEY, true)
            val color = if (custom) settings.getInt(COLOR_KEY, defaultColor(context)) else null
            val playerColor = settings.getBoolean(PLAYER_COLOR, false)
            val customColor = uiViewModel.playerColors.value?.accent?.takeIf { playerColor }

            val builder = DynamicColorsOptions.Builder()
            (customColor ?: color)?.let { builder.setContentBasedSource(it) }
            return builder.build()
        }

        const val BACK_ANIM = "back_anim"
        fun getMainActivity(context: Context) = if (ContextUtils.getSettings(context).getBoolean(BACK_ANIM, false))
            Back::class.java else MainActivity::class.java
    }
}