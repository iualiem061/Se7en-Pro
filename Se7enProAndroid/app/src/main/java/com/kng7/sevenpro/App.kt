package com.kng7.sevenpro

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.kng7.sevenpro.data.SettingsStore

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        // Apply persisted theme mode
        AppCompatDelegate.setDefaultNightMode(SettingsStore.themeMode)
    }
}
