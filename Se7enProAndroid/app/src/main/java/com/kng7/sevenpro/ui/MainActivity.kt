package com.kng7.sevenpro.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kng7.sevenpro.R
import com.kng7.sevenpro.data.Profile
import com.kng7.sevenpro.data.ProfileStore
import com.kng7.sevenpro.data.SettingsStore
import com.kng7.sevenpro.databinding.ActivityMainBinding
import com.kng7.sevenpro.parse.LinkParser

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> showFragment(HomeFragment(), "home")
                R.id.nav_subs -> showFragment(SubsFragment(), "subs")
                R.id.nav_settings -> showFragment(SettingsFragment(), "settings")
            }
            true
        }
        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.nav_home
        }

        requestNotificationPermissionIfNeeded()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun showFragment(f: Fragment, tag: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, f, tag)
            .commit()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /** Import proxy links shared from other apps (QR scanners, browsers, ...). */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val shared = when {
            intent.action == Intent.ACTION_SEND && intent.type == "text/plain" ->
                intent.getStringExtra(Intent.EXTRA_TEXT)
            intent.action == Intent.ACTION_VIEW -> intent.data?.toString()
            else -> null
        } ?: return
        val profiles = LinkParser.parse(shared)
        if (profiles.isEmpty()) {
            Toast.makeText(this, R.string.import_failed, Toast.LENGTH_SHORT).show()
            return
        }
        var added = 0
        profiles.forEach { p: Profile ->
            if (!ProfileStore.containsSignature(p.signature())) {
                ProfileStore.addProfile(p)
                added++
            }
        }
        Toast.makeText(this, getString(R.string.imported_count, added), Toast.LENGTH_SHORT).show()
        binding.bottomNav.selectedItemId = R.id.nav_home
    }

    // ---------- Toolbar menu ----------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_toolbar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_add -> {
                startActivity(Intent(this, ProfileEditorActivity::class.java)); true
            }
            R.id.menu_import -> { showImportDialog(); true }
            R.id.menu_apps -> {
                startActivity(Intent(this, AppPickerActivity::class.java)); true
            }
            R.id.menu_killswitch -> { showKillSwitchDialog(); true }
            R.id.menu_logs -> {
                startActivity(Intent(this, LogsActivity::class.java)); true
            }
            R.id.menu_about -> { showAbout(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showImportDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_input, null)
        val et = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etInput)
        et.hint = getString(R.string.import_hint)
        et.setSingleLine(false)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.import_title)
            .setView(view)
            .setPositiveButton(R.string.import_action) { _, _ ->
                val text = et.text?.toString() ?: ""
                val profiles = LinkParser.parse(text)
                var added = 0
                profiles.forEach { p ->
                    if (!ProfileStore.containsSignature(p.signature())) {
                        ProfileStore.addProfile(p); added++
                    }
                }
                Toast.makeText(this, getString(R.string.imported_count, added), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showKillSwitchDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.killswitch_title)
            .setMessage(R.string.killswitch_message)
            .setPositiveButton(R.string.killswitch_open_settings) { _, _ ->
                try {
                    startActivity(Intent("android.settings.VPN_SETTINGS"))
                } catch (e: Exception) {
                    try {
                        startActivity(Intent("android.settings.SETTINGS"))
                    } catch (_: Exception) {}
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAbout() {
        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (_: Exception) { "?" }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.app_name))
            .setMessage(getString(R.string.about_text, ver))
            .setPositiveButton(R.string.about_source) { _, _ ->
                try {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/KNG7-P/Se7en-Pro"))
                    )
                } catch (_: Exception) {}
            }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }
}
