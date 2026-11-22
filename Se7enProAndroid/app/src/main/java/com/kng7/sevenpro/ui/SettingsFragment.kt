package com.kng7.sevenpro.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.kng7.sevenpro.R
import com.kng7.sevenpro.data.SettingsStore
import com.kng7.sevenpro.databinding.FragmentSettingsBinding
import com.kng7.sevenpro.engine.SingBoxProcess

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupAppearance()
        setupTunnel()
        setupAbout()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun setupAppearance() {
        // Theme
        val themeOptions = listOf(
            getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark)
        )
        binding.spTheme.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, themeOptions))
        binding.spTheme.setText(themeOptions[when (SettingsStore.themeMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> 1
            AppCompatDelegate.MODE_NIGHT_YES -> 2
            else -> 0
        }], false)
        binding.spTheme.setOnItemClickListener { _, _, pos, _ ->
            val mode = when (pos) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            SettingsStore.themeMode = mode
            AppCompatDelegate.setDefaultNightMode(mode)
        }

        // Language
        val langCodes = listOf("system", "fa", "en", "ru")
        val langOptions = listOf(
            getString(R.string.lang_system), "فارسی", "English", "Русский"
        )
        binding.spLang.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, langOptions))
        binding.spLang.setText(langOptions[langCodes.indexOf(SettingsStore.language).coerceAtLeast(0)], false)
        binding.spLang.setOnItemClickListener { _, _, pos, _ ->
            val code = langCodes[pos]
            SettingsStore.language = code
            if (code == "system") {
                AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.getEmptyLocaleList())
            } else {
                AppCompatDelegate.setApplicationLocales(
                    androidx.core.os.LocaleListCompat.forLanguageTags(code)
                )
            }
        }
    }

    private fun setupTunnel() {
        binding.swIpv6.isChecked = SettingsStore.ipv6Enabled
        binding.swIpv6.setOnCheckedChangeListener { _, checked ->
            SettingsStore.ipv6Enabled = checked
        }

        binding.swIcmp.isChecked = SettingsStore.icmpReply
        binding.swIcmp.setOnCheckedChangeListener { _, checked ->
            SettingsStore.icmpReply = checked
        }

        binding.swBoot.isChecked = SettingsStore.autoBoot
        binding.swBoot.setOnCheckedChangeListener { _, checked ->
            SettingsStore.autoBoot = checked
        }

        binding.etPort.setText(SettingsStore.socksPort.toString())
        binding.etPort.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val v = binding.etPort.text?.toString()?.toIntOrNull() ?: 1080
                SettingsStore.socksPort = v.coerceIn(1024, 65535)
            }
        }
    }

    private fun setupAbout() {
        binding.btnApps.setOnClickListener {
            startActivity(Intent(requireContext(), AppPickerActivity::class.java))
        }
        binding.btnKillswitch.setOnClickListener {
            try {
                startActivity(Intent("android.settings.VPN_SETTINGS"))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), R.string.killswitch_open_settings, Toast.LENGTH_SHORT).show()
            }
        }
        val engineVer = SingBoxProcess.engineVersion(requireContext())
        binding.tvEngine.text = getString(
            R.string.engine_info,
            if (engineVer.isBlank()) getString(R.string.engine_not_run) else engineVer
        )
    }
}
