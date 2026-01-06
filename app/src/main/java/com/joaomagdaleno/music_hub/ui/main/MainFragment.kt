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
import com.joaomagdaleno.music_hub.ui.common.FragmentUtils.addIfNull
import com.joaomagdaleno.music_hub.ui.common.UiViewModel
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.BACKGROUND_GRADIENT
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.applyGradient
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.applyInsets
import com.joaomagdaleno.music_hub.ui.main.search.SearchFragment
import com.joaomagdaleno.music_hub.utils.ContextUtils.getSettings
import com.joaomagdaleno.music_hub.utils.ContextUtils.observe
import com.joaomagdaleno.music_hub.utils.ui.AnimationUtils.setupTransition
import com.joaomagdaleno.music_hub.utils.ui.AutoClearedValue.Companion.autoCleared
import com.joaomagdaleno.music_hub.utils.ui.FastScrollerHelper
import com.joaomagdaleno.music_hub.utils.ui.UiUtils.dpToPx
import com.joaomagdaleno.music_hub.utils.ui.UiUtils.isRTL
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import kotlin.math.max

class MainFragment : Fragment() {

    private var binding by autoCleared<FragmentMainBinding>()
    private val viewModel by activityViewModel<UiViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    private inline fun <reified F : Fragment> Fragment.addIfNull(tag: String): String {
        addIfNull<F>(R.id.main_fragment_container_view, tag)
        return tag
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupTransition(view)
        applyPlayerBg(view) {
            mainBgDrawable.combine(currentNavBackground) { a, b -> b ?: a }
        }
        observe(viewModel.navigation) {
            val toShow = when (it) {
                1 -> addIfNull<SearchFragment>("search")
                2 -> addIfNull<LibraryFragment>("library")
                else -> addIfNull<HomeFragment>("home")
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
        fun Fragment.applyPlayerBg(
            view: View,
            imageFlow: UiViewModel.() -> Flow<Drawable?>
        ): UiViewModel {
            val uiViewModel by activityViewModel<UiViewModel>()
            val combined = uiViewModel.imageFlow()
            observe(combined) { applyGradient(view, it) }
            return uiViewModel
        }

        fun Fragment.applyInsets(
            recyclerView: RecyclerView,
            outline: View,
            bottom: Int = 0,
            block: UiViewModel.(UiViewModel.Insets) -> Unit = {}
        ) {
            recyclerView.run {
                val height = 48.dpToPx(context)
                val settings = context.getSettings()
                val isGradient = settings.getBoolean(BACKGROUND_GRADIENT, true)
                val extra = if (isGradient) 0.5f else 0f
                setOnScrollChangeListener { _, _, _, _, _ ->
                    val offset =
                        computeVerticalScrollOffset().coerceAtMost(height) / height.toFloat()
                    outline.alpha = max(0f, offset - extra)
                }
            }
            val scroller = FastScrollerHelper.applyTo(recyclerView)
            applyInsets {
                recyclerView.applyInsets(it, 20, 20, bottom + 4)
                outline.updatePadding(top = it.top)
                scroller?.setPadding(recyclerView.run {
                    val pad = 8.dpToPx(context)
                    val isRtl = context.isRTL()
                    val left = if (!isRtl) it.start else it.end
                    val right = if (!isRtl) it.end else it.start
                    Rect(left + pad, it.top + pad, right + pad, it.bottom + bottom + pad)
                })
                block(it)
            }
        }
    }
}