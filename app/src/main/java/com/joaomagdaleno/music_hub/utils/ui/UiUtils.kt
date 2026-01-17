package com.joaomagdaleno.music_hub.utils.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.forEach
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.view.updatePaddingRelative
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigationrail.NavigationRailView
import com.google.android.material.snackbar.Snackbar
import com.joaomagdaleno.music_hub.MainActivity
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.helpers.ContinuationCallback.Companion.await
import com.joaomagdaleno.music_hub.common.models.Album
import com.joaomagdaleno.music_hub.common.models.Artist
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.Message
import com.joaomagdaleno.music_hub.common.models.Message.Action as MessageAction
import com.joaomagdaleno.music_hub.common.models.Playlist
import com.joaomagdaleno.music_hub.common.models.Track
import com.joaomagdaleno.music_hub.di.App
import com.joaomagdaleno.music_hub.download.exceptions.DownloadException
import com.joaomagdaleno.music_hub.download.tasks.BaseTask.Companion.getTitle
import com.joaomagdaleno.music_hub.playback.MediaItemUtils
import com.joaomagdaleno.music_hub.playback.exceptions.PlayerException
import com.joaomagdaleno.music_hub.ui.common.ExceptionFragment
import com.joaomagdaleno.music_hub.ui.common.SnackBarHandler
import com.joaomagdaleno.music_hub.ui.common.UiViewModel
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Insets
import com.joaomagdaleno.music_hub.ui.download.DownloadFragment
import com.joaomagdaleno.music_hub.ui.main.MainFragment
import com.joaomagdaleno.music_hub.ui.media.MediaFragment
import com.joaomagdaleno.music_hub.utils.ContextUtils.appVersion
import com.joaomagdaleno.music_hub.utils.ContextUtils.emit
import com.joaomagdaleno.music_hub.utils.ContextUtils.getSettings
import com.joaomagdaleno.music_hub.utils.ContextUtils.observe
import com.joaomagdaleno.music_hub.utils.Serializer
import com.joaomagdaleno.music_hub.utils.Serializer.toJson
import com.joaomagdaleno.music_hub.utils.ui.AnimationUtils.animateTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.lang.ref.WeakReference
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.BACKGROUND_GRADIENT
import android.content.res.Configuration.UI_MODE_NIGHT_NO

object UiUtils {

    fun hideSystemUi(activity: Activity, hide: Boolean) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (hide) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun configureAppBar(layout: AppBarLayout, block: (offset: Float) -> Unit) {
        val settings = getSettings(layout.context)
        val isGradient = settings.getBoolean(BACKGROUND_GRADIENT, true)
        val extra = if (isGradient) -191 else 0
        layout.addOnOffsetChangedListener { _, verticalOffset ->
            val offset = -verticalOffset / layout.totalScrollRange.toFloat()
            layout.background?.mutate()?.alpha = max(0, extra + (offset * 255).toInt())
            runCatching { block(offset) }
        }
    }

    fun isRTL(context: Context) =
        context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

    fun isLandscape(context: Context) =
        context.resources.configuration.orientation == ORIENTATION_LANDSCAPE

    fun isNightMode(context: Context) =
        context.resources.configuration.uiMode and UI_MODE_NIGHT_MASK != UI_MODE_NIGHT_NO

    fun dpToPx(context: Context, dp: Int) = (dp * context.resources.displayMetrics.density).toInt()

    fun resolveStyledDimension(context: Context, attr: Int): Int {
        val typed = context.theme.obtainStyledAttributes(intArrayOf(attr))
        val itemWidth = typed.getDimensionPixelSize(typed.getIndex(0), 0)
        return itemWidth
    }

    fun toTimeString(ms: Long): String {
        val seconds = (ms.toFloat() / 1000).roundToLong()
        val minutes = seconds / 60
        val hours = minutes / 60
        return if (hours > 0) {
            String.format(Locale.ENGLISH, "%02d:%02d:%02d", hours, minutes % 60, seconds % 60)
        } else {
            String.format(Locale.ENGLISH, "%02d:%02d", minutes, seconds % 60)
        }
    }

