package com.close.hook.ads.ui.adapter

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.close.hook.ads.data.model.SubscriptionSource
import com.close.hook.ads.databinding.ItemSubscriptionSourceBinding
import java.net.URI

class SubscriptionAdapter(
    private val onToggle: (SubscriptionSource) -> Unit,
    private val onRefresh: (SubscriptionSource) -> Unit,
    private val onEdit: (SubscriptionSource) -> Unit,
    private val onDelete: (SubscriptionSource) -> Unit
) : ListAdapter<SubscriptionSource, SubscriptionAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSubscriptionSourceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemSubscriptionSourceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(source: SubscriptionSource) {
            binding.cardView.alpha = if (source.enabled) 1f else 0.62f
            binding.name.text = source.name
            binding.authorText.text = binding.root.context.getString(
                com.close.hook.ads.R.string.subscription_author,
                resolveAuthor(source.url)
            )
            binding.urlText.text = source.url
            binding.statusText.text = buildStatusText(source)
            binding.errorText.isVisible = !source.lastError.isNullOrBlank()
            binding.errorText.text = source.lastError

            binding.enableSwitch.setOnCheckedChangeListener(null)
            binding.enableSwitch.isChecked = source.enabled
            binding.enableSwitch.setOnCheckedChangeListener { _, _ -> onToggle(source) }
            binding.cardView.setOnClickListener { onEdit(source) }
            binding.refreshButton.setOnClickListener { onRefresh(source) }
            binding.deleteButton.setOnClickListener { onDelete(source) }
        }

        private fun buildStatusText(source: SubscriptionSource): String {
            val ctx = binding.root.context
            val updated = if (source.lastSuccessAt > 0) {
                val diff = System.currentTimeMillis() - source.lastSuccessAt
                if (diff < DateUtils.MINUTE_IN_MILLIS) {
                    ctx.getString(com.close.hook.ads.R.string.subscription_just_now)
                } else {
                    DateUtils.getRelativeTimeSpanString(
                        source.lastSuccessAt,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    ).toString()
                }
            } else {
                ctx.getString(com.close.hook.ads.R.string.subscription_never_updated)
            }
            return ctx.getString(
                com.close.hook.ads.R.string.subscription_status,
                source.ruleCount,
                updated
            )
        }

        private fun resolveAuthor(url: String): String {
            val context = binding.root.context
            return runCatching {
                val uri = URI(url)
                val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
                when (uri.host?.lowercase()) {
                    "github.com",
                    "raw.githubusercontent.com",
                    "gist.githubusercontent.com",
                    "gitlab.com" -> segments.firstOrNull()
                    else -> null
                }
            }.getOrNull().orEmpty().ifBlank {
                context.getString(com.close.hook.ads.R.string.subscription_unknown_author)
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SubscriptionSource>() {
            override fun areItemsTheSame(a: SubscriptionSource, b: SubscriptionSource) = a.id == b.id
            override fun areContentsTheSame(a: SubscriptionSource, b: SubscriptionSource) = a == b
        }
    }
}
