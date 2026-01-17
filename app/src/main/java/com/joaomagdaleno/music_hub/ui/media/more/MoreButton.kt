package com.joaomagdaleno.music_hub.ui.media.more

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.DiffUtil

data class MoreButton(
    val id: String, val title: String, val icon: Int, val onClick: () -> Unit
) {
    object DiffCallback : DiffUtil.ItemCallback<MoreButton>() {
        override fun areItemsTheSame(oldItem: MoreButton, newItem: MoreButton) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: MoreButton, newItem: MoreButton) =
            oldItem == newItem
    }

    companion object {
        fun createButton(
            dialog: DialogFragment, id: String, title: String, icon: Int, onClick: () -> Unit
        ) = MoreButton(id, title, icon) {
            onClick()
            dialog.dismiss()
        }

        fun createButton(
            dialog: DialogFragment, id: String, title: Int, icon: Int, onClick: () -> Unit
        ) = createButton(dialog, id, dialog.getString(title), icon, onClick)
    }
}

fun DialogFragment.button(
    id: String, @StringRes title: Int, @DrawableRes icon: Int, onClick: () -> Unit
) = MoreButton.createButton(this, id, title, icon, onClick)

fun DialogFragment.button(
    id: String, title: String, @DrawableRes icon: Int, onClick: () -> Unit
) = MoreButton.createButton(this, id, title, icon, onClick)