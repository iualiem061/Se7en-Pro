package com.kng7.sevenpro.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kng7.sevenpro.R
import com.kng7.sevenpro.data.Subscription
import com.kng7.sevenpro.databinding.ItemSubBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SubAdapter(
    private val onClick: (Subscription, View) -> Unit
) : ListAdapter<Subscription, SubAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Subscription>() {
            override fun areItemsTheSame(a: Subscription, b: Subscription) = a.id == b.id
            override fun areContentsTheSame(a: Subscription, b: Subscription) = a == b
        }
    }

    class VH(val binding: ItemSubBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSubBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = getItem(position)
        holder.binding.tvName.text = s.name
        holder.binding.tvUrl.text = s.url
        holder.binding.tvMeta.text = holder.binding.root.context.getString(
            R.string.sub_meta,
            s.profileCount,
            if (s.lastUpdate > 0)
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(s.lastUpdate))
            else holder.binding.root.context.getString(R.string.sub_never)
        )
        holder.binding.root.setOnClickListener { onClick(s, it) }
    }
}
