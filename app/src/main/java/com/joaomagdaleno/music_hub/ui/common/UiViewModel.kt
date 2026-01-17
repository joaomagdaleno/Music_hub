package com.joaomagdaleno.music_hub.ui.common

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import com.google.android.material.color.MaterialColors
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.playback.PlayerState
import com.joaomagdaleno.music_hub.ui.player.PlayerColors
import com.joaomagdaleno.music_hub.utils.CacheUtils
import com.joaomagdaleno.music_hub.utils.ContextUtils
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import java.io.File
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.common.models.Message
import com.joaomagdaleno.music_hub.utils.InstallationUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Lazily
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import java.lang.ref.WeakReference
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.koin.androidx.viewmodel.ext.android.viewModel

class UiViewModel(
    context: Context,
    private val playerState: PlayerState
) : ViewModel() {

    data class Insets(
        val top: Int = 0,
        val bottom: Int = 0,
        val start: Int = 0,
        val end: Int = 0
    ) {
        fun add(vararg insets: Insets) = insets.fold(this) { acc, it ->
            Insets(
                acc.top + it.top,
                acc.bottom + it.bottom,
                acc.start + it.start,
                acc.end + it.end
            )
        }
    }

    var currentAppColor: String? = null
    val navigation = MutableStateFlow(CacheUtils.getFromCache<Int>(context, "main_nav") ?: 0).also { flow ->
        viewModelScope.launch { flow.collect { CacheUtils.saveToCache(context, "main_nav", it) } }
    }
    val selectedSettingsTab = MutableStateFlow(0)
    val navigationReselected = MutableSharedFlow<Int>()
    val navIds = listOf(
        R.id.homeFragment,
        R.id.searchFragment,
        R.id.libraryFragment
    )

    val currentNavBackground = MutableStateFlow<Drawable?>(null)
    
    private val sourceColor = navigation.map { nav ->
        val colorAttr = when (nav) {
            1 -> R.attr.navSearchColor
            2 -> R.attr.navLibraryColor
            else -> R.attr.navHomeColor
        }
        val color = runCatching { MaterialColors.getColor(context, colorAttr, 0) }.getOrNull()
        color?.toDrawable()
    }

    private val navViewInsets = MutableStateFlow(Insets())
    val playerNavViewInsets = MutableStateFlow(Insets())
    private val playerInsets = MutableStateFlow(Insets())
    val systemInsets = MutableStateFlow(Insets())
    val isMainFragment = MutableStateFlow(true)

    val combined = systemInsets.combine(navViewInsets) { system, nav ->
        if (isMainFragment.value) system.add(nav) else system
    }.combine(playerInsets) { system, player ->
        system.add(player)
    }.stateIn(viewModelScope, Lazily, Insets())

    fun getCombined() = (if (isMainFragment.value) systemInsets.value.add(navViewInsets.value)
    else systemInsets.value).add(playerInsets.value)

    fun getSnackbarInsets(): Insets {
        if (playerSheetState.value == STATE_EXPANDED) return Insets()
        if (isMainFragment.value) return navViewInsets.value.add(playerInsets.value)
        return playerInsets.value
    }

    fun setPlayerNavViewInsets(context: Context, isNavVisible: Boolean, isRail: Boolean): Insets {
        val insets = context.resources.run {
            if (!isNavVisible) return@run Insets()
            val height = getDimensionPixelSize(R.dimen.nav_height)
            if (!isRail) return@run Insets(bottom = height)
            val width = getDimensionPixelSize(R.dimen.nav_width)
            if (UiUtils.isRTL(context)) Insets(end = width) else Insets(start = width)
        }
        playerNavViewInsets.value = insets
        return insets
    }

    fun setNavInsets(insets: Insets) {
        navViewInsets.value = insets
    }

    fun setSystemInsets(context: Context, insets: WindowInsetsCompat) {
        val system = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
        val display = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        val inset = system.run {
            val top = if (top > 0) top else display.top
            val bottom = if (bottom > 0) bottom else display.bottom
            val left = if (left > 0) left else display.left
            val right = if (right > 0) right else display.right
            if (UiUtils.isRTL(context)) Insets(top, bottom, right, left)
            else Insets(top, bottom, left, right)
        }
        systemInsets.value = inset
    }

    fun setPlayerInsets(context: Context, isVisible: Boolean) {
        val insets = if (isVisible) {
            val height = context.resources.getDimensionPixelSize(R.dimen.collapsed_cover_size)
            Insets(bottom = height + UiUtils.dpToPx(context, 8))
        } else Insets()
        playerInsets.value = insets
    }

    val playerBgVisible = MutableStateFlow(false)
    private fun getState() =
        if (playerState.current.value != null) STATE_COLLAPSED else STATE_HIDDEN

    val playerSheetState = MutableStateFlow(getState())
    val playerSheetOffset = MutableStateFlow(0f)
    val moreSheetState = MutableStateFlow(STATE_COLLAPSED)
    val moreSheetOffset = MutableStateFlow(0f)
    val playerBackProgress = MutableStateFlow(0f)
    var playerBackPressCallback: OnBackPressedCallback? = null
    var moreBackPressCallback: OnBackPressedCallback? = null
    fun backPressCallback() = object : OnBackPressedCallback(false) {
        val backPress
            get() = moreBackPressCallback ?: playerBackPressCallback

        override fun handleOnBackStarted(backEvent: BackEventCompat) {
            backPress?.handleOnBackStarted(backEvent)
        }

        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            backPress?.handleOnBackProgressed(backEvent)
        }

        override fun handleOnBackPressed() {
            backPress?.handleOnBackPressed()
        }

        override fun handleOnBackCancelled() {
            backPress?.handleOnBackCancelled()
        }
    }

    fun collapsePlayer() {
        changePlayerState(getState())
        changeMoreState(STATE_COLLAPSED)
    }

    var playerBehaviour = WeakReference<BottomSheetBehavior<View>>(null)
    fun changePlayerState(state: Int) {
        val behavior = playerBehaviour.get() ?: return
        if (state == STATE_HIDDEN) behavior.isHideable = true
        behavior.state = state
    }

    var moreBehaviour = WeakReference<BottomSheetBehavior<View>>(null)
    fun changeMoreState(state: Int) {
        val behavior = moreBehaviour.get() ?: return
        behavior.state = state
    }

    var lastMoreTab = R.id.queue
    var playerControlsHeight = MutableStateFlow(0)
    val playerDrawable = MutableStateFlow<Drawable?>(null)
    val playerColors = MutableStateFlow<PlayerColors?>(null)

    val mainBgDrawable = playerDrawable.combine(sourceColor) { a, b -> a ?: b }

    fun changeBgVisible(show: Boolean) {
        playerBgVisible.value = show
        if (!show && moreSheetState.value == STATE_EXPANDED)
            changeMoreState(STATE_COLLAPSED)
    }

    // App Update Logic
    val installFileFlow = MutableSharedFlow<File>()
    val installedFlow = MutableSharedFlow<Pair<File, Result<Unit>>>()

    private val updateTime = 1000 * 60 * 60 * 2L // Check every 2hrs

    private fun shouldCheckForUpdates(context: Context): Boolean {
        val check = ContextUtils.getSettings(context).getBoolean("check_for_updates", true)
        if (!check) return false
        val lastUpdateCheck = CacheUtils.getFromCache<Long>(context, "last_update_check") ?: 0
        return System.currentTimeMillis() - lastUpdateCheck > updateTime
    }

    private suspend fun message(app: App, msg: String) {
        app.messageFlow.emit(Message(msg))
    }

    private suspend fun awaitInstallation(file: File): Result<Unit> {
        installFileFlow.emit(file)
        return installedFlow.first { it.first == file }.second
    }

    fun checkForUpdates(activity: FragmentActivity, force: Boolean = false) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val app = activity.applicationContext as App
        if (!force && !shouldCheckForUpdates(activity)) return@launch

        CacheUtils.saveToCache(activity, "last_update_check", System.currentTimeMillis())
        ContextUtils.cleanupTempApks(activity)

        if (force) message(app, activity.getString(R.string.checking_for_updates))

        runCatching {
            val appApk = com.joaomagdaleno.music_hub.utils.AppUpdater.updateApp(app)
            if (appApk != null) {
                CacheUtils.saveToCache(activity, "last_update_check", 0L)
                awaitInstallation(appApk).getOrThrow()
            } else {
                 if (force) message(app, activity.getString(R.string.no_update_available_for_x, activity.getString(R.string.app_name)))
            }
        }.getOrElse {
            if (force) app.throwFlow.emit(it)
        }
    }

    companion object {
        const val BACKGROUND_GRADIENT = "bg_gradient"
        const val NAVBAR_GRADIENT = "navbar_gradient"

        fun configureAppUpdater(activity: FragmentActivity) {
            val viewModel by activity.viewModel<UiViewModel>()
            var currentFile: File? = null
            
            ContextUtils.observe(activity, viewModel.installFileFlow) {
                currentFile = it
                activity.lifecycleScope.launch {
                    viewModel.installedFlow.emit(it to runCatching { 
                        InstallationUtils.installApp(activity, it) 
                    })
                }
            }

            activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
                 override fun onDestroy(owner: LifecycleOwner) {
                     val file = currentFile ?: return
                     val scope = viewModel.viewModelScope
                     scope.launch {
                         viewModel.installedFlow.emit(
                             file to Result.failure<Unit>(kotlinx.coroutines.CancellationException())
                         )
                     }
                 }
            })
            
            viewModel.checkForUpdates(activity, false)
        }
    }
}
