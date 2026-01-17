package com.joaomagdaleno.music_hub.ui.player

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.annotation.OptIn
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
import androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import com.google.android.material.slider.Slider
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.Streamable
import com.joaomagdaleno.music_hub.databinding.FragmentPlayerBinding
import com.joaomagdaleno.music_hub.playback.MediaItemUtils
import com.joaomagdaleno.music_hub.ui.common.UiViewModel
import com.joaomagdaleno.music_hub.ui.player.audiofx.AudioEffectsBottomSheet
import com.joaomagdaleno.music_hub.ui.media.more.MediaMoreBottomSheet
import com.joaomagdaleno.music_hub.ui.media.MediaFragment
import com.joaomagdaleno.music_hub.ui.player.quality.FormatUtils
import com.joaomagdaleno.music_hub.ui.player.quality.QualitySelectionBottomSheet
import com.joaomagdaleno.music_hub.utils.ContextUtils
import com.joaomagdaleno.music_hub.utils.image.ImageUtils
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import com.joaomagdaleno.music_hub.utils.ui.AnimationUtils
import com.joaomagdaleno.music_hub.utils.ui.AutoClearedValue
import com.joaomagdaleno.music_hub.utils.ui.CheckBoxListener
import com.joaomagdaleno.music_hub.utils.ui.SimpleItemSpan
import com.joaomagdaleno.music_hub.utils.ui.ViewPager2Utils
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class PlayerFragment : Fragment() {
    private var binding by AutoClearedValue.autoClearedNullable<FragmentPlayerBinding>(this)
    private val viewModel by activityViewModel<PlayerViewModel>()
    private val uiViewModel by activityViewModel<UiViewModel>()
    private val adapter by lazy {
        PlayerTrackAdapter(uiViewModel, viewModel.playerState.current, adapterListener)
    }
    private var lastHapticTime = -1L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = binding!!
        ViewPager2Utils.supportBottomSheetBehavior(binding.viewPager)
        UiUtils.setupPlayerMoreBehavior(uiViewModel, binding.playerMoreContainer)
        configureOutline(binding.root)
        configureCollapsing(binding)
        configureColors()
        configurePlayerControls()
        configureBackgroundPlayerView()
    }

    private val collapseHeight by lazy {
        resources.getDimension(R.dimen.collapsed_cover_size).toInt()
    }

    private fun configureOutline(view: View) {
        PlayerAnimations.configurePlayerOutline(view, uiViewModel, this, collapseHeight)
    }

    private fun configureCollapsing(binding: FragmentPlayerBinding) {
        PlayerAnimations.configurePlayerCollapsing(
            binding, uiViewModel, viewModel, adapter, this, collapseHeight, requireActivity()
        )
        // Additional listeners kept in Fragment just in case, but moved logic to helper covers most
        // binding.bgPanel.setOnClickListener { adapterListener.onClick() }
    }

    private val adapterListener = object : PlayerTrackAdapter.Listener {
        override fun onClick() {
            uiViewModel.run {
                if (playerSheetState.value != STATE_EXPANDED) changePlayerState(STATE_EXPANDED)
                else {
                    if (moreSheetState.value == STATE_EXPANDED) {
                        changeMoreState(STATE_COLLAPSED)
                        return
                    }
                    val shouldBeVisible = !playerBgVisible.value
                    if (shouldBeVisible) {
                        val binding = binding ?: return@run
                        if (binding.bgImage.drawable == null && !hasVideo(binding.playerView.player))
                            return
                        changeMoreState(STATE_COLLAPSED)
                    }
                    changeBgVisible(shouldBeVisible)
                }
            }
        }

        override fun onStartDoubleClick() {
            viewModel.seekToAdd(-10000)
        }

        override fun onEndDoubleClick() {
            viewModel.seekToAdd(10000)
        }
    }

    private fun configurePlayerControls() {
        val viewPager = binding!!.viewPager
        viewPager.adapter = adapter
        ViewPager2Utils.registerOnUserPageChangeCallback(viewPager) { pos, isUser ->
            val index = viewModel.playerState.current.value?.index
            if (index != pos && isUser) viewModel.seek(pos)
        }
        viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(pos: Int, offset: Float, offsetPx: Int) {
                adapter.playerOffsetUpdated()
            }
        })

        fun submit() {
            adapter.submitList(viewModel.queue) {
                val index = (viewModel.playerState.current.value?.index ?: -1).takeIf { it != -1 }
                    ?: return@submitList
                val current = binding?.viewPager?.currentItem ?: 0
                val smooth = abs(index - current) <= 1
                binding?.viewPager?.setCurrentItem(index, smooth)
            }
        }

        val binding = binding!!
        binding.playerControls.trackHeart.addOnCheckedStateChangedListener(likeListener)
        ContextUtils.observe(this, viewModel.playerState.current) { it ->
            uiViewModel.run {
                if (it == null) return@run changePlayerState(STATE_HIDDEN)
                if (!UiUtils.isFinalState(playerSheetState.value)) return@run
                changePlayerState(
                    if (playerSheetState.value != STATE_EXPANDED) STATE_COLLAPSED
                    else STATE_EXPANDED
                )
            }
            submit()
            it?.mediaItem ?: return@observe
            applyCurrent(binding, it.mediaItem)
        }

        ContextUtils.observe(this, viewModel.queueFlow) { submit() }

        val playPauseListener = CheckBoxListener { viewModel.setPlaying(it) }
        binding.playerControls.trackPlayPause
            .addOnCheckedStateChangedListener(playPauseListener)
        binding.playerCollapsedContainer.collapsedTrackPlayPause
            .addOnCheckedStateChangedListener(playPauseListener)
        binding.playerControls.trackPlayPause.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstantsCompat.GESTURE_START)
        }
        ContextUtils.observe(this, viewModel.isPlaying) {
            binding.run {
                playPauseListener.enabled = false
                playerControls.trackPlayPause.isChecked = it
                playerCollapsedContainer.collapsedTrackPlayPause.isChecked = it
                playPauseListener.enabled = true
            }
        }
        ContextUtils.observe(this, viewModel.buffering) {
            binding.playerControls.playingIndicator.alpha = if (it) 1f else 0f
            binding.playerCollapsedContainer.collapsedPlayingIndicator.alpha = if (it) 1f else 0f
        }

        ContextUtils.observe(this, viewModel.progress) { (curr, buff) ->
            binding.playerCollapsedContainer.run {
                collapsedBuffer.progress = buff.toInt()
                collapsedSeekbar.progress = curr.toInt()
            }
            binding.playerControls.run {
                if (!seekBar.isPressed) {
                    bufferBar.progress = buff.toInt()
                    seekBar.value = max(0f, min(curr.toFloat(), seekBar.valueTo))
                    trackCurrentTime.text = UiUtils.toTimeString(curr)
                } else {
                    val value = seekBar.value.toLong()
                    if (abs(value - lastHapticTime) >= 1000) {
                        lastHapticTime = value
                        seekBar.performHapticFeedback(HapticFeedbackConstantsCompat.CLOCK_TICK)
                    }
                }
            }
        }

        ContextUtils.observe(this, viewModel.totalDuration) { it ->
            val duration = it ?: viewModel.playerState.current.value?.let { MediaItemUtils.getTrack(it.mediaItem).duration } ?: 0L
            binding.playerCollapsedContainer.run {
                collapsedSeekbar.max = duration.toInt()
                collapsedBuffer.max = duration.toInt()
            }
            binding.playerControls.run {
                bufferBar.max = duration.toInt()
                seekBar.apply {
                    value = max(0f, min(value, duration.toFloat()))
                    valueTo = 1f + duration
                }
                trackTotalTime.text = UiUtils.toTimeString(duration)
            }
        }


        val repeatModes = listOf(REPEAT_MODE_OFF, REPEAT_MODE_ALL, REPEAT_MODE_ONE)
        val animatedVectorDrawables = requireContext().run {
            fun asAnimated(id: Int) =
                AppCompatResources.getDrawable(this, id) as AnimatedVectorDrawable
            listOf(
                asAnimated(R.drawable.ic_repeat_one_to_repeat_off_40dp),
                asAnimated(R.drawable.ic_repeat_off_to_repeat_40dp),
                asAnimated(R.drawable.ic_repeat_to_repeat_one_40dp)
            )
        }
        val drawables = requireContext().run {
            fun asDrawable(id: Int) = AppCompatResources.getDrawable(this, id)!!
            listOf(
                asDrawable(R.drawable.ic_repeat_off_40dp),
                asDrawable(R.drawable.ic_repeat_40dp),
                asDrawable(R.drawable.ic_repeat_one_40dp),
            )
        }

        binding.playerControls.trackRepeat.icon =
            drawables[repeatModes.indexOf(viewModel.repeatMode.value)]

        fun changeRepeatDrawable(repeatMode: Int) = binding.playerControls.trackRepeat.run {
            val index = repeatModes.indexOf(repeatMode)
            icon = animatedVectorDrawables[index]
            (icon as Animatable).start()
        }

        binding.playerControls.run {
            seekBar.apply {
                addOnChangeListener { _, value, fromUser ->
                    if (fromUser) trackCurrentTime.text = UiUtils.toTimeString(value.toLong())
                }
                addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                    override fun onStartTrackingTouch(slider: Slider) = Unit
                    override fun onStopTrackingTouch(slider: Slider) =
                        viewModel.seekTo(slider.value.toLong())
                })
            }

            trackNext.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstantsCompat.GESTURE_START)
                viewModel.next()
                (trackNext.icon as Animatable).start()
            }
            ContextUtils.observe(this@PlayerFragment, viewModel.nextEnabled) { trackNext.isEnabled = it }

            trackPrevious.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstantsCompat.GESTURE_START)
                viewModel.previous()
                (trackPrevious.icon as Animatable).start()
            }
            ContextUtils.observe(this@PlayerFragment, viewModel.previousEnabled) { trackPrevious.isEnabled = it }

            val shuffleListener = CheckBoxListener { viewModel.setShuffle(it) }
            trackShuffle.addOnCheckedStateChangedListener(shuffleListener)
            trackShuffle.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstantsCompat.GESTURE_START)
            }
            ContextUtils.observe(this@PlayerFragment, viewModel.shuffleMode) {
                shuffleListener.enabled = false
                trackShuffle.isChecked = it
                shuffleListener.enabled = true
            }

            trackRepeat.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstantsCompat.GESTURE_START)
                val mode = when (viewModel.repeatMode.value) {
                    REPEAT_MODE_OFF -> REPEAT_MODE_ALL
                    REPEAT_MODE_ALL -> REPEAT_MODE_ONE
                    else -> REPEAT_MODE_OFF
                }
                changeRepeatDrawable(mode)
                viewModel.setRepeat(mode)
            }
            ContextUtils.observe(this@PlayerFragment, viewModel.repeatMode) { changeRepeatDrawable(it) }

            trackSubtitle.setOnClickListener {
                QualitySelectionBottomSheet().show(parentFragmentManager, null)
            }
            ContextUtils.observe(this@PlayerFragment, viewModel.serverAndTracks) { (tracks, server, index) ->
                trackSubtitle.text = tracks?.let { FormatUtils.getDetails(it, requireContext(), server, index) }
                    ?.joinToString(" ⦿ ")?.takeIf { it.isNotBlank() }
            }
        }
    }

    private val likeListener = CheckBoxListener { viewModel.likeCurrent(it) }

    private fun configureColors() {
        ContextUtils.observe(this, viewModel.playerState.current) { adapter.onCurrentUpdated() }
        var last: Drawable? = null
        adapter.currentDrawableListener = { drawable ->
            if (last != drawable) {
                last = drawable
                val context = requireContext()
                uiViewModel.playerDrawable.value = drawable
                val colors =
                    if (isDynamic(context)) PlayerColors.getColorsFrom(context, drawable?.toBitmap()) else null
                uiViewModel.playerColors.value = colors
                if (MediaItemUtils.showBackground(ContextUtils.getSettings(context))) ImageUtils.loadBlurred(binding!!.bgImage, drawable, 12f)
                else binding?.bgImage?.setImageDrawable(null)
            }
        }
        val bufferView =
            binding?.playerView?.findViewById<ProgressBar>(androidx.media3.ui.R.id.exo_buffering)
        ContextUtils.observe(this, uiViewModel.playerColors) {
            val context = requireContext()
            if (isPlayerColor(context) && isDynamic(context)) {
                if (uiViewModel.currentAppColor != viewModel.playerState.current.value?.let { MediaItemUtils.getTrack(it.mediaItem).id }) {
                    uiViewModel.currentAppColor =
                        viewModel.playerState.current.value?.let { MediaItemUtils.getTrack(it.mediaItem).id }
                    requireActivity().recreate()
                    return@observe
                }
            }
            val colors = it ?: PlayerColors.defaultPlayerColors(context)
            val binding = binding!!
            adapter.onColorsUpdated()

            binding.run {
                val color = if (isDynamic(requireContext())) colors.accent
                else colors.background
                root.setBackgroundColor(color)
                val backgroundState = ColorStateList.valueOf(colors.background)
                bgGradient.imageTintList = backgroundState
                bgCollapsed.backgroundTintList = backgroundState
                bufferView?.indeterminateDrawable?.setTint(colors.accent)
                expandedToolbar.run {
                    setTitleTextColor(colors.onBackground)
                    setSubtitleTextColor(colors.onBackground)
                }
            }

            binding.playerCollapsedContainer.run {
                collapsedPlayingIndicator.setIndicatorColor(colors.accent)
                collapsedSeekbar.setIndicatorColor(colors.accent)
                collapsedBuffer.setIndicatorColor(colors.accent)
                collapsedBuffer.trackColor = colors.onBackground
            }

            binding.playerControls.run {
                seekBar.trackActiveTintList = ColorStateList.valueOf(colors.accent)
                seekBar.thumbTintList = ColorStateList.valueOf(colors.accent)
                playingIndicator.setIndicatorColor(colors.accent)
                bufferBar.setIndicatorColor(colors.accent)
                bufferBar.trackColor = colors.onBackground
                trackCurrentTime.setTextColor(colors.onBackground)
                trackTotalTime.setTextColor(colors.onBackground)
                trackTitle.setTextColor(colors.onBackground)
                trackArtist.setTextColor(colors.onBackground)
            }
        }
    }

    private fun applyCurrent(binding: FragmentPlayerBinding, item: MediaItem) {
        val track = MediaItemUtils.getTrack(item)
        val origin = MediaItemUtils.getOrigin(item)
        binding.expandedToolbar.run {
            val itemContext = MediaItemUtils.getContext(item)
            title = if (itemContext != null) context.getString(R.string.playing_from) else null
            subtitle = itemContext?.title
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_more -> {
                        onMoreClicked(item)
                        true
                    }

                    R.id.menu_audio_fx -> {
                        AudioEffectsBottomSheet().show(parentFragmentManager, null)
                        true
                    }

                    else -> false
                }
            }
        }
        binding.playerControls.run {
            trackHeart.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstantsCompat.GESTURE_START)
            }
            trackTitle.text = track.title
            UiUtils.marquee(trackTitle)
            val artists = track.artists
            val artistNames = artists.joinToString(", ") { it.name }
            val span = SpannableString(artistNames)

            artists.forEach { artist ->
                val start = artistNames.indexOf(artist.name)
                val end = start + artist.name.length
                val clickableSpan = SimpleItemSpan(trackArtist.context) {
                    openItem(origin, artist)
                }
                runCatching {
                    span.setSpan(
                        clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }

            trackArtist.text = span
            trackArtist.movementMethod = LinkMovementMethod.getInstance()
            likeListener.enabled = false
            trackHeart.isChecked = MediaItemUtils.isLiked(item)
            likeListener.enabled = true
            lifecycleScope.launch {
                val isTrackClient = viewModel.isLikeClient(MediaItemUtils.getOrigin(item))
                trackHeart.isVisible = isTrackClient
            }
        }
    }

    private fun openItem(source: String, item: EchoMediaItem) {
        UiUtils.openFragment<MediaFragment>(requireActivity(), null, MediaFragment.getBundle(source, item, false))
    }

    private fun onMoreClicked(item: MediaItem) {
        MediaMoreBottomSheet.newInstance(
            R.id.navHostFragment, MediaItemUtils.getOrigin(item), MediaItemUtils.getTrack(item), MediaItemUtils.isLoaded(item), true
        ).show(requireActivity().supportFragmentManager, null)
    }

    private fun hasVideo(player: Player?) =
        player?.currentTracks?.groups.orEmpty().any { it.type == C.TRACK_TYPE_VIDEO }

    private fun applyVideoVisibility(visible: Boolean) {
        binding?.playerView?.isVisible = visible
        binding?.bgImage?.isVisible = !visible
        if (UiUtils.isLandscape(requireContext())) return
        binding?.playerControls?.trackCoverPlaceHolder?.isVisible = visible
        adapter.updatePlayerVisibility(visible)
    }

    private var oldBg: Streamable.Media.Background? = null
    private var backgroundPlayer: Player? = null

    @OptIn(UnstableApi::class)
    private fun applyPlayer() {
        val mainPlayer = viewModel.browser.value
        val background = viewModel.playerState.current.value?.let { MediaItemUtils.getBackground(it.mediaItem) }
        val visible = if (hasVideo(mainPlayer)) {
            binding?.playerView?.player = mainPlayer
            binding?.playerView?.resizeMode = RESIZE_MODE_FIT
            backgroundPlayer?.release()
            backgroundPlayer = null
            true
        } else if (background != null) {
            if (oldBg != background || backgroundPlayer == null) {
                oldBg = background
                backgroundPlayer?.release()
                backgroundPlayer = getPlayer(requireContext(), viewModel.cache, background)
            }
            binding?.playerView?.player = backgroundPlayer
            binding?.playerView?.resizeMode = RESIZE_MODE_ZOOM
            true
        } else {
            backgroundPlayer?.release()
            backgroundPlayer = null
            binding?.playerView?.player = null
            false
        }
        applyVideoVisibility(visible)
    }

    @OptIn(UnstableApi::class)
    private fun configureBackgroundPlayerView() {
        binding?.playerView?.subtitleView?.setStyle(
            CaptionStyleCompat(
                Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT,
                EDGE_TYPE_OUTLINE, Color.BLACK, null
            )
        )
        ContextUtils.observe(this, viewModel.serverAndTracks) { applyPlayer() }
    }

    companion object {
        const val DYNAMIC_PLAYER = "dynamic_player"
        const val PLAYER_COLOR = "player_app_color"
        fun isDynamic(context: Context) =
            ContextUtils.getSettings(context).getBoolean(DYNAMIC_PLAYER, true)

        private fun isPlayerColor(context: Context) =
            ContextUtils.getSettings(context).getBoolean(PLAYER_COLOR, false)

        @OptIn(UnstableApi::class)
        fun getPlayer(
            context: Context, cache: SimpleCache, video: Streamable.Media.Background,
        ): ExoPlayer {
            val cacheFactory = CacheDataSource
                .Factory().setCache(cache)
                .setUpstreamDataSourceFactory(
                    DefaultHttpDataSource.Factory()
                        .setDefaultRequestProperties(video.request.headers)
                )
            val factory = DefaultMediaSourceFactory(context)
                .setDataSourceFactory(cacheFactory)
            val player = ExoPlayer.Builder(context).setMediaSourceFactory(factory).build()
            player.setMediaItem(MediaItem.fromUri(video.request.url.toUri()))
            player.repeatMode = REPEAT_MODE_ONE
            player.volume = 0f
            player.prepare()
            player.play()
            return player
        }
    }
}