    fun marquee(textView: TextView) {
        textView.isSelected = true
        textView.ellipsize = TextUtils.TruncateAt.MARQUEE
        textView.maxLines = 1
        textView.marqueeRepeatLimit = -1
        textView.setHorizontallyScrolling(true)
    }

    fun configureBottomBar(fragment: BottomSheetDialogFragment, bar: View) {
        val view = fragment.requireView()
        val dialog = fragment.requireDialog() as BottomSheetDialog
        val behavior = dialog.behavior
        val barHeight = dpToPx(fragment.requireContext(), 72)
        var peek = 0
        var toScroll = 0
        var offset = 0f

        val callback = object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(p0: View, p1: Int) {}
            override fun onSlide(p0: View, p1: Float) {
                offset = p1.coerceAtLeast(0f)
                bar.y = peek + toScroll * offset
            }
        }
        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val screen = v.height - barHeight
            peek = (behavior.peekHeight - barHeight).coerceAtMost(screen)
            toScroll = screen - peek
            bar.y = peek + toScroll * offset
        }
        behavior.addBottomSheetCallback(callback)
    }

    fun applyGradient(fragment: Fragment, view: View, drawable: Drawable?) {
        val settings = getSettings(fragment.requireContext())
        val isGradient = settings.getBoolean(BACKGROUND_GRADIENT, true)
        val colorDrawable = if (isGradient) {
            drawable ?: MaterialColors.getColor(view, androidx.appcompat.R.attr.colorPrimary)
                .toDrawable()
        } else null
        view.background = GradientDrawable.createBlurred(view, colorDrawable)
    }

    fun applyInsets(fragment: Fragment, vararg flows: Flow<*>, block: UiViewModel.(Insets) -> Unit) {
        val viewModel by fragment.activityViewModels<UiViewModel>()
        val flowsList = listOf(viewModel.combined) + flows
        observe(fragment, flowsList.merge()) { viewModel.block(viewModel.combined.value) }
    }

    fun applyInsetsWithChild(
        fragment: Fragment,
        appBar: View,
        child: View?,
        bottom: Int = 12,
        block: UiViewModel.(Insets) -> Unit = {}
    ) {
        val viewModel by fragment.activityViewModels<UiViewModel>()
        observe(fragment, viewModel.combined) { insets ->
            child?.updatePaddingRelative(
                bottom = insets.bottom + dpToPx(child.context, bottom),
            )
            appBar.updatePaddingRelative(
                top = insets.top,
                start = insets.start,
                end = insets.end
            )
            viewModel.block(insets)
        }
    }

    fun applyContentInsets(
        view: View, insets: Insets, horizontal: Int = 0, vertical: Int = 0, bottom: Int = 0
    ) {
        val horizontalPadding = dpToPx(view.context, horizontal)
        val verticalPadding = dpToPx(view.context, vertical)
        view.updatePaddingRelative(
            top = verticalPadding,
            bottom = insets.bottom + verticalPadding + dpToPx(view.context, bottom),
            start = insets.start + horizontalPadding,
            end = insets.end + horizontalPadding
        )
    }

    fun applyInsets(view: View, it: Insets, vertical: Int, horizontal: Int, bottom: Int = 0) {
        val verticalPadding = dpToPx(view.context, vertical)
        val horizontalPadding = dpToPx(view.context, horizontal)
        val bottomPadding = dpToPx(view.context, bottom)
        view.updatePaddingRelative(
            top = verticalPadding + it.top,
            bottom = bottomPadding + verticalPadding + it.bottom,
            start = horizontalPadding + it.start,
            end = horizontalPadding + it.end,
        )
    }

    fun applyInsets(view: View, it: Insets, paddingDp: Int = 0) {
        val padding = dpToPx(view.context, paddingDp)
        view.updatePaddingRelative(
            top = it.top + padding,
            bottom = it.bottom + padding,
            start = it.start + padding,
            end = it.end + padding,
        )
    }

    fun applyHorizontalInsets(view: View, it: Insets, isLandScape: Boolean = false) {
        view.updatePaddingRelative(
            start = if (!isLandScape) it.start else 0,
            end = it.end
        )
    }

    fun applyFabInsets(view: View, it: Insets, system: Insets, paddingDp: Int = 0) {
        val padding = dpToPx(view.context, paddingDp)
        view.updatePaddingRelative(
            bottom = it.bottom - system.bottom + padding,
            start = it.start + padding,
            end = it.end + padding,
        )
    }

    fun applyBackPressCallback(fragment: Fragment, callback: ((Int) -> Unit)? = null) {
        val activity = fragment.requireActivity()
        val viewModel by activity.viewModel<UiViewModel>()
        val backPress = viewModel.backPressCallback()
        observe(fragment, viewModel.playerSheetState) {
            backPress.isEnabled = it == STATE_EXPANDED
            callback?.invoke(it)
        }
        activity.onBackPressedDispatcher.addCallback(fragment.viewLifecycleOwner, backPress)
    }

    fun isFinalState(state: Int): Boolean {
        return state == STATE_HIDDEN || state == STATE_COLLAPSED || state == STATE_EXPANDED
    }

    fun configureSwipeRefresh(layout: SwipeRefreshLayout, it: Insets = Insets()) {
        layout.setProgressViewOffset(true, it.top, dpToPx(layout.context, 72) + it.top)
    }

    fun setupPlayerBehavior(owner: LifecycleOwner, viewModel: UiViewModel, view: View) {
        val behavior = BottomSheetBehavior.from(view)
        viewModel.playerBehaviour = WeakReference(behavior)
        observe(owner, viewModel.moreSheetState) { behavior.isDraggable = it == STATE_COLLAPSED }
        viewModel.playerBackPressCallback = createBehaviorBackPressCallback(behavior) {
            viewModel.playerBackProgress.value = it
        }

        val combined =
            viewModel.run { playerNavViewInsets.combine(systemInsets) { nav: Insets, _: Insets -> nav } }
        observe(owner, combined) { it: Insets ->
            val bottomPadding = dpToPx(view.context, 8)
            val collapsedCoverSize =
                view.resources.getDimensionPixelSize(com.joaomagdaleno.music_hub.R.dimen.collapsed_cover_size) + bottomPadding
            val peekHeight =
                view.resources.getDimensionPixelSize(com.joaomagdaleno.music_hub.R.dimen.bottom_player_peek_height)
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
        val backPress = createBehaviorBackPressCallback(behavior)
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

    private fun createBehaviorBackPressCallback(
        behavior: BottomSheetBehavior<View>,
        onProgress: (Float) -> Unit = {},
    ) = object : OnBackPressedCallback(true) {
        override fun handleOnBackStarted(backEvent: BackEventCompat) {
            behavior.startBackProgress(backEvent)
            onProgress(0f)
        }

        override fun handleOnBackProgressed(backEvent: BackEventCompat) {
            behavior.updateBackProgress(backEvent)
            onProgress(min(1f, backEvent.progress * 2))
        }

        override fun handleOnBackPressed() {
            behavior.handleBackInvoked()
            onProgress(0f)
        }

        override fun handleOnBackCancelled() {
            behavior.cancelBackProgress()
            onProgress(0f)
        }
    }

    inline fun <reified T : Fragment> openFragment(
        fragment: Fragment, view: View? = null, bundle: Bundle? = null,
    ) {
        val viewModel by fragment.activityViewModels<UiViewModel>()
        openFragment<T>(R.id.navHostFragment, fragment.parentFragmentManager, viewModel, view, bundle)
    }

    inline fun <reified T : Fragment> openFragment(
        cont: Int,
        manager: FragmentManager,
        viewModel: UiViewModel,
        view: View? = null,
        bundle: Bundle? = null,
    ) {
        viewModel.collapsePlayer()
        manager.commit {
            setReorderingAllowed(true)
            addToBackStack(null)
            val fragment = createFragment<T>(bundle)
            val old = manager.findFragmentById(cont)
            if (old != null) hide(old)
            add(cont, fragment)
            setPrimaryNavigationFragment(fragment)
        }
    }

    inline fun <reified T : Fragment> createFragment(
        bundle: Bundle? = null
    ): T = T::class.java.getDeclaredConstructor().newInstance().apply { arguments = bundle }

    inline fun <reified T : Fragment> openFragment(
        activity: FragmentActivity, view: View? = null, bundle: Bundle? = null, cont: Int = R.id.navHostFragment
    ) {
        val oldFragment = activity.supportFragmentManager.findFragmentById(cont)
        if (oldFragment == null) {
            val viewModel by activity.viewModel<UiViewModel>()
            openFragment<T>(cont, activity.supportFragmentManager, viewModel, view, bundle)
        } else openFragment<T>(oldFragment, view, bundle)
    }

    inline fun <reified F : Fragment> addIfNull(
        fragment: Fragment, id: Int, tag: String, args: Bundle? = null
    ) {
        fragment.childFragmentManager.run {
            if (findFragmentByTag(tag) == null) commit {
                val frag = createFragment<F>(args)
                add(id, frag, tag)
            }
        }
    }

    fun setupIntents(
        activity: MainActivity,
        uiViewModel: UiViewModel,
    ) {
        activity.addOnNewIntentListener { onIntent(activity, uiViewModel, it) }
        onIntent(activity, uiViewModel, activity.intent)
    }

    fun onIntent(activity: FragmentActivity, uiViewModel: UiViewModel, intent: Intent?) {
        activity.intent = null
        intent ?: return
        val fromNotif = intent.hasExtra("fromNotification")
        if (fromNotif) uiViewModel.run {
            if (playerSheetState.value == STATE_HIDDEN) return@run
            changePlayerState(STATE_EXPANDED)
            changeMoreState(STATE_COLLAPSED)
            return
        }
        val fromDownload = intent.hasExtra("fromDownload")
        if (fromDownload) {
            uiViewModel.selectedSettingsTab.value = 0
            openFragment<DownloadFragment>(activity)
            return
        }
        val uri = intent.data
        when (uri?.scheme) {
            "echo" -> runCatching { openItemFragmentFromUri(activity, uri) }
        }
    }

    fun openItemFragmentFromUri(activity: FragmentActivity, uri: Uri) {
        when (val sourceType = uri.host) {
            "music" -> {
                val origin = uri.pathSegments.firstOrNull() ?: "internal"
                val type = uri.pathSegments.getOrNull(1)
                val id = uri.pathSegments.getOrNull(2) ?: return
                
                val name = uri.getQueryParameter("name").orEmpty()
                val item: EchoMediaItem? = when (type) {
                    "artist" -> Artist(id, name)
                    "track" -> Track(id, name)
                    "album" -> Album(id, name)
                    "playlist" -> Playlist(id, name, false)
                    else -> null
                }
                if (item == null) {
                    createSnack(activity, "Invalid item type")
                    return
                }
                openFragment<MediaFragment>(activity, null, MediaFragment.getBundle(origin, item, false))
            }

            else -> {
                createSnack(activity, "Opening $sourceType source is not possible")
            }
        }
    }

    fun getExceptionTitle(context: Context, throwable: Throwable): String? = when (throwable) {
        is UnknownHostException, is UnresolvedAddressException -> context.getString(R.string.no_internet)
        is PlayerException -> "${throwable.mediaItem?.let { MediaItemUtils.getTrack(it) }?.title}: ${getFinalExceptionTitle(context, throwable.cause)}"
        is DownloadException -> {
            val title = getTitle(context, throwable.type, throwable.downloadEntity.track.getOrNull()?.title ?: "???")
            "${title}: ${getFinalExceptionTitle(context, throwable.cause)}"
        }
        else -> null
    }

    fun getFinalExceptionTitle(context: Context, throwable: Throwable?): String? {
        throwable ?: return null
        return getExceptionTitle(context, throwable) ?: throwable.cause?.let { getFinalExceptionTitle(context, it) } ?: throwable.message
    }

    private fun getExceptionDetails(throwable: Throwable): String? = when (throwable) {
        is PlayerException -> throwable.mediaItem?.let {
            """
            Track: ${Serializer.toJson(MediaItemUtils.getTrack(it))}
            Stream: ${it.run { Serializer.toJson(MediaItemUtils.getTrack(it).servers.getOrNull(MediaItemUtils.getServerIndex(it))) }}
        """.trimIndent()
        }
        is DownloadException -> """
            Type: ${throwable.type}
            Track: ${Serializer.toJson(throwable.downloadEntity)}
        """.trimIndent()
        is Serializer.DecodingException -> "JSON: ${throwable.json}"
        else -> null
    }

    private fun getFinalExceptionDetails(throwable: Throwable): String = buildString {
        getExceptionDetails(throwable)?.let { appendLine(it) }
        throwable.cause?.let { append(getFinalExceptionDetails(it)) }
    }

    private fun getExceptionStackTrace(throwable: Throwable): String = buildString {
        appendLine("Version: ${appVersion()}")
        appendLine(getFinalExceptionDetails(throwable))
        appendLine("---Stack Trace---")
        appendLine(throwable.stackTraceToString())
    }

    fun toExceptionData(context: Context, throwable: Throwable) = run {
        val title = getFinalExceptionTitle(context, throwable) ?: context.getString(
            R.string.error_x,
            throwable.message ?: throwable::class.run { simpleName ?: java.name }
        )
        ExceptionData(title, getExceptionStackTrace(throwable))
    }

    fun getExceptionMessage(activity: FragmentActivity, throwable: Throwable, view: View?): Message {
        val title = getFinalExceptionTitle(activity, throwable) ?: activity.getString(
            R.string.error_x,
            throwable.message ?: throwable::class.run { simpleName ?: java.name }
        )
        return Message(
            message = title,
            MessageAction(activity.getString(R.string.view)) {
                runCatching { openException(activity, ExceptionData(title, getExceptionStackTrace(throwable)), view) }
            }
        )
    }

    fun openException(activity: FragmentActivity, data: ExceptionData, view: View? = null) {
        openFragment<ExceptionFragment>(activity, view, ExceptionFragment.getBundle(data))
    }

    fun setupExceptionHandler(activity: MainActivity, handler: SnackBarHandler) {
        observe(activity, handler.app.throwFlow) { throwable ->
            val message = getExceptionMessage(activity, throwable, null)
            handler.create(message)
        }
    }

    val exceptionClient = OkHttpClient()
    suspend fun getPasteLink(data: ExceptionData) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://paste.rs")
            .post(data.trace.toRequestBody())
            .build()
        runCatching { com.joaomagdaleno.music_hub.common.helpers.ContinuationCallback.await(exceptionClient.newCall(request)).body?.string() }
    }

    fun setupSnackBar(
        activity: MainActivity, uiViewModel: UiViewModel, root: View
    ): SnackBarHandler {
        val handler by activity.inject<SnackBarHandler>()
        val padding = dpToPx(activity, 8)
        @Suppress("IDENTITY_SENSITIVE_OPERATIONS_WITH_VALUE_TYPE")
        val snackBars = WeakHashMap<Int, Snackbar>()
        fun updateInsets(snackBar: Snackbar) {
            snackBar.view.updateLayoutParams<MarginLayoutParams> {
                val insets = uiViewModel.systemInsets.value
                val snackbarInsets = uiViewModel.getSnackbarInsets()
                marginStart = insets.start + snackbarInsets.start + padding
                marginEnd = insets.end + snackbarInsets.end + padding
                bottomMargin = snackbarInsets.bottom + padding
            }
        }
        fun createSnackBar(message: Message) {
            val snackBar = Snackbar.make(root, message.message, Snackbar.LENGTH_LONG)
            snackBar.animationMode = Snackbar.ANIMATION_MODE_SLIDE
            updateInsets(snackBar)
            message.action?.run { snackBar.setAction(name) { handler() } }
            snackBars[message.hashCode()] = snackBar
            snackBar.addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    snackBars.remove(message.hashCode())
                    activity.lifecycleScope.launch {
                        handler.remove(message, event != Snackbar.Callback.DISMISS_EVENT_MANUAL)
                    }
                }
            })
            snackBar.show()
        }

        observe(activity, handler.app.messageFlow) { message ->
            createSnackBar(message)
        }
        observe(activity, uiViewModel.combined) { _ ->
            snackBars.values.forEach { updateInsets(it) }
        }
        return handler
    }

    fun createSnack(fragment: Fragment, message: Message) {
        val handler by fragment.inject<SnackBarHandler>()
        fragment.lifecycleScope.launch { handler.create(message) }
    }

    fun createSnack(fragment: Fragment, message: String) {
        createSnack(fragment, Message(message))
    }

    fun createSnack(fragment: Fragment, message: Int) {
        createSnack(fragment, fragment.getString(message))
    }

    fun createSnack(activity: FragmentActivity, message: Message) {
        val handler by activity.inject<SnackBarHandler>()
        activity.lifecycleScope.launch { handler.create(message) }
    }

    fun createSnack(activity: FragmentActivity, message: String) {
        createSnack(activity, Message(message))
    }

    fun setupNavBarAndInsets(
        activity: MainActivity,
        uiViewModel: UiViewModel,
        root: View,
        navView: NavigationBarView
    ) {
        val isRail = navView is NavigationRailView
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            uiViewModel.setSystemInsets(activity, insets)
            val navBarSize = uiViewModel.systemInsets.value.bottom
            val full = getSettings(activity).getBoolean(UiViewModel.NAVBAR_GRADIENT, true)
            GradientDrawable.applyNav(navView, isRail, navBarSize, !full)
            insets
        }

        navView.setOnItemSelectedListener {
            uiViewModel.navigation.value = uiViewModel.navIds.indexOf(it.itemId)
            true
        }
        navView.menu.forEach {
            activity.findViewById<View>(it.itemId).setOnClickListener { _ ->
                uiViewModel.run {
                    if (navigation.value == navIds.indexOf(it.itemId))
                        emit(activity, navigationReselected, navIds.indexOf(it.itemId))
                }
                navView.selectedItemId = it.itemId
            }
        }

        fun animateNav(animate: Boolean) {
            val isMainFragment = uiViewModel.isMainFragment.value
            val insets =
                uiViewModel.setPlayerNavViewInsets(activity, isMainFragment, isRail)
            val isPlayerCollapsed = uiViewModel.playerSheetState.value != STATE_EXPANDED
            AnimationUtils.animateTranslation(navView, isRail, isMainFragment, isPlayerCollapsed, animate) { offset ->
                uiViewModel.setNavInsets(insets)
                if (isPlayerCollapsed) navView.updateLayoutParams<MarginLayoutParams> {
                    bottomMargin = -offset.toInt()
                }
            }
        }

        animateNav(false)
        activity.supportFragmentManager.addOnBackStackChangedListener {
            val current = activity.supportFragmentManager.findFragmentById(R.id.navHostFragment)
            val isMain = current is MainFragment
            uiViewModel.isMainFragment.value = isMain
            animateNav(true)
        }
        observe(activity, uiViewModel.navigation) { navView.selectedItemId = uiViewModel.navIds[it] }
        observe(activity, uiViewModel.playerSheetOffset) {
            if (!uiViewModel.isMainFragment.value) return@observe
            val offset = max(0f, it)
            if (isRail) navView.translationX = -navView.width * offset
            else navView.translationY = navView.height * offset
            navView.menu.forEach { item ->
                activity.findViewById<View>(item.itemId).apply {
                    translationX = 0f
                    translationY = 0f
                }
            }
        }
    }
}
