package com.kng7.sevenpro.util

import com.kng7.sevenpro.data.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object Fmt {
    fun bytes(b: Long): String = when {
        b >= 1 shl 30 -> String.format("%.2f GB", b / 1073741824.0)
        b >= 1 shl 20 -> String.format("%.2f MB", b / 1048576.0)
        b >= 1 shl 10 -> String.format("%.1f KB", b / 1024.0)
        else -> "$b B"
    }

    fun speed(bps: Long): String = when {
        bps >= 1 shl 20 -> String.format("%.2f MB/s", bps / 1048576.0)
        bps >= 1 shl 10 -> String.format("%.1f KB/s", bps / 1024.0)
        bps > 0 -> "$bps B/s"
        else -> "0 B/s"
    }

    fun duration(secs: Long): String {
        val h = secs / 3600
        val m = (secs % 3600) / 60
        val s = secs % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }
}

object Pinger {
    /** TCP connect latency to the profile server. Returns -1 on failure. */
    suspend fun tcpPing(profile: Profile, timeoutMs: Int = 4000): Int = withContext(Dispatchers.IO) {
        var best = -1
        repeat(2) {
            try {
                val start = System.nanoTime()
                Socket().use { s ->
                    s.connect(InetSocketAddress(profile.server, profile.serverPort), timeoutMs)
                }
                val ms = ((System.nanoTime() - start) / 1_000_000).toInt()
                if (best < 0 || ms < best) best = ms
            } catch (_: Exception) {
            }
        }
        best
    }
}
