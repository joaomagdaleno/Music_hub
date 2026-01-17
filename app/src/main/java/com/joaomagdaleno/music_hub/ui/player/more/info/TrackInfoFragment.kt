package com.joaomagdaleno.music_hub.ui.player.more.info

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.transition.MaterialSharedAxis
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.ui.media.MediaDetailsFragment
import com.joaomagdaleno.music_hub.utils.ui.AnimationUtils.setupTransition
import org.koin.androidx.viewmodel.ext.android.viewModel

class TrackInfoFragment : Fragment(
    R.layout.fragment_player_info
), MediaDetailsFragment.Parent {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupTransition(this, view, axis = MaterialSharedAxis.Y)
    }

    override val feedId = "player"
    override val fromPlayer = true
    override val viewModel by viewModel<TrackInfoViewModel>()
}