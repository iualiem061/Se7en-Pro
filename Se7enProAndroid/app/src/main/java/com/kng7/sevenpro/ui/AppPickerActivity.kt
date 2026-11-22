package com.kng7.sevenpro.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kng7.sevenpro.R
import com.kng7.sevenpro.data.SettingsStore
import com.kng7.sevenpro.databinding.ActivityAppPickerBinding

/**
 * Split tunneling: pick apps whose traffic bypasses the VPN.
 */
class AppPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppPickerBinding
    private val apps = mutableListOf<AppInfo>()
    private val filtered = mutableListOf<AppInfo>()
    private val selected = mutableSetOf<String>()
    private lateinit var adapter: AppAdapter

    data class AppInfo(
        val label: String,
        val pkg: String,
        val icon: android.graphics.drawable.Drawable?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.apps_title)

        selected.addAll(SettingsStore.bypassApps)
        loadApps()

        adapter = AppAdapter()
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        applyFilter("")

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = applyFilter(s?.toString() ?: "")
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        binding.btnSave.setOnClickListener {
            SettingsStore.bypassApps = selected.toSet()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadApps() {
        val pm = packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val resolve = pm.queryIntentActivities(intent, 0)
        val self = packageName
        val seen = mutableSetOf<String>()
        apps.clear()
        resolve.forEach { info ->
            val pkg = info.activityInfo.packageName
            if (pkg == self || pkg in seen) return@forEach
            seen.add(pkg)
            apps.add(
                AppInfo(
                    label = info.loadLabel(pm).toString(),
                    pkg = pkg,
                    icon = try { info.loadIcon(pm) } catch (_: Exception) { null }
                )
            )
        }
        apps.sortBy { it.label.lowercase() }
    }

    private fun applyFilter(q: String) {
        filtered.clear()
        filtered.addAll(apps.filter { it.label.contains(q, true) || it.pkg.contains(q, true) })
        adapter.notifyDataSetChanged()
    }

    inner class AppAdapter : RecyclerView.Adapter<AppAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.ivIcon)
            val label: TextView = v.findViewById(R.id.tvLabel)
            val pkg: TextView = v.findViewById(R.id.tvPkg)
            val check: ImageView = v.findViewById(R.id.ivCheck)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = filtered.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val app = filtered[position]
            holder.label.text = app.label
            holder.pkg.text = app.pkg
            if (app.icon != null) holder.icon.setImageDrawable(app.icon)
            else holder.icon.setImageResource(R.drawable.ic_apps)
            holder.check.visibility = if (app.pkg in selected) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener {
                if (app.pkg in selected) selected.remove(app.pkg) else selected.add(app.pkg)
                holder.check.visibility = if (app.pkg in selected) View.VISIBLE else View.GONE
            }
        }
    }
}
