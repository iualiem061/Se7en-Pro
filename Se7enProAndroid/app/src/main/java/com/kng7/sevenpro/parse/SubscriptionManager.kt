package com.kng7.sevenpro.parse

import android.util.Base64
import com.kng7.sevenpro.data.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Fetches remote subscriptions and parses them into profiles. */
object SubscriptionManager {

    suspend fun fetch(url: String): Result<List<Profile>> = withContext(Dispatchers.IO) {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 12_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Se7enPro-Android/1.0")
            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext Result.failure(IllegalStateException("HTTP $code"))
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            Result.success(decode(text))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Decodes a subscription payload: base64 list, plain link list, or raw JSON. */
    fun decode(payload: String): List<Profile> {
        val trimmed = payload.trim()
        // Plain links?
        val direct = LinkParser.parse(trimmed)
        if (direct.isNotEmpty()) return dedupe(direct)
        // Base64 encoded list?
        val decoded = b64decode(trimmed)
        if (decoded != null) {
            val parsed = LinkParser.parse(decoded)
            if (parsed.isNotEmpty()) return dedupe(parsed)
        }
        return emptyList()
    }

    private fun dedupe(list: List<Profile>): List<Profile> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<Profile>()
        for (p in list) {
            val sig = p.signature()
            if (sig in seen) continue
            seen.add(sig)
            out.add(p)
        }
        return out
    }

    private fun b64decode(s: String): String? {
        if (s.isBlank()) return null
        val clean = s.trim().replace('-', '+').replace('_', '/').replace("\n", "").replace("\r", "")
        val padded = clean + "=".repeat((4 - clean.length % 4) % 4)
        return try {
            String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
