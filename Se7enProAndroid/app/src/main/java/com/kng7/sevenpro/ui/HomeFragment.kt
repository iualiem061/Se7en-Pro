package com.kng7.sevenpro.ui

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.kng7.sevenpro.R
import com.kng7.sevenpro.data.Profile
import com.kng7.sevenpro.data.ProfileStore
import com.kng7.sevenpro.data.SettingsStore
import com.kng7.sevenpro.databinding.FragmentHomeBinding
import com.kng7.sevenpro.engine.SingBoxProcess
import com.kng7.sevenpro.engine.TunnelController
import com.kng7.sevenpro.engine.TunnelState
import com.kng7.sevenpro.service.SevenVpnService
import com.kng7.sevenpro.util.Fmt
import com.kng7.sevenpro.util.Pinger
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ProfileAdapter

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == android.app.Activity.RESULT_OK) connect()
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = ProfileAdapter(
            onClick = { p -> selectProfile(p) },
            onLongClick = { p, anchor -> showItemMenu(p, anchor) }
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        binding.recycler.setHasFixedSize(true)

        binding.fabConnect.setOnClickListener { onFabClick() }
        binding.fabConnect.scaleX = 1.15f
        binding.fabConnect.scaleY = 1.15f

        observeState()
        observeStats()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    // ---------- list ----------

    fun refreshList() {
        val list = ProfileStore.allProfiles()
        adapter.submitList(list)
        binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        val selected = SettingsStore.selectedProfileId
        adapter.setSelected(selected)
    }

    private fun selectProfile(p: Profile) {
        SettingsStore.selectedProfileId = p.id
        adapter.setSelected(p.id)
        val st = TunnelController.currentState()
        if (st is TunnelState.Connected || st is TunnelState.Connecting) {
            Snackbar.make(binding.root, R.string.reconnect_hint, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showItemMenu(p: Profile, anchor: View) {
        val popup = android.widget.PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_profile_item, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.item_edit -> {
                    val i = Intent(requireContext(), ProfileEditorActivity::class.java)
                        .putExtra("profile_id", p.id)
                    startActivity(i)
                    true
                }
                R.id.item_ping -> { runPing(p); true }
                R.id.item_share -> { shareProfile(p); true }
                R.id.item_delete -> {
                    ProfileStore.deleteProfile(p.id)
                    if (SettingsStore.selectedProfileId == p.id) SettingsStore.selectedProfileId = ""
                    refreshList()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun runPing(p: Profile) {
        lifecycleScope.launch {
            val ms = Pinger.tcpPing(p)
            ProfileStore.updatePing(p.id, ms)
            refreshList()
            val msg = if (ms >= 0) getString(R.string.ping_result, ms) else getString(R.string.ping_failed)
            Toast.makeText(requireContext(), "${p.name}: $msg", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareProfile(p: Profile) {
        val text = "${p.name} — ${p.type} ${p.summary()}"
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(i, null))
    }

    // ---------- connect ----------

    private fun onFabClick() {
        when (TunnelController.currentState()) {
            is TunnelState.Connected, is TunnelState.Connecting -> disconnect()
            else -> {
                if (SettingsStore.selectedProfileId.isBlank()) {
                    Snackbar.make(binding.root, R.string.err_no_profile, Snackbar.LENGTH_SHORT).show()
                    return
                }
                if (android.net.VpnService.prepare(requireContext()) != null) {
                    vpnPermission.launch(android.net.VpnService.prepare(requireContext()))
                } else {
                    connect()
                }
            }
        }
    }

    private fun connect() {
        ContextCompat.startForegroundService(
            requireContext(), SevenVpnService.intent(requireContext(), true)
        )
    }

    private fun disconnect() {
        requireContext().startService(SevenVpnService.intent(requireContext(), false))
    }

    // ---------- observers ----------

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TunnelController.state.collect { st ->
                    when (st) {
                        is TunnelState.Connected -> {
                            binding.fabConnect.setImageResource(R.drawable.ic_stop)
                            binding.fabConnect.backgroundTintList =
                                ContextCompat.getColorStateList(requireContext(), R.color.state_connected)
                            binding.stateText.text = getString(R.string.state_connected)
                            binding.engineInfo.text =
                                getString(R.string.engine_running, SingBoxProcess.engineVersion(requireContext()))
                        }
                        is TunnelState.Connecting -> {
                            binding.fabConnect.setImageResource(R.drawable.ic_stop)
                            binding.fabConnect.backgroundTintList =
                                ContextCompat.getColorStateList(requireContext(), R.color.state_connecting)
                            binding.stateText.text = getString(R.string.state_connecting)
                            binding.engineInfo.text = ""
                        }
                        is TunnelState.Disconnecting -> {
                            binding.fabConnect.setImageResource(R.drawable.ic_play)
                            binding.fabConnect.backgroundTintList =
                                ContextCompat.getColorStateList(requireContext(), R.color.state_disconnected)
                            binding.stateText.text = getString(R.string.state_disconnecting)
                        }
                        is TunnelState.Error -> {
                            binding.fabConnect.setImageResource(R.drawable.ic_play)
                            binding.fabConnect.backgroundTintList =
                                ContextCompat.getColorStateList(requireContext(), R.color.state_error)
                            binding.stateText.text = getString(R.string.state_error)
                            binding.engineInfo.text = st.message.lines().firstOrNull() ?: ""
                            Snackbar.make(binding.root, st.message, Snackbar.LENGTH_LONG).show()
                        }
                        else -> {
                            binding.fabConnect.setImageResource(R.drawable.ic_play)
                            binding.fabConnect.backgroundTintList =
                                ContextCompat.getColorStateList(requireContext(), R.color.state_disconnected)
                            binding.stateText.text = getString(R.string.state_disconnected)
                            binding.engineInfo.text = ""
                        }
                    }
                    refreshList()
                }
            }
        }
    }

    private fun observeStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TunnelController.stats.collect { s ->
                    binding.chart.addSample(s.upBps.toFloat(), s.downBps.toFloat())
                    binding.statsText.text = getString(
                        R.string.stats_line,
                        Fmt.speed(s.upBps), Fmt.speed(s.downBps),
                        Fmt.bytes(s.upTotal), Fmt.bytes(s.downTotal)
                    )
                }
            }
        }
    }
}
