package com.kng7.sevenpro.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.kng7.sevenpro.Constants
import com.kng7.sevenpro.R
import com.kng7.sevenpro.data.Profile
import com.kng7.sevenpro.data.ProfileStore
import com.kng7.sevenpro.data.SettingsStore
import com.kng7.sevenpro.engine.SingBoxConfigBuilder
import com.kng7.sevenpro.engine.SingBoxProcess
import com.kng7.sevenpro.engine.TunnelController
import com.kng7.sevenpro.engine.TunnelState
import com.kng7.sevenpro.ui.MainActivity
import com.kng7.sevenpro.util.Fmt
import hev.htproxy.TProxyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The VPN tunnel service.
 *
 * Connection pipeline:
 *  1. generate sing-box config for the selected profile
 *  2. spawn the sing-box engine process (socks5 inbound on 127.0.0.1:port)
 *  3. establish a VpnService TUN interface (own UID excluded to avoid loops)
 *  4. start hev-socks5-tunnel with the TUN fd -> forwards traffic to the socks5 server
 */
class SevenVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunFd: ParcelFileDescriptor? = null
    private var statsJob: Job? = null
    private var lastTx = 0L
    private var lastRx = 0L
    private var notifTick = 0

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == Constants.ACTION_DISCONNECT) {
            scope.launch { stopTunnel() }
            return START_NOT_STICKY
        }
        // Promote to foreground immediately (required on Android 12+)
        startForegroundCompat(buildNotification(getString(R.string.notif_connecting), ""))
        scope.launch { startTunnel() }
        return START_STICKY
    }

    private suspend fun startTunnel() = withContext(Dispatchers.IO) {
        if (tunFd != null) return@withContext
        TunnelController.setState(TunnelState.Connecting)
        TunnelController.resetStats()

        val selectedId = SettingsStore.selectedProfileId
        val profile: Profile = ProfileStore.selectedProfile(selectedId).also {
            if (it == null) {
                fail(getString(R.string.err_no_profile))
                return@withContext
            }
        }!!

        // 1. Engine config
        val configJson = try {
            SingBoxConfigBuilder.build(profile).toString()
        } catch (e: Exception) {
            fail(getString(R.string.err_config) + ": " + e.message)
            return@withContext
        }
        val configFile = File(filesDir, "singbox.json")
        try { configFile.writeText(configJson) } catch (e: Exception) {
            fail(e.message ?: "config write failed"); return@withContext
        }

        // 2. Spawn engine
        if (!SingBoxProcess.start(applicationContext, configFile.absolutePath)) {
            fail(getString(R.string.err_engine_start))
            return@withContext
        }
        val port = SettingsStore.socksPort
        if (!SingBoxProcess.waitReady(port, 15_000)) {
            val tail = SingBoxProcess.logs().takeLast(6).joinToString("\n")
            fail(getString(R.string.err_engine_ready) + "\n" + tail)
            SingBoxProcess.stop()
            return@withContext
        }

        // 3. Build VPN interface
        val builder = Builder()
            .setSession(profile.name.ifBlank { getString(R.string.app_name) })
            .setMtu(Constants.VPN_MTU)
            .setBlocking(false)
        builder.addAddress(Constants.VPN_ADDR_V4, 32)
        builder.addRoute("0.0.0.0", 0)
        builder.addDnsServer(Constants.VPN_DNS_V4)
        if (SettingsStore.ipv6Enabled) {
            builder.addAddress(Constants.VPN_ADDR_V6, 128)
            builder.addRoute("::", 0)
            builder.addDnsServer(Constants.VPN_DNS_V6)
        }
        // Exclude our own app (engine process shares our UID) -> prevents routing loops
        try { builder.addDisallowedApplication(packageName) } catch (_: Exception) {}
        // User-selected bypass apps (split tunneling)
        SettingsStore.bypassApps.forEach { pkg ->
            try { builder.addDisallowedApplication(pkg) } catch (_: Exception) {}
        }
        val fd = try {
            builder.establish()
        } catch (e: Exception) {
            fail(getString(R.string.err_vpn_establish) + ": " + e.message)
            SingBoxProcess.stop()
            return@withContext
        }
        if (fd == null) {
            fail(getString(R.string.err_vpn_permission))
            SingBoxProcess.stop()
            return@withContext
        }
        tunFd = fd

        // 4. Start the TUN <-> SOCKS bridge with the tunnel fd
        val hevLog = File(cacheDir, "hev.log").absolutePath
        val yaml = SingBoxConfigBuilder.buildHevYaml(port, SettingsStore.icmpReply, hevLog)
        val yamlFile = File(cacheDir, "hev.yaml")
        try { yamlFile.writeText(yaml) } catch (e: Exception) {
            fail(e.message ?: "yaml write failed"); return@withContext
        }
        val ok = try {
            TProxyService.TProxyStartService(yamlFile.absolutePath, fd.getFd())
        } catch (e: Exception) {
            fail("hev tunnel: " + e.message)
            SingBoxProcess.stop()
            return@withContext
        }
        if (!ok || !TProxyService.TProxyIsRunning()) {
            fail(getString(R.string.err_tunnel_bridge))
            SingBoxProcess.stop()
            return@withContext
        }

        lastTx = 0; lastRx = 0
        TunnelController.setState(TunnelState.Connected(SystemClock.elapsedRealtime()))
        updateNotification(getString(R.string.notif_connected), profile.name.ifBlank { getString(R.string.app_name) })
        startStatsLoop(profile)
    }

    private fun startStatsLoop(profile: Profile) {
        statsJob?.cancel()
        statsJob = scope.launch {
            var elapsedBase = TunnelController.currentState().let {
                (it as? TunnelState.Connected)?.startedAt ?: SystemClock.elapsedRealtime()
            }
            while (isActive && tunFd != null) {
                delay(1000)
                try {
                    val s = TProxyService.TProxyGetStats()
                    // [tx_packets, tx_bytes, rx_packets, rx_bytes]
                    if (s != null && s.size >= 4) {
                        val tx = s[1]; val rx = s[3]
                        val upDelta = (tx - lastTx).coerceAtLeast(0)
                        val downDelta = (rx - lastRx).coerceAtLeast(0)
                        TunnelController.updateStats(
                            upBps = upDelta,
                            downBps = downDelta,
                            upTotal = tx,
                            downTotal = rx
                        )
                        lastTx = tx; lastRx = rx
                        notifTick++
                        if (notifTick % 3 == 0) {
                            val secs = (SystemClock.elapsedRealtime() - elapsedBase) / 1000
                            updateNotification(
                                Fmt.speed(downDelta) + " | " + Fmt.duration(secs),
                                profile.name.ifBlank { getString(R.string.app_name) }
                            )
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private suspend fun stopTunnel() = withContext(Dispatchers.IO) {
        if (tunFd == null && !SingBoxProcess.isRunning()) {
            TunnelController.setState(TunnelState.Disconnected)
            stopSelf()
            return@withContext
        }
        TunnelController.setState(TunnelState.Disconnecting)
        statsJob?.cancel()
        try { TProxyService.TProxyStopService() } catch (_: Exception) {}
        SingBoxProcess.stop()
        try { tunFd?.close() } catch (_: Exception) {}
        tunFd = null
        TunnelController.setState(TunnelState.Disconnected)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fail(msg: String) {
        TunnelController.setState(TunnelState.Error(msg))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        // System revoked the VPN (user toggled another VPN / always-on)
        scope.launch { stopTunnel() }
        super.onRevoke()
    }

    override fun onDestroy() {
        statsJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep running as a foreground service
        super.onTaskRemoved(rootIntent)
    }

    // ---------- Notification ----------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                Constants.CHANNEL_VPN,
                getString(R.string.channel_vpn),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE)
        val stop = Intent(this, SevenVpnService::class.java).apply { action = Constants.ACTION_DISCONNECT }
        val stopPi = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, Constants.CHANNEL_VPN)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.action_disconnect), stopPi)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(Constants.NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Constants.NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Constants.NOTIFICATION_ID, n)
        }
    }

    companion object {
        fun intent(context: Context, connect: Boolean): Intent =
            Intent(context, SevenVpnService::class.java)
                .setAction(if (connect) Constants.ACTION_CONNECT else Constants.ACTION_DISCONNECT)
    }
}
