package com.joaomagdaleno.music_hub.ui.extensions.manage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.joaomagdaleno.music_hub.common.Extension
import com.joaomagdaleno.music_hub.common.MusicExtension
import com.joaomagdaleno.music_hub.common.models.ExtensionType
import com.joaomagdaleno.music_hub.databinding.FragmentManageExtensionsBinding
import com.joaomagdaleno.music_hub.ui.common.FragmentUtils.openFragment
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.applyBackPressCallback
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.applyInsets
import com.joaomagdaleno.music_hub.ui.common.UiViewModel.Companion.applyInsetsWithChild
import com.joaomagdaleno.music_hub.ui.extensions.ExtensionInfoFragment
import com.joaomagdaleno.music_hub.ui.extensions.ExtensionInfoPreference.Companion.getType
import com.joaomagdaleno.music_hub.ui.extensions.ExtensionsViewModel
import com.joaomagdaleno.music_hub.ui.extensions.add.ExtensionsAddBottomSheet
import com.joaomagdaleno.music_hub.utils.ContextUtils.observe
import com.joaomagdaleno.music_hub.utils.ui.AnimationUtils.setupTransition
import com.joaomagdaleno.music_hub.utils.ui.AutoClearedValue.Companion.autoCleared
import com.joaomagdaleno.music_hub.utils.ui.FastScrollerHelper
import com.joaomagdaleno.music_hub.utils.ui.UiUtils.configureAppBar
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class ManageExtensionsFragment : Fragment() {
    private var binding by autoCleared<FragmentManageExtensionsBinding>()
    private val viewModel by activityViewModel<ExtensionsViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentManageExtensionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupTransition(view)
        applyInsetsWithChild(binding.appBarLayout, binding.recyclerView, 104) {
            binding.fabContainer.applyInsets(it)
        }
        applyBackPressCallback()
        binding.appBarLayout.configureAppBar { offset ->
            binding.appBarOutline.alpha = offset
            binding.appBarOutline.isVisible = offset > 0
            binding.toolBar.alpha = 1 - offset
        }
        binding.toolBar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.toolBar.setOnMenuItemClickListener {
            viewModel.update(requireActivity(), true)
            true
        }

        FastScrollerHelper.applyTo(binding.recyclerView)
        binding.fabAddExtensions.setOnClickListener {
            ExtensionsAddBottomSheet().show(parentFragmentManager, null)
        }

        val tabs = ExtensionType.entries.map {
            binding.tabLayout.newTab().apply {
                setText(getType(it))
            }
        }
        binding.tabLayout.run {
            tabs.forEach { addTab(it) }
        }

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                viewModel.moveExtensionItem(toPos, fromPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun getMovementFlags(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder
            ) = makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)

        }

        val touchHelper = ItemTouchHelper(callback)
        val extensionAdapter = ExtensionAdapter(object : ExtensionAdapter.Listener {
            override fun onClick(extension: Extension<*>, view: View) {
                openFragment<ExtensionInfoFragment>(
                    view, ExtensionInfoFragment.getBundle(extension)
                )
            }

            override fun onDragHandleTouched(viewHolder: ExtensionAdapter.ViewHolder) {
                touchHelper.startDrag(viewHolder)
            }

            override fun onOpenClick(extension: Extension<*>) {
                viewModel.onExtensionSelected(extension as MusicExtension)
                parentFragmentManager.popBackStack()
                parentFragmentManager.popBackStack()
            }
        })

        observe(viewModel.manageExtListFlow) { list ->
            extensionAdapter.submit(list, viewModel.lastSelectedManageExt.value, viewModel.app.settings)
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            fun select(tab: TabLayout.Tab) {
                viewModel.lastSelectedManageExt.value = tab.position
            }

            override fun onTabSelected(tab: TabLayout.Tab) = select(tab)
            override fun onTabReselected(tab: TabLayout.Tab) = select(tab)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
        })

        binding.tabLayout.selectTab(tabs[viewModel.lastSelectedManageExt.value])
        binding.recyclerView.adapter = extensionAdapter.withEmptyAdapter()
        touchHelper.attachToRecyclerView(binding.recyclerView)
    }
}