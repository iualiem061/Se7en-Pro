package com.kng7.sevenpro.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kng7.sevenpro.Constants
import com.kng7.sevenpro.data.SettingsStore
import com.kng7.sevenpro.engine.TunnelController
import com.kng7.sevenpro.engine.TunnelState

/** Auto-connect on device boot (if enabled in settings and VPN permission already granted). */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!SettingsStore.autoBoot) return
        if (SettingsStore.selectedProfileId.isBlank()) return
        if (TunnelController.currentState() !is TunnelState.Disconnected) return

        val service = Intent(context, SevenVpnService::class.java).setAction(Constants.ACTION_CONNECT)
        try {
            context.startForegroundService(service)
        } catch (_: Exception) {
        }
    }
}
