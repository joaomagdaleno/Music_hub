package com.joaomagdaleno.music_hub.ui.main

import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.recyclerview.widget.RecyclerView
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.databinding.FragmentMainBinding
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import com.joaomagdaleno.music_hub.ui.common.UiViewModel
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.BACKGROUND_GRADIENT
import com.joaomagdaleno.music_hub.ui.main.search.SearchFragment
import com.joaomagdaleno.music_hub.utils.ContextUtils
import com.joaomagdaleno.music_hub.utils.ui.AnimationUtils
import com.joaomagdaleno.music_hub.utils.ui.AutoClearedValue
import com.joaomagdaleno.music_hub.utils.ui.FastScrollerHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import kotlin.math.max

class MainFragment : Fragment() {

    private var binding by AutoClearedValue.autoCleared<FragmentMainBinding>(this)
    private val viewModel by activityViewModel<UiViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        AnimationUtils.setupTransition(this, view)
        applyPlayerBg(this, view) {
            mainBgDrawable.combine(currentNavBackground) { a, b -> b ?: a }
        }
        ContextUtils.observe(this, viewModel.navigation) {
            val toShow = when (it) {
                1 -> {
                    UiUtils.addIfNull<SearchFragment>(this, R.id.main_fragment_container_view, "search")
                    "search"
                }
                2 -> {
                    UiUtils.addIfNull<LibraryFragment>(this, R.id.main_fragment_container_view, "library")
                    "library"
                }
                else -> {
                    UiUtils.addIfNull<HomeFragment>(this, R.id.main_fragment_container_view, "home")
                    "home"
                }
            }

            childFragmentManager.commit(true) {
                childFragmentManager.fragments.forEach { fragment ->
                    if (fragment.tag != toShow) hide(fragment)
                    else show(fragment)
                }
                setReorderingAllowed(true)
            }
        }
    }

    companion object {
        fun applyPlayerBg(
            fragment: Fragment,
            view: View,
            imageFlow: UiViewModel.() -> Flow<Drawable?>
        ): UiViewModel {
            val uiViewModel by fragment.activityViewModel<UiViewModel>()
            val combined = uiViewModel.imageFlow()
            ContextUtils.observe(fragment, combined) { UiUtils.applyGradient(fragment, view, it) }
            return uiViewModel
        }

        fun applyInsets(
            fragment: Fragment,
            recyclerView: RecyclerView,
            outline: View,
            bottom: Int = 0,
            block: UiViewModel.(UiViewModel.Insets) -> Unit = {}
        ) {
            recyclerView.run {
                val height = UiUtils.dpToPx(context, 48)
                val settings = ContextUtils.getSettings(context)
                val isGradient = settings.getBoolean(BACKGROUND_GRADIENT, true)
                val extra = if (isGradient) 0.5f else 0f
                setOnScrollChangeListener { _, _, _, _, _ ->
                    val offset =
                        computeVerticalScrollOffset().coerceAtMost(height) / height.toFloat()
                    outline.alpha = max(0f, offset - extra)
                }
            }
            val scroller = FastScrollerHelper.applyTo(recyclerView)
            UiUtils.applyInsets(fragment) {
                UiUtils.applyInsets(recyclerView, it, 20, 20, bottom + 4)
                outline.updatePadding(top = it.top)
                scroller?.setPadding(recyclerView.run {
                    val pad = UiUtils.dpToPx(context, 8)
                    val isRtl = UiUtils.isRTL(context)
                    val left = if (!isRtl) it.start else it.end
                    val right = if (!isRtl) it.end else it.start
                    Rect(left + pad, it.top + pad, right + pad, it.bottom + bottom + pad)
                })
                block(viewModel(fragment), it)
            }
        }
        
        private fun viewModel(fragment: Fragment): UiViewModel {
            val vm by fragment.activityViewModel<UiViewModel>()
            return vm
        }
    }
}