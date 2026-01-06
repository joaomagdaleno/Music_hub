package com.joaomagdaleno.music_hub.ui.extensions.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.button.MaterialButtonToggleGroup.OnButtonCheckedListener
import com.joaomagdaleno.music_hub.R
import com.joaomagdaleno.music_hub.common.models.ExtensionType
import com.joaomagdaleno.music_hub.databinding.DialogExtensionsListBinding
import com.joaomagdaleno.music_hub.databinding.ItemExtensionButtonBinding
import com.joaomagdaleno.music_hub.ui.common.FragmentUtils.openFragment
import com.joaomagdaleno.music_hub.ui.extensions.ExtensionInfoFragment
import com.joaomagdaleno.music_hub.ui.extensions.ExtensionInfoPreference.Companion.getType
import com.joaomagdaleno.music_hub.ui.extensions.ExtensionsViewModel
import com.joaomagdaleno.music_hub.ui.extensions.add.ExtensionsAddBottomSheet
import com.joaomagdaleno.music_hub.ui.extensions.manage.ManageExtensionsFragment
import com.joaomagdaleno.music_hub.ui.player.more.lyrics.LyricsViewModel
import com.joaomagdaleno.music_hub.utils.ContextUtils.observe
import com.joaomagdaleno.music_hub.utils.image.ImageUtils.loadAsCircle
import com.joaomagdaleno.music_hub.utils.ui.AutoClearedValue.Companion.autoCleared
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class ExtensionsListBottomSheet : BottomSheetDialogFragment() {

    companion object {
        fun newInstance(type: ExtensionType) = ExtensionsListBottomSheet().apply {
            arguments = Bundle().apply {
                putString("type", type.name)
            }
        }
    }

    private var binding by autoCleared<DialogExtensionsListBinding>()
    private val args by lazy { requireArguments() }
    private val type by lazy { ExtensionType.valueOf(args.getString("type")!!) }

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        binding = DialogExtensionsListBinding.inflate(inflater, parent, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.topAppBar.setNavigationOnClickListener { dismiss() }
        binding.topAppBar.title = getString(R.string.x_extensions, getString(getType(type)))

        binding.addExtension.setOnClickListener {
            dismiss()
            ExtensionsAddBottomSheet().show(parentFragmentManager, null)
        }

        binding.manageExtension.setOnClickListener {
            dismiss()
            requireActivity().openFragment<ManageExtensionsFragment>()
        }

        val viewModel = when (type) {
            ExtensionType.MUSIC -> activityViewModel<ExtensionsViewModel>().value
            ExtensionType.LYRICS -> activityViewModel<LyricsViewModel>().value
            else -> throw IllegalStateException("Not supported")
        }

        binding.topAppBar.setOnMenuItemClickListener {
            val extension =
                viewModel.currentSelectionFlow.value ?: return@setOnMenuItemClickListener false
            requireActivity().openFragment<ExtensionInfoFragment>(
                null, ExtensionInfoFragment.getBundle(extension)
            )
            dismiss()
            true
        }

        val listener = object : OnButtonCheckedListener {
            var enabled = false
            override fun onButtonChecked(
                group: MaterialButtonToggleGroup?, checkedId: Int, isChecked: Boolean
            ) {
                if (isChecked && enabled) {
                    viewModel.selectExtension(checkedId)
                    dismiss()
                }
            }
        }
        binding.buttonToggleGroup.addOnButtonCheckedListener(listener)
        val extensionFlow = viewModel.extensionsFlow
        observe(extensionFlow) { list ->
            binding.buttonToggleGroup.removeAllViews()
            listener.enabled = false
            val selected = viewModel.currentSelectionFlow.value
            list.forEachIndexed { index, extension ->
                if (!extension.isEnabled) return@forEachIndexed
                val button = ItemExtensionButtonBinding.inflate(
                    layoutInflater,
                    binding.buttonToggleGroup,
                    false
                ).root
                button.text = extension.name
                binding.buttonToggleGroup.addView(button)
                button.isChecked = extension == selected
                extension.metadata.icon.loadAsCircle(button) {
                    if (it != null) {
                        button.icon = it
                        button.iconTint = null
                    } else button.setIconResource(R.drawable.ic_extension_32dp)
                }
                button.id = index
            }

            val checked = list.indexOf(selected).takeIf { it != -1 }
            if (checked != null) binding.buttonToggleGroup.check(checked)
            listener.enabled = true
        }
    }

}