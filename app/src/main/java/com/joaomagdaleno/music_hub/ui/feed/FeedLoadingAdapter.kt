package com.joaomagdaleno.music_hub.ui.feed

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.paging.LoadState
import com.joaomagdaleno.music_hub.databinding.ItemShelfErrorBinding
import com.joaomagdaleno.music_hub.databinding.ItemShelfLoginRequiredBinding
import com.joaomagdaleno.music_hub.databinding.ItemShelfNotLoadingBinding
import com.joaomagdaleno.music_hub.common.helpers.ClientException
import com.joaomagdaleno.music_hub.utils.ui.UiUtils
import com.joaomagdaleno.music_hub.ui.common.GridAdapter
import com.joaomagdaleno.music_hub.ui.common.PagedSource
import com.joaomagdaleno.music_hub.utils.ui.scrolling.ScrollAnimLoadStateAdapter
import com.joaomagdaleno.music_hub.utils.ui.scrolling.ScrollAnimViewHolder

class FeedLoadingAdapter(
    val listener: Listener? = null,
    val loadingAdapter: (ViewGroup) -> ViewHolder,
) : ScrollAnimLoadStateAdapter<FeedLoadingAdapter.ViewHolder>(), GridAdapter {

    interface Listener {
        fun onRetry()
        fun onError(view: View, error: Throwable)
        fun onLoginRequired(view: View, error: ClientException.LoginRequired)
    }

    abstract class ViewHolder(val view: View) : ScrollAnimViewHolder(view) {
        open fun bind(loadState: LoadState) {}
    }

    data class NotLoading(
        val inflater: LayoutInflater,
        val parent: ViewGroup,
        val listener: Listener?,
        val binding: ItemShelfNotLoadingBinding =
            ItemShelfNotLoadingBinding.inflate(inflater, parent, false)
    ) : ViewHolder(binding.root) {
        override fun bind(loadState: LoadState) {
            binding.retry.setOnClickListener {
                listener?.onRetry()
            }
        }
    }

    data class Error(
        val inflater: LayoutInflater,
        val parent: ViewGroup,
        val listener: Listener?,
        val binding: ItemShelfErrorBinding =
            ItemShelfErrorBinding.inflate(inflater, parent, false)
    ) : ViewHolder(binding.root) {
        override fun bind(loadState: LoadState) {
            loadState as LoadState.Error
            val throwable = loadState.error
            binding.error.run {
                transitionName = throwable.hashCode().toString()
                text = UiUtils.getFinalExceptionTitle(context, throwable)
            }
            binding.errorView.setOnClickListener {
                listener?.onError(binding.error, throwable)
            }
            binding.retry.setOnClickListener {
                listener?.onRetry()
            }
        }
    }

    data class LoginRequired(
        val inflater: LayoutInflater,
        val parent: ViewGroup,
        val listener: Listener?,
        val binding: ItemShelfLoginRequiredBinding
        = ItemShelfLoginRequiredBinding.inflate(inflater, parent, false)
    ) : ViewHolder(binding.root) {
        override fun bind(loadState: LoadState) {
            val error = (loadState as LoadState.Error).error
            val appError = error as ClientException.LoginRequired
            binding.error.run {
                text = UiUtils.getFinalExceptionTitle(context, appError)
            }
            binding.login.transitionName = appError.hashCode().toString()
            binding.login.setOnClickListener {
                listener?.onLoginRequired(it, appError)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, loadState: LoadState): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (getStateViewType(loadState)) {
            0 -> loadingAdapter(parent)
            1 -> NotLoading(inflater, parent, listener)
            2 -> Error(inflater, parent, listener)
            3 -> LoginRequired(inflater, parent, listener)
            else -> throw IllegalStateException()
        }
    }

    override fun getStateViewType(loadState: LoadState): Int {
        return when (loadState) {
            is LoadState.Loading -> 0
            is LoadState.NotLoading -> 1
            is LoadState.Error -> {
                when (loadState.error) {
                    is ClientException.LoginRequired -> 3
                    is PagedSource.LoadingException -> 0
                    else -> 2
                }
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, loadState: LoadState) {
        super.onBindViewHolder(holder, loadState)
        holder.bind(loadState)
    }

    override val adapter = this
    override fun getSpanSize(position: Int, width: Int, count: Int) = count

    companion object {
        fun createListener(fragment: Fragment, retry: () -> Unit) =
            object : Listener {
                override fun onRetry() {
                    retry()
                }

                override fun onError(view: View, error: Throwable) {
                    UiUtils.getExceptionMessage(fragment.requireActivity(), error, view).action?.handler?.invoke()
                }

                override fun onLoginRequired(view: View, error: ClientException.LoginRequired) {
                    onError(view, error)
                }
            }
    }
}