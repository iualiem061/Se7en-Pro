package com.kng7.sevenpro.data

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

/** SharedPreferences-backed app settings. */
object SettingsStore {

    private const val PREFS = "settings"
    private lateinit var prefs: SharedPreferences

    var selectedProfileId: String
        get() = prefs.getString("selected_profile", "") ?: ""
        set(v) = prefs.edit().putString("selected_profile", v).apply()

    /** 0 = system, 1 = light, 2 = dark */
    var themeMode: Int
        get() = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(v) = prefs.edit().putInt("theme_mode", v).apply()

    /** "system" | "fa" | "en" | "ru" */
    var language: String
        get() = prefs.getString("language", "system") ?: "system"
        set(v) = prefs.edit().putString("language", v).apply()

    var ipv6Enabled: Boolean
        get() = prefs.getBoolean("ipv6", false)
        set(v) = prefs.edit().putBoolean("ipv6", v).apply()

    var socksPort: Int
        get() = prefs.getInt("socks_port", 1080)
        set(v) = prefs.edit().putInt("socks_port", v).apply()

    /** Apps whose traffic is excluded from the VPN (bypass mode). */
    var bypassApps: Set<String>
        get() = prefs.getStringSet("bypass_apps", emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet("bypass_apps", v).apply()

    var autoBoot: Boolean
        get() = prefs.getBoolean("auto_boot", false)
        set(v) = prefs.edit().putBoolean("auto_boot", v).apply()

    /** Subscription auto-update interval in minutes (0 = manual only). */
    var subUpdateMinutes: Int
        get() = prefs.getInt("sub_update_minutes", 0)
        set(v) = prefs.edit().putInt("sub_update_minutes", v).apply()

    /** Whether ICMP echo replies are generated inside the tunnel. */
    var icmpReply: Boolean
        get() = prefs.getBoolean("icmp_reply", true)
        set(v) = prefs.edit().putBoolean("icmp_reply", v).apply()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}
