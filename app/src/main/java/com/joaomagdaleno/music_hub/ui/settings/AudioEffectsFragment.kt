package com.joaomagdaleno.music_hub.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.databinding.FragmentAudioFxBinding
import com.joaomagdaleno.music_hub.databinding.FragmentGenericCollapsableBinding
import com.joaomagdaleno.music_hub.playback.listener.EffectsListener.Companion.deleteGlobalFx
import com.joaomagdaleno.music_hub.playback.listener.EffectsListener.Companion.globalFx
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.applyContentInsets
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.applyInsets
import com.joaomagdaleno.music_hub.ui.extensions.login.LoginFragment.Companion.bind
import com.joaomagdaleno.music_hub.ui.player.audiofx.AudioEffectsBottomSheet.Companion.bind
import com.joaomagdaleno.music_hub.ui.player.audiofx.AudioEffectsBottomSheet.Companion.onEqualizerClicked
import com.joaomagdaleno.music_hub.utils.ui.AutoClearedValue.Companion.autoCleared
import com.joaomagdaleno.music_hub.utils.ui.FastScrollerHelper

class AudioEffectsFragment : Fragment() {

    private var binding: FragmentGenericCollapsableBinding by autoCleared()
    private val fragment = AudioFxFragment()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentGenericCollapsableBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.bind(this)
        binding.extensionIcon.isVisible = false
        binding.toolBar.title = getString(R.string.audio_fx)
        childFragmentManager.beginTransaction().replace(R.id.genericFragmentContainer, fragment)
            .commit()

        binding.toolBar.inflateMenu(R.menu.refresh_menu)
        binding.toolBar.setOnMenuItemClickListener {
            val context = requireContext()
            context.deleteGlobalFx()
            fragment.bind()
            true
        }
    }

    class AudioFxFragment : Fragment() {
        var binding by autoCleared<FragmentAudioFxBinding>()

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            binding = FragmentAudioFxBinding.inflate(inflater, container, false)
            return binding.root
        }

        fun bind() = binding.bind(requireContext().globalFx()) { onEqualizerClicked() }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            bind()
            binding.root.apply {
                clipToPadding = false
                applyInsets { applyContentInsets(it) }
                isVerticalScrollBarEnabled = false
                FastScrollerHelper.applyTo(this)
            }
        }
    }

    companion object {
        const val AUDIO_FX = "audio_fx"
    }
}