package com.joaomagdaleno.music_hub.ui.player.more

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.updatePaddingRelative
import androidx.fragment.app.Fragment
import androidx.fragment.app.add
import androidx.fragment.app.commitNow
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.databinding.FragmentPlayerMoreBinding
import com.joaomagdaleno.music_hub.ui.common.UiViewModel
import com.joaomagdaleno.music_hub.ui.player.PlayerColors
import com.joaomagdaleno.music_hub.ui.player.PlayerColors.Companion.defaultPlayerColors
import com.joaomagdaleno.music_hub.ui.player.more.info.TrackInfoFragment
import com.joaomagdaleno.music_hub.ui.player.more.lyrics.LyricsFragment
import com.joaomagdaleno.music_hub.ui.player.more.upnext.QueueFragment
import com.joaomagdaleno.music_hub.utils.ContextUtils.observe
import com.joaomagdaleno.music_hub.utils.ui.AutoClearedValue.Companion.autoCleared
import com.joaomagdaleno.music_hub.utils.ui.UiUtils.dpToPx
import com.joaomagdaleno.music_hub.utils.ui.UiUtils.isLandscape
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class PlayerMoreFragment : Fragment() {

    private var binding by autoCleared<FragmentPlayerMoreBinding>(this)
    private val uiViewModel by activityViewModel<UiViewModel>()
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentPlayerMoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    private inline fun <reified F : Fragment> Fragment.addIfNull(): String {
        val tag = F::class.java.simpleName
        childFragmentManager.run {
            if (findFragmentByTag(tag) == null) commitNow {
                add<F>(R.id.player_more_fragment_container, tag)
            }
        }
        return tag
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        var topInset = 0
        val topMargin = requireContext().run {
            if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 0
            else (72 * resources.displayMetrics.density).toInt()
        }
        fun updateTranslateY() {
            val offset = uiViewModel.moreSheetOffset.value
            val inverted = 1 - offset
            binding.root.translationY = -topInset * inverted
            binding.playerMoreFragmentContainer.translationY =
                (1 - offset) * 2 * (uiViewModel.systemInsets.value?.bottom ?: 0)
        }

        observe(this, uiViewModel.systemInsets) { insets ->
            topInset = insets.top + topMargin
            view.updatePaddingRelative(top = insets.top)
            updateTranslateY()
        }

        observe(this, uiViewModel.moreSheetOffset) { offset ->
            updateTranslateY()
            binding.buttonToggleGroupBg.alpha = offset
        }

        observe(this, uiViewModel.moreSheetState) { state ->
            if (state == BottomSheetBehavior.STATE_COLLAPSED) binding.buttonToggleGroup.clearChecked()
            else binding.buttonToggleGroup.check(uiViewModel.lastMoreTab)
        }

        observe(this, uiViewModel.playerColors) { colorsNullable ->
            val colors = colorsNullable ?: PlayerColors.defaultPlayerColors(requireContext())
            binding.buttonToggleGroupBg.children.forEach {
                it as MaterialButton
                it.backgroundTintList = ColorStateList.valueOf(colors.background)
            }

            val textColorStateList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(
                    colors.background,
                    colors.onBackground
                )
            )
            val foregroundColorStateList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(
                    colors.onBackground,
                    Color.TRANSPARENT
                )
            )
            binding.buttonToggleGroup.children.forEach {
                it as MaterialButton
                it.setTextColor(textColorStateList)
                it.backgroundTintList = foregroundColorStateList
            }
        }

        showFragment()
        binding.buttonToggleGroup.addOnButtonCheckedListener { group, _, _ ->
            uiViewModel.run {
                val current = moreSheetState.value
                if (current != BottomSheetBehavior.STATE_COLLAPSED && current != BottomSheetBehavior.STATE_EXPANDED) return@run
                changeMoreState(
                    if (group.checkedButtonId != -1) BottomSheetBehavior.STATE_EXPANDED else BottomSheetBehavior.STATE_COLLAPSED
                )
            }
            showFragment()
        }

        @SuppressLint("ClickableViewAccessibility")
        val touchListener = View.OnTouchListener { v, _ ->
            uiViewModel.lastMoreTab = v.id
            false
        }
        binding.buttonToggleGroup.children.forEach { it.setOnTouchListener(touchListener) }
    }

    private fun showFragment() {
        val checkedId = binding.buttonToggleGroup.checkedButtonId
        val toShow = when (checkedId) {
            R.id.queue -> addIfNull<QueueFragment>()
            R.id.lyrics -> addIfNull<LyricsFragment>()
            R.id.info -> addIfNull<TrackInfoFragment>()
            else -> null
        }
        childFragmentManager.commitNow {
            childFragmentManager.fragments.forEach { fragment ->
                if (fragment.tag != toShow) hide(fragment)
                else show(fragment)
            }
            setReorderingAllowed(true)
        }
    }
}