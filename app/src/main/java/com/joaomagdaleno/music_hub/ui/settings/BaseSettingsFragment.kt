package com.joaomagdaleno.music_hub.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceFragmentCompat
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.ImageHolder
import com.joaomagdaleno.music_hub.databinding.FragmentGenericCollapsableBinding
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import com.joaomagdaleno.music_hub.utils.ui.AnimationUtils
import com.joaomagdaleno.music_hub.utils.ui.AutoClearedValue.Companion.autoCleared
import com.joaomagdaleno.music_hub.utils.ui.FastScrollerHelper

abstract class BaseSettingsFragment : Fragment() {

    abstract val title: String
    abstract val icon: ImageHolder?
    abstract val creator: () -> Fragment

    var binding: FragmentGenericCollapsableBinding by autoCleared(this)

    final override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentGenericCollapsableBinding.inflate(inflater, container, false)
        return binding.root
    }

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        AnimationUtils.setupTransition(this, view)

        UiUtils.applyBackPressCallback(this)
        UiUtils.configureAppBar(binding.appBarLayout) { offset ->
            binding.toolbarOutline.alpha = offset
        }
        binding.toolBar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.toolBar.title = title

        childFragmentManager.beginTransaction().replace(R.id.genericFragmentContainer, creator())
            .commit()
    }

    companion object {
        fun configure(fragment: PreferenceFragmentCompat) {
            fragment.listView?.apply {
                clipToPadding = false
                UiUtils.applyInsets(fragment) { UiUtils.applyContentInsets(this@apply, it) }
                isVerticalScrollBarEnabled = false
                FastScrollerHelper.applyTo(this)
            }
        }
    }
}