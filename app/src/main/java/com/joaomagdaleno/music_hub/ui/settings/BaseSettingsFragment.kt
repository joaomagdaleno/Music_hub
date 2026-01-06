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
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.applyBackPressCallback
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.applyContentInsets
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.applyInsets
import com.joaomagdaleno.music_hub.utils.image.ImageUtils.loadAsCircle
import com.joaomagdaleno.music_hub.utils.ui.AnimationUtils.setupTransition
import com.joaomagdaleno.music_hub.utils.ui.AutoClearedValue.Companion.autoCleared
import com.joaomagdaleno.music_hub.utils.ui.FastScrollerHelper
import com.joaomagdaleno.music_hub.utils.ui.UiUtils.configureAppBar

abstract class BaseSettingsFragment : Fragment() {

    abstract val title: String
    abstract val icon: ImageHolder?
    abstract val creator: () -> Fragment

    var binding: FragmentGenericCollapsableBinding by autoCleared()

    final override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentGenericCollapsableBinding.inflate(inflater, container, false)
        return binding.root
    }

    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupTransition(view)

        applyBackPressCallback()
        binding.appBarLayout.configureAppBar { offset ->
            binding.toolbarOutline.alpha = offset
            binding.extensionIcon.alpha = 1 - offset
        }
        binding.toolBar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.toolBar.title = title
        icon.loadAsCircle(binding.extensionIcon, R.drawable.ic_extension_32dp) {
            binding.extensionIcon.setImageDrawable(it)
        }

        childFragmentManager.beginTransaction().replace(R.id.genericFragmentContainer, creator())
            .commit()
    }

    companion object {
        fun PreferenceFragmentCompat.configure() {
            listView?.apply {
                clipToPadding = false
                applyInsets { applyContentInsets(it) }
                isVerticalScrollBarEnabled = false
                FastScrollerHelper.applyTo(this)
            }
        }
    }
}