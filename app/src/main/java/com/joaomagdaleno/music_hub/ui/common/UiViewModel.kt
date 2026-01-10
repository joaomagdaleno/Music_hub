package com.joaomagdaleno.music_hub.ui.common

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.forEach
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.view.updatePaddingRelative
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import com.google.android.material.color.MaterialColors
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigationrail.NavigationRailView
import com.joaomagdaleno.music_hub.MainActivity
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.playback.PlayerState
import com.joaomagdaleno.music_hub.ui.main.MainFragment
import com.joaomagdaleno.music_hub.ui.player.PlayerColors
import com.joaomagdaleno.music_hub.utils.CacheUtils.getFromCache
import com.joaomagdaleno.music_hub.utils.CacheUtils.saveToCache
import com.joaomagdaleno.music_hub.utils.ContextUtils.emit
import com.joaomagdaleno.music_hub.utils.ContextUtils.getSettings
import com.joaomagdaleno.music_hub.utils.ContextUtils.observe
import com.joaomagdaleno.music_hub.utils.ui.AnimationUtils.animateTranslation
import com.joaomagdaleno.music_hub.utils.ui.GradientDrawable
import com.joaomagdaleno.music_hub.utils.ui.UiUtils.dpToPx
import com.joaomagdaleno.music_hub.utils.ui.UiUtils.isRTL
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Lazily
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.min

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
    val navigation = MutableStateFlow(context.getFromCache("main_nav") ?: 0).also { flow ->
        viewModelScope.launch { flow.collect { context.saveToCache("main_nav", it) } }
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
    private val playerNavViewInsets = MutableStateFlow(Insets())
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
            if (context.isRTL()) Insets(end = width) else Insets(start = width)
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
            if (context.isRTL()) Insets(top, bottom, right, left)
            else Insets(top, bottom, left, right)
        }
        systemInsets.value = inset
    }

    fun setPlayerInsets(context: Context, isVisible: Boolean) {
        val insets = if (isVisible) {
            val height = context.resources.getDimensionPixelSize(R.dimen.collapsed_cover_size)
            Insets(bottom = height + 8.dpToPx(context))
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
    private var playerBackPressCallback: OnBackPressedCallback? = null
    private var moreBackPressCallback: OnBackPressedCallback? = null
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

    private var playerBehaviour = WeakReference<BottomSheetBehavior<View>>(null)
    fun changePlayerState(state: Int) {
        val behavior = playerBehaviour.get() ?: return
        if (state == STATE_HIDDEN) behavior.isHideable = true
        behavior.state = state
    }

    private var moreBehaviour = WeakReference<BottomSheetBehavior<View>>(null)
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
        val check = context.getSettings().getBoolean("check_for_updates", true)
        if (!check) return false
        val lastUpdateCheck = context.getFromCache<Long>("last_update_check") ?: 0
        return System.currentTimeMillis() - lastUpdateCheck > updateTime
    }

    private suspend fun message(app: App, msg: String) {
        app.messageFlow.emit(Message(msg))
    }

    private suspend fun awaitInstallation(file: File): Result<Unit> {
        installFileFlow.emit(file)
        return installedFlow.first { it.first == file }.second
    }

    fun checkForUpdates(activity: androidx.fragment.app.FragmentActivity, force: Boolean = false) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val app = activity.applicationContext as App
        if (!force && !shouldCheckForUpdates(activity)) return@launch

        activity.saveToCache("last_update_check", System.currentTimeMillis())
        activity.cleanupTempApks()

        if (force) message(app, activity.getString(R.string.checking_for_updates))

        runCatching {
            val appApk = com.joaomagdaleno.music_hub.utils.AppUpdater.updateApp(app)
            if (appApk != null) {
                activity.saveToCache("last_update_check", 0)
                awaitInstallation(appApk).getOrThrow()
            } else {
                 if (force) message(app, activity.getString(R.string.no_update_available_for_x, activity.getString(R.string.app_name)))
            }
        }.getOrElse {
            if (force) app.throwFlow.emit(it)
        }
    }

    companion object {
        fun androidx.fragment.app.FragmentActivity.configureAppUpdater() {
            val viewModel by org.koin.androidx.viewmodel.ext.android.viewModel<UiViewModel>()
            var currentFile: File? = null
            
            observe(viewModel.installFileFlow) {
                currentFile = it
                viewModel.installedFlow.emit(it to runCatching { 
                    com.joaomagdaleno.music_hub.utils.InstallationUtils.installApp(this, it) 
                })
            }

            lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
                 override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                     val file = currentFile ?: return
                     viewModel.run {
                         viewModelScope.launch {
                             installedFlow.emit(
                                 file to Result.failure(kotlinx.coroutines.CancellationException())
                             )
                         }
                     }
                 }
            })
            
            viewModel.checkForUpdates(this, false)
        }

        const val BACKGROUND_GRADIENT = "bg_gradient"
        fun Fragment.applyGradient(view: View, drawable: Drawable?) {
            val settings = requireContext().getSettings()
            val isGradient = settings.getBoolean(BACKGROUND_GRADIENT, true)
            val colorDrawable = if (isGradient) {
                drawable ?: MaterialColors.getColor(view, androidx.appcompat.R.attr.colorPrimary)
                    .toDrawable()
            } else null
            view.background = GradientDrawable.createBlurred(view, colorDrawable)
        }

        fun Fragment.applyInsets(vararg flows: Flow<*>, block: UiViewModel.(Insets) -> Unit) {
            val uiViewModel by activityViewModel<UiViewModel>()
            val flowsList = listOf(uiViewModel.combined) + flows
            observe(flowsList.merge()) { uiViewModel.block(uiViewModel.combined.value) }
        }

        fun Fragment.applyInsetsWithChild(
            appBar: View,
            child: View?,
            bottom: Int = 12,
            block: UiViewModel.(Insets) -> Unit = {}
        ) {
            val uiViewModel by activityViewModel<UiViewModel>()
            observe(uiViewModel.combined) { insets ->
                child?.updatePadding(
                    bottom = insets.bottom + bottom.dpToPx(child.context),
                )
                appBar.updatePaddingRelative(
                    top = insets.top,
                    start = insets.start,
                    end = insets.end
                )
                uiViewModel.block(insets)
            }
        }

        fun View.applyContentInsets(
            insets: Insets, horizontal: Int = 0, vertical: Int = 0, bottom: Int = 0
        ) {
            val horizontalPadding = horizontal.dpToPx(context)
            val verticalPadding = vertical.dpToPx(context)
            updatePaddingRelative(
                top = verticalPadding,
                bottom = insets.bottom + verticalPadding + bottom.dpToPx(context),
                start = insets.start + horizontalPadding,
                end = insets.end + horizontalPadding
            )
        }

        fun View.applyInsets(it: Insets, vertical: Int, horizontal: Int, bottom: Int = 0) {
            val verticalPadding = vertical.dpToPx(context)
            val horizontalPadding = horizontal.dpToPx(context)
            val bottomPadding = bottom.dpToPx(context)
            updatePaddingRelative(
                top = verticalPadding + it.top,
                bottom = bottomPadding + verticalPadding + it.bottom,
                start = horizontalPadding + it.start,
                end = horizontalPadding + it.end,
            )
        }

        fun View.applyInsets(it: Insets, paddingDp: Int = 0) {
            val padding = paddingDp.dpToPx(context)
            updatePaddingRelative(
                top = it.top + padding,
                bottom = it.bottom + padding,
                start = it.start + padding,
                end = it.end + padding,
            )
        }

        fun View.applyHorizontalInsets(it: Insets, isLandScape: Boolean = false) {
            updatePaddingRelative(
                start = if (!isLandScape) it.start else 0,
                end = it.end
            )
        }

        fun View.applyFabInsets(it: Insets, system: Insets, paddingDp: Int = 0) {
            val padding = paddingDp.dpToPx(context)
            updatePaddingRelative(
                bottom = it.bottom - system.bottom + padding,
                start = it.start + padding,
                end = it.end + padding,
            )
        }

        fun Fragment.applyBackPressCallback(callback: ((Int) -> Unit)? = null) {
            val activity = requireActivity()
            val viewModel by activity.viewModel<UiViewModel>()
            val backPress = viewModel.backPressCallback()
            observe(viewModel.playerSheetState) {
                backPress.isEnabled = it == STATE_EXPANDED
                callback?.invoke(it)
            }
            activity.onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPress)
        }

        private fun BottomSheetBehavior<View>.backPressCallback(
            onProgress: (Float) -> Unit = {},
        ) = object : OnBackPressedCallback(true) {
            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                startBackProgress(backEvent)
                onProgress(0f)
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                updateBackProgress(backEvent)
                onProgress(min(1f, backEvent.progress * 2))
            }

            override fun handleOnBackPressed() {
                handleBackInvoked()
                onProgress(0f)
            }

            override fun handleOnBackCancelled() {
                cancelBackProgress()
                onProgress(0f)
            }
        }

        const val NAVBAR_GRADIENT = "navbar_gradient"
        fun MainActivity.setupNavBarAndInsets(
            uiViewModel: UiViewModel,
            root: View,
            navView: NavigationBarView
        ) {
            val isRail = navView is NavigationRailView
            ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
                uiViewModel.setSystemInsets(this, insets)
                val navBarSize = uiViewModel.systemInsets.value.bottom
                val full = getSettings().getBoolean(NAVBAR_GRADIENT, true)
                GradientDrawable.applyNav(navView, isRail, navBarSize, !full)
                insets
            }

            navView.setOnItemSelectedListener {
                uiViewModel.navigation.value = uiViewModel.navIds.indexOf(it.itemId)
                true
            }
            navView.menu.forEach {
                findViewById<View>(it.itemId).setOnClickListener { _ ->
                    uiViewModel.run {
                        if (navigation.value == navIds.indexOf(it.itemId))
                            emit(navigationReselected, navIds.indexOf(it.itemId))
                    }
                    navView.selectedItemId = it.itemId
                }
            }

            fun animateNav(animate: Boolean) {
                val isMainFragment = uiViewModel.isMainFragment.value
                val insets =
                    uiViewModel.setPlayerNavViewInsets(this, isMainFragment, isRail)
                val isPlayerCollapsed = uiViewModel.playerSheetState.value != STATE_EXPANDED
                navView.animateTranslation(isRail, isMainFragment, isPlayerCollapsed, animate) {
                    uiViewModel.setNavInsets(insets)
                    if (isPlayerCollapsed) navView.updateLayoutParams<MarginLayoutParams> {
                        bottomMargin = -it.toInt()
                    }
                }
            }

            animateNav(false)
            supportFragmentManager.addOnBackStackChangedListener {
                val current = supportFragmentManager.findFragmentById(R.id.navHostFragment)
                val isMain = current is MainFragment
                uiViewModel.isMainFragment.value = isMain
                animateNav(true)
            }
            observe(uiViewModel.navigation) { navView.selectedItemId = uiViewModel.navIds[it] }
            observe(uiViewModel.playerSheetOffset) {
                if (!uiViewModel.isMainFragment.value) return@observe
                val offset = max(0f, it)
                if (isRail) navView.translationX = -navView.width * offset
                else navView.translationY = navView.height * offset
                navView.menu.forEach { item ->
                    findViewById<View>(item.itemId).apply {
                        translationX = 0f
                        translationY = 0f
                    }
                }
            }
        }

        fun isFinalState(state: Int): Boolean {
            return state == STATE_HIDDEN || state == STATE_COLLAPSED || state == STATE_EXPANDED
        }

        fun LifecycleOwner.setupPlayerBehavior(viewModel: UiViewModel, view: View) {
            val behavior = BottomSheetBehavior.from(view)
            viewModel.playerBehaviour = WeakReference(behavior)
            observe(viewModel.moreSheetState) { behavior.isDraggable = it == STATE_COLLAPSED }
            viewModel.playerBackPressCallback = behavior.backPressCallback {
                viewModel.playerBackProgress.value = it
            }

            val combined =
                viewModel.run { playerNavViewInsets.combine(systemInsets) { nav, _ -> nav } }
            observe(combined) {
                val bottomPadding = 8.dpToPx(view.context)
                val collapsedCoverSize =
                    view.resources.getDimensionPixelSize(R.dimen.collapsed_cover_size) + bottomPadding
                val peekHeight =
                    view.resources.getDimensionPixelSize(R.dimen.bottom_player_peek_height)
                val height = if (it.bottom == 0) collapsedCoverSize else peekHeight
                val newHeight = viewModel.systemInsets.value.bottom + height
                behavior.peekHeight = newHeight
                if (viewModel.playerSheetState.value != STATE_HIDDEN)
                    animateTranslation(view, behavior.peekHeight, newHeight)
            }
            val callback = object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    val expanded = newState == STATE_EXPANDED
                    behavior.isHideable = !expanded
                    viewModel.playerSheetState.value = newState
                    if (!isFinalState(newState)) return
                    viewModel.setPlayerInsets(view.context, newState != STATE_HIDDEN)
                    onSlide(view, if (newState == STATE_EXPANDED) 1f else 0f)
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    viewModel.playerSheetOffset.value = slideOffset
                }
            }
            val state = viewModel.playerSheetState.value
            callback.onStateChanged(view, state)
            callback.onSlide(view, if (state == STATE_EXPANDED) 1f else 0f)
            behavior.addBottomSheetCallback(callback)
        }

        fun setupPlayerMoreBehavior(viewModel: UiViewModel, view: View) {
            val behavior = BottomSheetBehavior.from(view)
            viewModel.moreBehaviour = WeakReference(behavior)
            val backPress = behavior.backPressCallback()
            val callback = object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    viewModel.moreSheetState.value = newState
                    viewModel.moreBackPressCallback =
                        backPress.takeIf { newState != STATE_COLLAPSED }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    val offset = max(0f, slideOffset)
                    viewModel.moreSheetOffset.value = offset
                }
            }
            val state = viewModel.moreSheetState.value
            callback.onStateChanged(view, state)
            callback.onSlide(view, if (state == STATE_EXPANDED) 1f else 0f)
            behavior.addBottomSheetCallback(callback)
        }

        fun SwipeRefreshLayout.configure(it: Insets = Insets()) {
            setProgressViewOffset(true, it.top, 72.dpToPx(context) + it.top)
        }
    }
}