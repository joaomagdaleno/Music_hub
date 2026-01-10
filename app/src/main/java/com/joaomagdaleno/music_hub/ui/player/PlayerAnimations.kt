package com.joaomagdaleno.music_hub.ui.player

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.databinding.FragmentPlayerBinding
import com.joaomagdaleno.music_hub.ui.common.UiViewModel
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.applyHorizontalInsets
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.applyInsets
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.getCombined
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.isFinalState
import com.joaomagdaleno.music_hub.utils.ContextUtils.emit
import com.joaomagdaleno.music_hub.utils.ContextUtils.observe
import com.joaomagdaleno.music_hub.utils.ui.AnimationUtils.animateVisibility
import com.joaomagdaleno.music_hub.utils.ui.UiUtils.dpToPx
import com.joaomagdaleno.music_hub.utils.ui.UiUtils.hideSystemUi
import com.joaomagdaleno.music_hub.utils.ui.UiUtils.isLandscape
import com.joaomagdaleno.music_hub.utils.ui.UiUtils.isRTL
import kotlin.math.max
import kotlin.math.min
import androidx.fragment.app.FragmentActivity

fun View.configurePlayerOutline(
    uiViewModel: UiViewModel,
    lifecycleOwner: LifecycleOwner,
    collapseHeight: Int
) {
    val context = this.context
    val padding = 8.dpToPx(context)
    var currHeight = collapseHeight
    var currRound = padding.toFloat()
    var currRight = 0
    var currLeft = 0
    
    this.outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(
                currLeft, 0, currRight, currHeight, currRound
            )
        }
    }
    this.clipToOutline = true

    var leftPadding = 0
    var rightPadding = 0

    val maxElevation = 4.dpToPx(context).toFloat()
    fun updateOutline() {
        val offset = max(0f, uiViewModel.playerSheetOffset.value)
        val inv = 1 - offset
        val curve = offset * offset // Non-linear for snappier finish
        val invCurve = 1 - curve
        
        this.elevation = maxElevation * inv
        currHeight = collapseHeight + ((this.height - collapseHeight) * offset).toInt()
        currLeft = (leftPadding * invCurve).toInt()
        currRight = this.width - (rightPadding * invCurve).toInt()
        currRound = max(padding * invCurve, padding * uiViewModel.playerBackProgress.value * 2)
        this.invalidateOutline()
    }
    lifecycleOwner.observe(uiViewModel.combined) {
        leftPadding = (if (context.isRTL()) it.end else it.start) + padding
        rightPadding = (if (context.isRTL()) it.start else it.end) + padding
        updateOutline()
    }
    lifecycleOwner.observe(uiViewModel.playerBackProgress) { updateOutline() }
    lifecycleOwner.observe(uiViewModel.playerSheetOffset) { updateOutline() }
    this.doOnLayout { updateOutline() }
}

