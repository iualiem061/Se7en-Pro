package com.kng7.sevenpro.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Tunnel lifecycle state, shared between the VPN service and the UI. */
sealed class TunnelState {
    object Disconnected : TunnelState()
    object Connecting : TunnelState()
    data class Connected(val startedAt: Long) : TunnelState()
    object Disconnecting : TunnelState()
    data class Error(val message: String) : TunnelState()
}

/** Live traffic counters / speeds. */
data class TunnelStats(
    val upBps: Long = 0,
    val downBps: Long = 0,
    val upTotal: Long = 0,
    val downTotal: Long = 0
)

/**
 * Singleton state holder: the VPN service writes, the UI observes.
 */
object TunnelController {

    private val _state = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    val state: StateFlow<TunnelState> = _state

    private val _stats = MutableStateFlow(TunnelStats())
    val stats: StateFlow<TunnelStats> = _stats

    var lastError: String? = null
        private set

    fun setState(s: TunnelState) {
        if (s is TunnelState.Error) lastError = s.message
        _state.value = s
    }

    fun currentState(): TunnelState = _state.value

    fun updateStats(upBps: Long, downBps: Long, upTotal: Long, downTotal: Long) {
        _stats.value = TunnelStats(upBps, downBps, upTotal, downTotal)
    }

    fun resetStats() {
        _stats.value = TunnelStats()
    }
}
