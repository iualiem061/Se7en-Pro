package com.kng7.sevenpro.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.kng7.sevenpro.R
import com.kng7.sevenpro.data.Profile
import com.kng7.sevenpro.data.ProfileTypes
import com.kng7.sevenpro.databinding.ItemProfileBinding

class ProfileAdapter(
    private val onClick: (Profile) -> Unit,
    private val onLongClick: (Profile, View) -> Unit
) : ListAdapter<Profile, ProfileAdapter.VH>(DIFF) {

    private var selectedId: String = ""

    fun setSelected(id: String) {
        selectedId = id
        notifyDataSetChanged()
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Profile>() {
            override fun areItemsTheSame(a: Profile, b: Profile) = a.id == b.id
            override fun areContentsTheSame(a: Profile, b: Profile) = a == b
        }
    }

    inner class VH(val binding: ItemProfileBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = getItem(position)
        val ctx = holder.binding.root.context

        holder.binding.tvName.text = p.name.ifBlank { p.server }
        holder.binding.tvSummary.text = "${typeName(ctx, p.type)} · ${p.summary()}"
        holder.binding.tvPing.text = when {
            p.pingMs in 0..150 -> "${p.pingMs} ms ✓"
            p.pingMs > 0 -> "${p.pingMs} ms"
            else -> ""
        }
        holder.binding.tvPing.setTextColor(
            when {
                p.pingMs in 0..150 -> ctx.getColor(R.color.ping_good)
                p.pingMs > 0 -> ctx.getColor(R.color.ping_mid)
                else -> ctx.getColor(android.R.color.transparent)
            }
        )

        val selected = p.id == selectedId
        holder.binding.card.strokeWidth = if (selected) 2 else 0
        holder.binding.card.setStrokeColor(
            ctx.getColor(if (selected) R.color.accent else R.color.card_stroke)
        )
        holder.binding.ivSelected.visibility = if (selected) View.VISIBLE else View.GONE

        (holder.binding.root as MaterialCardView).setOnClickListener { onClick(p) }
        (holder.binding.root as MaterialCardView).setOnLongClickListener {
            onLongClick(p, it); true
        }
    }

    private fun typeName(ctx: android.content.Context, type: String): String = when (type) {
        ProfileTypes.VMESS -> "VMess"
        ProfileTypes.VLESS -> "VLESS"
        ProfileTypes.TROJAN -> "Trojan"
        ProfileTypes.SHADOWSOCKS -> "Shadowsocks"
        ProfileTypes.HYSTERIA2 -> "Hysteria2"
        ProfileTypes.TUIC -> "TUIC"
        ProfileTypes.WIREGUARD -> "WARP/WireGuard"
        ProfileTypes.SOCKS -> "SOCKS5"
        ProfileTypes.HTTP -> "HTTP"
        ProfileTypes.TORSOCKS -> "Tor (Orbot)"
        ProfileTypes.SINGBOX_JSON -> "sing-box JSON"
        else -> type
    }
}
