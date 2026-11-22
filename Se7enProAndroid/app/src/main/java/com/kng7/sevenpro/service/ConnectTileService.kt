package com.kng7.sevenpro.service

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.kng7.sevenpro.Constants
import com.kng7.sevenpro.data.SettingsStore
import com.kng7.sevenpro.engine.TunnelController
import com.kng7.sevenpro.engine.TunnelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick-settings tile for one-tap connect / disconnect. */
class ConnectTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
        scope.launch {
            TunnelController.state.collect { updateTile() }
        }
    }

    override fun onStopListening() {
        scope.cancel()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val connected = TunnelController.currentState() is TunnelState.Connected
        if (connected) {
            startService(SevenVpnService.intent(this, false))
        } else {
            // VPN permission must already be granted; otherwise open the app
            if (VpnService.prepare(this) == null && SettingsStore.selectedProfileId.isNotBlank()) {
                startService(SevenVpnService.intent(this, true))
            } else {
                startActivityAndCollapseCompat()
            }
        }
    }

    private fun startActivityAndCollapseCompat() {
        val intent = Intent(this, com.kng7.sevenpro.ui.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        } catch (_: Exception) {
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val st = TunnelController.currentState()
        when (st) {
            is TunnelState.Connected -> {
                tile.state = Tile.STATE_ACTIVE
                setSubtitle(tile, getString(com.kng7.sevenpro.R.string.tile_connected))
            }
            is TunnelState.Connecting, is TunnelState.Disconnecting -> {
                tile.state = Tile.STATE_INACTIVE
                setSubtitle(tile, getString(com.kng7.sevenpro.R.string.tile_connecting))
            }
            is TunnelState.Error -> {
                tile.state = Tile.STATE_INACTIVE
                setSubtitle(tile, getString(com.kng7.sevenpro.R.string.tile_error))
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                setSubtitle(tile, null)
            }
        }
        tile.updateTile()
    }

    private fun setSubtitle(tile: Tile, text: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = text
        }
    }
}
