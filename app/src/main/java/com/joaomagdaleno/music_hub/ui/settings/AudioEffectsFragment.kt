package com.joaomagdaleno.music_hub.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.databinding.FragmentAudioFxBinding
import com.joaomagdaleno.music_hub.databinding.FragmentGenericCollapsableBinding
import com.joaomagdaleno.music_hub.playback.listener.EffectsListener
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import com.joaomagdaleno.music_hub.ui.player.audiofx.AudioEffectsBottomSheet
import com.joaomagdaleno.music_hub.utils.ui.AutoClearedValue.Companion.autoCleared
import com.joaomagdaleno.music_hub.utils.ui.FastScrollerHelper

class AudioEffectsFragment : Fragment() {

    private var binding: FragmentGenericCollapsableBinding by autoCleared(this)
    private val fragment = AudioFxFragment()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentGenericCollapsableBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setNavigationOnClickListener { requireActivity().onBackPressed() }
        binding.toolBar.title = getString(R.string.audio_fx)
        childFragmentManager.beginTransaction().replace(R.id.genericFragmentContainer, fragment)
            .commit()

        binding.toolBar.inflateMenu(R.menu.refresh_menu)
        binding.toolBar.setOnMenuItemClickListener {
            val context = requireContext()
            EffectsListener.deleteGlobalFx(context)
            fragment.bind()
            true
        }
    }

    class AudioFxFragment : Fragment() {
        var binding by autoCleared<FragmentAudioFxBinding>(this)

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            binding = FragmentAudioFxBinding.inflate(inflater, container, false)
            return binding.root
        }

        fun bind() = AudioEffectsBottomSheet.bind(binding, EffectsListener.globalFx(requireContext())) { 
            AudioEffectsBottomSheet.onEqualizerClicked(this) 
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            bind()
            binding.root.apply {
                clipToPadding = false
                UiUtils.applyInsets(this@AudioFxFragment) { UiUtils.applyContentInsets(this@apply, it) }
                isVerticalScrollBarEnabled = false
                FastScrollerHelper.applyTo(this)
            }
        }
    }

    companion object {
        const val AUDIO_FX = "audio_fx"
    }
}