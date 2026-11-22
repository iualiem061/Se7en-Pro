package com.kng7.sevenpro.parse

import android.net.Uri
import android.util.Base64
import com.kng7.sevenpro.Constants
import com.kng7.sevenpro.data.Profile
import com.kng7.sevenpro.data.ProfileTypes
import org.json.JSONObject

/**
 * Parses proxy share links (vmess://, vless://, trojan://, ss://, hysteria2://,
 * tuic://, warp://, socks://, http://) and raw sing-box JSON into [Profile]s.
 */
object LinkParser {

    fun parse(text: String): List<Profile> {
        val out = mutableListOf<Profile>()
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            parseOne(line)?.let { out.add(it) }
        }
        return out
    }

    fun parseOne(line: String): Profile? {
        val l = line.trim()
        if (l.startsWith("vmess://")) return parseVmess(l)
        if (l.startsWith("vless://")) return parseUri(l, ProfileTypes.VLESS)
        if (l.startsWith("trojan://")) return parseUri(l, ProfileTypes.TROJAN)
        if (l.startsWith("ss://")) return parseShadowsocks(l)
        if (l.startsWith("hysteria2://") || l.startsWith("hy2://")) return parseUri(l, ProfileTypes.HYSTERIA2)
        if (l.startsWith("tuic://")) return parseUri(l, ProfileTypes.TUIC)
        if (l.startsWith("warp://")) return parseWarp(l)
        if (l.startsWith("socks://") || l.startsWith("socks5://")) return parseUri(l, ProfileTypes.SOCKS)
        if (l.startsWith("http://") || l.startsWith("https://")) {
            // only treat as proxy link if it carries userinfo@host:port
            val p = parseUri(l, ProfileTypes.HTTP)
            return p
        }
        if (l.startsWith("{")) return parseSingboxJson(l)
        return null
    }

    // ---------- helpers ----------

    private fun b64(s: String): String? {
        if (s.isBlank()) return null
        val clean = s.trim().replace('-', '+').replace('_', '/')
        val padded = clean + "=".repeat((4 - clean.length % 4) % 4)
        val candidates = listOf(padded, s.trim())
        for (c in candidates) {
            try {
                val bytes = Base64.decode(c, Base64.DEFAULT)
                val str = String(bytes, Charsets.UTF_8)
                if (str.isNotBlank()) return str
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun nameOr(u: Uri, fallback: String): String =
        u.getQueryParameter("remarks") ?: u.fragment?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: fallback

    private fun uriProfile(u: Uri, type: String): Profile {
        val p = Profile()
        p.type = type
        val host = u.host ?: return p
        p.server = host
        p.serverPort = if (u.port > 0) u.port else defaultPort(type)
        p.name = nameOr(u, host)
        // tls params
        val security = u.getQueryParameter("security")
        if (security == "tls" || security == "reality" || type == ProfileTypes.TROJAN) {
            p.tlsSecurity = security ?: "tls"
        }
        p.sni = u.getQueryParameter("sni") ?: u.getQueryParameter("peer") ?: ""
        p.allowInsecure = (u.getQueryParameter("allowInsecure") ?: u.getQueryParameter("insecure")
            ?: u.getQueryParameter("allow_insecure")) in listOf("1", "true")
        p.utlsFingerprint = u.getQueryParameter("fp") ?: "chrome"
        p.realityPublicKey = u.getQueryParameter("pbk") ?: ""
        p.realityShortId = u.getQueryParameter("sid") ?: ""
        p.alpn = u.getQueryParameter("alpn") ?: ""
        // transport params
        val net = u.getQueryParameter("type") ?: "tcp"
        p.transport = when (net) {
            "h2", "http" -> "http"
            else -> net
        }
        p.wsPath = u.getQueryParameter("path") ?: ""
        p.wsHost = u.getQueryParameter("host") ?: ""
        p.grpcServiceName = u.getQueryParameter("serviceName") ?: u.getQueryParameter("servicename") ?: ""
        if (p.transport == "tcp" && u.getQueryParameter("headerType") == "http") p.transport = "http"
        return p
    }

    private fun defaultPort(type: String): Int = when (type) {
        ProfileTypes.TUIC -> 443
        ProfileTypes.HYSTERIA2 -> 443
        else -> 443
    }

    // ---------- per-scheme ----------

    private fun parseVmess(link: String): Profile? {
        val body = link.removePrefix("vmess://")
        val json = b64(body) ?: return null
        return try {
            val o = JSONObject(json)
            val p = Profile()
            p.type = ProfileTypes.VMESS
            p.name = o.optString("ps", "").ifBlank { "vmess" }
            p.server = o.optString("add", "")
            p.serverPort = o.optString("port", "443").toIntOrNull() ?: 443
            p.uuid = o.optString("id", "")
            p.alterId = o.optString("aid", "0").toIntOrNull() ?: 0
            p.security = o.optString("scy", "").ifBlank { "auto" }
            val net = o.optString("net", "tcp")
            p.transport = when (net) {
                "h2" -> "http"
                else -> net
            }
            p.wsPath = o.optString("path", "")
            p.wsHost = o.optString("host", "")
            p.grpcServiceName = o.optString("path", "")
            p.httpHost = o.optString("host", "")
            if (o.optString("tls", "") == "tls") p.tlsSecurity = "tls"
            p.sni = o.optString("sni", "")
            p.utlsFingerprint = o.optString("fp", "chrome")
            p.alpn = o.optString("alpn", "")
            if (p.server.isBlank() || p.uuid.isBlank()) null else p
        } catch (_: Exception) {
            null
        }
    }

    private fun parseUri(link: String, type: String): Profile? {
        return try {
            val u = Uri.parse(link)
            val p = uriProfile(u, type)
            when (type) {
                ProfileTypes.VLESS -> {
                    p.uuid = u.userInfo ?: ""
                    p.flow = u.getQueryParameter("flow") ?: ""
                }
                ProfileTypes.TROJAN -> p.password = Uri.decode(u.userInfo ?: "")
                ProfileTypes.HYSTERIA2 -> {
                    p.password = Uri.decode(u.userInfo ?: "")
                    val obfs = u.getQueryParameter("obfs")
                    if (obfs == "salamander") {
                        val obfsPass = u.getQueryParameter("obfs-password") ?: ""
                        // stored in transport-free way: appended to password via builder is not possible;
                        // use wsHost? No: keep obfs inside password field is wrong.
                        // We store obfs password in pluginOpts (shared mechanism for extras).
                        p.plugin = "salamander"
                        p.pluginOpts = obfsPass
                    }
                }
                ProfileTypes.TUIC -> {
                    val info = u.userInfo ?: ""
                    val idx = info.indexOf(":")
                    if (idx > 0) {
                        p.uuid = Uri.decode(info.substring(0, idx))
                        p.password = Uri.decode(info.substring(idx + 1))
                    } else {
                        p.uuid = Uri.decode(info)
                    }
                    p.transport = "tcp"
                }
                ProfileTypes.SOCKS -> {
                    val info = u.userInfo
                    if (info != null && info.contains(":")) {
                        p.username = Uri.decode(info.substring(0, info.indexOf(":")))
                        p.password = Uri.decode(info.substring(info.indexOf(":") + 1))
                    } else if (info != null) {
                        p.username = Uri.decode(info)
                    }
                }
                ProfileTypes.HTTP -> {
                    val info = u.userInfo
                    if (info != null && info.contains("@")) {
                        val cred = info.substringBefore("@")
                        if (cred.contains(":")) {
                            p.username = Uri.decode(cred.substringBefore(":"))
                            p.password = Uri.decode(cred.substringAfter(":"))
                        }
                    }
                }
            }
            if (p.server.isBlank()) null else p
        } catch (_: Exception) {
            null
        }
    }

    private fun parseShadowsocks(link: String): Profile? {
        val body = link.removePrefix("ss://")
        return try {
            val p = Profile()
            p.type = ProfileTypes.SHADOWSOCKS
            val hashIdx = body.indexOf('#')
            val frag = if (hashIdx >= 0) java.net.URLDecoder.decode(body.substring(hashIdx + 1), "UTF-8") else ""
            val main = if (hashIdx >= 0) body.substring(0, hashIdx) else body
            val queryIdx = main.indexOf('?')
            val query = if (queryIdx >= 0) main.substring(queryIdx + 1) else ""
            val core = if (queryIdx >= 0) main.substring(0, queryIdx) else main

            if (core.contains("@")) {
                // SIP002: base64(method:pass)@host:port  (or plain method:pass)
                val atIdx = core.lastIndexOf('@')
                val userinfo = core.substring(0, atIdx)
                val hostpart = core.substring(atIdx + 1)
                val decoded = b64(userinfo)
                val cred = decoded ?: userinfo
                val colon = cred.indexOf(':')
                if (colon > 0) {
                    p.method = cred.substring(0, colon)
                    p.password = Uri.decode(cred.substring(colon + 1))
                }
                val (h, port) = parseHostPort(hostpart)
                p.server = h; p.serverPort = port
            } else {
                // legacy: base64(method:pass@host:port)
                val decoded = b64(core) ?: return null
                val atIdx = decoded.lastIndexOf('@')
                if (atIdx <= 0) return null
                val cred = decoded.substring(0, atIdx)
                val hostpart = decoded.substring(atIdx + 1)
                val colon = cred.indexOf(':')
                if (colon > 0) {
                    p.method = cred.substring(0, colon)
                    p.password = cred.substring(colon + 1)
                }
                val (h, port) = parseHostPort(hostpart)
                p.server = h; p.serverPort = port
            }

            // plugin param
            if (query.isNotBlank()) {
                val params = parseQuery(query)
                params["plugin"]?.let { pl ->
                    val parts = pl.split(";")
                    if (parts.isNotEmpty() && parts[0].startsWith("obfs")) {
                        p.plugin = "obfs-local"
                        p.pluginOpts = parts.joinToString(";")
                    }
                }
            }

            p.name = frag.ifBlank { p.server }
            if (p.server.isBlank() || p.method.isBlank()) null else p
        } catch (_: Exception) {
            null
        }
    }

    private fun parseWarp(link: String): Profile? {
        // warp://<private_key>@engage.cloudflareclient.com:2408?reserved=<b64>#name
        val body = link.removePrefix("warp://")
        return try {
            val p = Profile()
            p.type = ProfileTypes.WIREGUARD
            p.name = "WARP"
            val hashIdx = body.indexOf('#')
            val frag = if (hashIdx >= 0) java.net.URLDecoder.decode(body.substring(hashIdx + 1), "UTF-8") else ""
            val main = if (hashIdx >= 0) body.substring(0, hashIdx) else body
            val queryIdx = main.indexOf('?')
            val query = if (queryIdx >= 0) main.substring(queryIdx + 1) else ""
            val core = if (queryIdx >= 0) main.substring(0, queryIdx) else main

            if (core.contains("@")) {
                val atIdx = core.lastIndexOf('@')
                p.wgPrivateKey = core.substring(0, atIdx)
                val hostpart = core.substring(atIdx + 1)
                val (h, port) = parseHostPort(hostpart)
                p.server = h.ifBlank { Constants.WARP_HOST }
                p.serverPort = if (port > 0) port else Constants.WARP_PORT
            } else {
                return null // WARP requires a registered private key
            }
            val params = parseQuery(query)
            p.wgReserved = params["reserved"] ?: ""
            p.name = frag.ifBlank { "WARP" }
            if (p.wgPrivateKey.isBlank()) null else p
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSingboxJson(json: String): Profile? {
        return try {
            val o = JSONObject(json)
            val p = Profile()
            p.type = ProfileTypes.SINGBOX_JSON
            p.name = o.optString("tag", "").ifBlank { "sing-box" }
            if (o.has("outbounds") || o.has("type")) {
                p.rawSingboxJson = json
                p
            } else null
        } catch (_: Exception) {
            null
        }
    }

    // ---------- utils ----------

    private fun parseHostPort(s: String): Pair<String, Int> {
        return try {
            val u = Uri.parse("proxy://$s")
            val host = u.host ?: s.substringBeforeLast(":")
            val port = u.port
            Pair(host, if (port > 0) port else 443)
        } catch (_: Exception) {
            val lastColon = s.lastIndexOf(':')
            if (lastColon > 0) {
                Pair(s.substring(0, lastColon), s.substring(lastColon + 1).toIntOrNull() ?: 443)
            } else {
                Pair(s, 443)
            }
        }
    }

    private fun parseQuery(q: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        q.split("&").forEach { kv ->
            val idx = kv.indexOf('=')
            if (idx > 0) {
                val k = Uri.decode(kv.substring(0, idx))
                val v = Uri.decode(kv.substring(idx + 1))
                map[k] = v
            }
        }
        return map
    }
}