fun FragmentPlayerBinding.configurePlayerCollapsing(
    uiViewModel: UiViewModel,
    viewModel: PlayerViewModel,
    adapter: PlayerTrackAdapter,
    lifecycleOwner: LifecycleOwner,
    collapseHeight: Int,
    activity: FragmentActivity
) {
    playerCollapsedContainer.root.clipToOutline = true

    val context = root.context
    val collapsedTopPadding = 8.dpToPx(context)
    var currRound = collapsedTopPadding.toFloat()
    var currTop = 0
    var currBottom = collapseHeight
    var currRight = 0
    var currLeft = 0

    val view = viewPager
    view.outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(
                currLeft, currTop, currRight, currBottom, currRound
            )
        }
    }
    view.clipToOutline = true

    val extraEndPadding = 108.dpToPx(context)
    var leftPadding = 0
    var rightPadding = 0
    val isLandscape = context.isLandscape()
    
    fun updateCollapsed() {
        val (collapsedY, offset, collapsedOffset) = uiViewModel.run {
            if (playerSheetState.value == STATE_EXPANDED) {
                val offset = moreSheetOffset.value
                Triple(systemInsets.value.top, offset, if (isLandscape) 0f else offset)
            } else {
                val offset = 1 - max(0f, playerSheetOffset.value)
                Triple(-collapsedTopPadding, offset, offset)
            }
        }
        val collapsedInv = 1 - collapsedOffset
        playerCollapsedContainer.root.run {
            translationY = collapsedY - collapseHeight * collapsedInv * 2
            alpha = collapsedOffset * 2
            translationZ = -1f * collapsedInv
        }
        bgCollapsed.run {
            translationY = collapsedY - collapseHeight * collapsedInv * 2
            alpha = min(1f, collapsedOffset * 2) - 0.5f
        }
        val alphaInv = 1 - min(1f, offset * 3)
        expandedToolbar.run {
            translationY = collapseHeight * offset * 2
            alpha = alphaInv
            isVisible = offset < 1
            translationZ = -1f * offset
        }
        playerControls.root.run {
            translationY = collapseHeight * offset * 2
            alpha = alphaInv
            isVisible = offset < 1
        }
        currTop = uiViewModel.run {
            val top = if (playerSheetState.value != STATE_EXPANDED) 0
            else collapsedTopPadding + systemInsets.value.top
            (top * max(0f, (collapsedOffset - 0.75f) * 4)).toInt()
        }
        val bot = currTop + collapseHeight
        currBottom = bot + ((view.height - bot) * collapsedInv).toInt()
        currLeft = (leftPadding * collapsedOffset).toInt()
        currRight = view.width - (rightPadding * collapsedOffset).toInt()
        currRound = collapsedTopPadding * collapsedOffset
        view.invalidateOutline()
    }

    view.doOnLayout { updateCollapsed() }
    lifecycleOwner.observe(uiViewModel.combined) {
        val system = uiViewModel.systemInsets.value
        constraintLayout.applyInsets(system, 64, 0)
        expandedToolbar.applyInsets(system)
        val insets = uiViewModel.run {
            if (playerSheetState.value == STATE_EXPANDED) system
            else getCombined()
        }
        playerCollapsedContainer.root.applyHorizontalInsets(insets)
        playerControls.root.applyHorizontalInsets(
            insets,
            activity.isLandscape()
        )
        val left = if (context.isRTL()) system.end + extraEndPadding else system.start
        leftPadding = collapsedTopPadding + left
        val right = if (context.isRTL()) system.start else system.end + extraEndPadding
        rightPadding = collapsedTopPadding + right
        updateCollapsed()
        adapter.insetsUpdated()
    }

    lifecycleOwner.observe(uiViewModel.moreSheetOffset) {
        updateCollapsed()
        adapter.moreOffsetUpdated()
    }
    lifecycleOwner.observe(uiViewModel.playerSheetOffset) {
        updateCollapsed()
        adapter.playerOffsetUpdated()

        viewModel.browser.value?.volume = 1 + min(0f, it)
        if (it < 1)
            activity.hideSystemUi(false)
        else if (uiViewModel.playerBgVisible.value)
            activity.hideSystemUi(true)
    }

    lifecycleOwner.observe(uiViewModel.playerSheetState) {
        updateCollapsed()
        if (isFinalState(it)) adapter.playerSheetStateUpdated()
        if (it == STATE_HIDDEN) viewModel.clearQueue()
        else if (it == STATE_COLLAPSED) lifecycleOwner.emit(uiViewModel.playerBgVisible, false)
    }

    playerControls.root.doOnLayout {
        uiViewModel.playerControlsHeight.value = it.height
        adapter.playerControlsHeightUpdated()
    }
    lifecycleOwner.observe(uiViewModel.playerBgVisible) {
        fgContainer.animateVisibility(!it)
        playerMoreContainer.animateVisibility(!it)
        activity.hideSystemUi(it)
    }
    // Note: Listeners are set in the Fragment as they involve navigation/callbacks, or can be passed if simple.
    // For now, these were part of configureCollapsing in original code, so keeping them here
    playerCollapsedContainer.playerClose.setOnClickListener {
        uiViewModel.changePlayerState(STATE_HIDDEN)
    }
    expandedToolbar.setNavigationOnClickListener {
        uiViewModel.collapsePlayer()
    }
}
