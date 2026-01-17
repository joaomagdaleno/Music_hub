package com.joaomagdaleno.music_hub.ui.common

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.databinding.FragmentExceptionBinding
import com.joaomagdaleno.music_hub.utils.ContextUtils
import com.joaomagdaleno.music_hub.utils.Serializer
import com.joaomagdaleno.music_hub.utils.ui.AnimationUtils
import com.joaomagdaleno.music_hub.utils.ui.AutoClearedValue
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import kotlinx.coroutines.launch

class ExceptionFragment : Fragment() {
    companion object {
        fun getBundle(data: com.joaomagdaleno.music_hub.utils.ui.ExceptionData) = Bundle().apply {
            Serializer.putSerialized(this, "data", data)
        }
    }

    private val data by lazy {
        requireArguments().getSerializable("data") as com.joaomagdaleno.music_hub.utils.ui.ExceptionData
    }

    private var binding by AutoClearedValue.autoCleared<FragmentExceptionBinding>(this)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentExceptionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        AnimationUtils.setupTransition(this, view)
        UiUtils.applyInsets(this) { insets ->
            UiUtils.applyContentInsets(binding.nestedScrollView, insets)
            UiUtils.applyFabInsets(binding.fabContainer, insets, systemInsets.value)
        }
        UiUtils.applyBackPressCallback(this)
        UiUtils.configureAppBar(binding.appBarLayout) { offset ->
            binding.toolbarOutline.alpha = offset
            binding.exceptionIconContainer.alpha = 1 - offset
        }

        binding.exceptionMessage.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.exceptionMessage.title = data.title
        binding.exceptionDetails.text = data.trace
        binding.fabCopy.setOnClickListener {
            copyException()
        }

        requireActivity().run {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return@run
            registerScreenCaptureCallback(mainExecutor, screenCaptureCallback)
        }
    }

    private fun copyException() {
        UiUtils.createSnack(this, R.string.copying_the_error)
        viewLifecycleOwner.lifecycleScope.launch {
            val toCopy = UiUtils.getPasteLink(data).getOrElse { data.trace } ?: data.trace
            ContextUtils.copyToClipboard(requireContext(), "Error", toCopy)
        }
    }

    private val screenCaptureCallback by lazy {
        Activity.ScreenCaptureCallback {
            copyException()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().run {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return@run
            unregisterScreenCaptureCallback(screenCaptureCallback)
        }
    }
}