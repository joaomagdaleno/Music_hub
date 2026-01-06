package com.joaomagdaleno.music_hub.ui.extensions.add

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ConcatAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.joaomagdaleno.music_hub.databinding.DialogExtensionsAddListBinding
import com.joaomagdaleno.music_hub.ui.extensions.ExtensionsViewModel
import com.joaomagdaleno.music_hub.utils.ContextUtils.observe
import com.joaomagdaleno.music_hub.utils.ui.AutoClearedValue.Companion.autoCleared
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class ExtensionsAddListBottomSheet : BottomSheetDialogFragment() {
    var binding by autoCleared<DialogExtensionsAddListBinding>()
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = DialogExtensionsAddListBinding.inflate(inflater, container, false)
        return binding.root
    }

    private var clicked = false
    private val viewModel by lazy {
        requireParentFragment().viewModel<AddViewModel>().value
    }
    private val extensionsViewModel by activityViewModel<ExtensionsViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val extensionListAdapter = ExtensionsAddListAdapter { item, isChecked ->
            viewModel.toggleItem(item, isChecked)
        }

        val headerAdapter = ExtensionsAddListAdapter.Header(
            object : ExtensionsAddListAdapter.Header.Listener {
                override fun onClose() = dismiss()
                override fun onSelectAllChanged(select: Boolean) {
                    viewModel.selectAll(select)
                }
            }
        )

        val footerAdapter = ExtensionsAddListAdapter.Footer {
            clicked = true
            dismiss()
        }
        binding.root.adapter = ConcatAdapter(headerAdapter, extensionListAdapter, footerAdapter)
        observe(viewModel.addingFlow) {
            val list = viewModel.getList()
            extensionListAdapter.submitList(list)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        viewModel.download(clicked, extensionsViewModel)
        super.onDismiss(dialog)
    }
}
