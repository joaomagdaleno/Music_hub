package com.joaomagdaleno.music_hub.utils.ui

import android.content.Context
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import com.joaomagdaleno.music_hub.R

class SimpleItemSpan(
    val context: Context,
    private val onItemClicked: () -> Unit
) : ClickableSpan() {

    override fun onClick(widget: View) {
        onItemClicked()
    }

    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)
        ds.isUnderlineText = false
        context.run {
            ds.color = resources.getColor(R.color.button_player, theme)
        }
    }
}