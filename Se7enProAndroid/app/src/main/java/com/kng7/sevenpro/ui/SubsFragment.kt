package com.kng7.sevenpro.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.kng7.sevenpro.R
import com.kng7.sevenpro.data.ProfileStore
import com.kng7.sevenpro.data.Subscription
import com.kng7.sevenpro.databinding.FragmentSubsBinding
import com.kng7.sevenpro.parse.SubscriptionManager
import kotlinx.coroutines.launch

class SubsFragment : Fragment() {

    private var _binding: FragmentSubsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SubAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = SubAdapter(
            onClick = { s, anchor -> showMenu(s, anchor) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        binding.fabAdd.setOnClickListener { showAddDialog() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun refresh() {
        val subs = ProfileStore.allSubs()
        adapter.submitList(subs)
        binding.emptyView.visibility = if (subs.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showAddDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_sub_edit, null)
        val etName = view.findViewById<TextInputEditText>(R.id.etSubName)
        val etUrl = view.findViewById<TextInputEditText>(R.id.etSubUrl)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sub_add)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val url = etUrl.text?.toString()?.trim() ?: ""
                if (url.isBlank()) return@setPositiveButton
                val s = Subscription(
                    name = etName.text?.toString()?.trim().takeUnless { it.isNullOrBlank() } ?: url.substringBefore("//").substringAfterLast(".") ,
                    url = url
                )
                ProfileStore.addSub(s)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showMenu(s: Subscription, anchor: View) {
        val popup = android.widget.PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_sub_item, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.item_refresh -> { refreshSub(s); true }
                R.id.item_delete -> {
                    ProfileStore.deleteSub(s.id); refresh(); true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun refreshSub(s: Subscription) {
        lifecycleScope.launch {
            val result = SubscriptionManager.fetch(s.url)
            result.fold(
                onSuccess = { profiles ->
                    val added = ProfileStore.mergeSubscription(s.id, profiles)
                    s.lastUpdate = System.currentTimeMillis()
                    s.profileCount = profiles.size
                    ProfileStore.updateSub(s)
                    refresh()
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.sub_updated, profiles.size, added),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onFailure = { e ->
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.sub_failed, e.message ?: ""),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }
}
