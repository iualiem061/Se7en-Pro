package com.kng7.sevenpro.engine

import com.kng7.sevenpro.Constants
import com.kng7.sevenpro.data.Profile
import com.kng7.sevenpro.data.ProfileStore
import com.kng7.sevenpro.data.ProfileTypes
import com.kng7.sevenpro.data.SettingsStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds a sing-box 1.14 compatible JSON configuration from a [Profile].
 *
 * Architecture:
 *   apps -> VpnService TUN -> hev-socks5-tunnel -> socks5 (this config) -> selected outbound
 *
 * WireGuard profiles (WARP) are emitted as sing-box "endpoints" (the legacy
 * wireguard outbound was removed in sing-box 1.13).
 */
object SingBoxConfigBuilder {

    fun build(profile: Profile): JSONObject {
        val port = SettingsStore.socksPort
        val root = JSONObject()
        root.put("log", JSONObject().put("level", "info").put("timestamp", true))

        val outbounds = JSONArray()
        val endpoints = JSONArray()

        var proxyTag = ""
        var chainTagUsed = ""

        // Chain (multi-hop) parent: route the selected outbound through another profile
        if (profile.chainTag.isNotBlank()) {
            val chain = ProfileStore.getProfile(profile.chainTag)
            if (chain != null && chain.id != profile.id) {
                chainTagUsed = "chain-" + chain.id
                addOutbound(chain, chainTagUsed, outbounds, endpoints, detour = "")
            }
        }

        when (profile.type) {
            ProfileTypes.SINGBOX_JSON -> {
                mergeRawConfig(profile, root, outbounds, endpoints)
                applyCommon(root, outbounds, endpoints, port)
                return root
            }
            else -> {
                proxyTag = "proxy"
                addOutbound(profile, proxyTag, outbounds, endpoints, detour = chainTagUsed)
            }
        }

        outbounds.put(JSONObject().put("type", "direct").put("tag", "direct"))
        applyCommon(root, outbounds, endpoints, port)

        val route = root.getJSONObject("route")
        route.put("final", proxyTag)

        // Route DNS queries through the proxy (works for both outbounds and endpoints)
        try {
            val dns = root.getJSONObject("dns")
            val servers = dns.getJSONArray("servers")
            for (i in 0 until servers.length()) {
                val s = servers.getJSONObject(i)
                if (s.optString("tag") == dns.optString("final", "dns-proxy") && !s.has("detour")) {
                    s.put("detour", proxyTag)
                }
            }
        } catch (_: Exception) {}
        return root
    }

    /** Adds dns/inbounds/route skeleton if the raw config did not provide one. */
    private fun applyCommon(root: JSONObject, outbounds: JSONArray, endpoints: JSONArray, port: Int) {
        // DNS
        if (!root.has("dns")) {
            val dns = JSONObject()
            val servers = JSONArray()
            servers.put(
                JSONObject()
                    .put("type", "udp")
                    .put("tag", "dns-proxy")
                    .put("server", "1.1.1.1")
            )
            dns.put("servers", servers)
            dns.put("final", "dns-proxy")
            root.put("dns", dns)
        }

        // Inbounds: replace with our local socks5 server
        val inbounds = JSONArray()
        inbounds.put(
            JSONObject()
                .put("type", "socks")
                .put("tag", "socks-in")
                .put("listen", Constants.SOCKS_HOST)
                .put("listen_port", port)
        )
        root.put("inbounds", inbounds)

        if (root.has("endpoints")) {
            val arr = root.getJSONArray("endpoints")
            for (i in 0 until arr.length()) endpoints.put(arr.getJSONObject(i))
        }
        if (endpoints.length() > 0) root.put("endpoints", endpoints)
        root.put("outbounds", outbounds)

        // Route
        val route = if (root.has("route")) root.getJSONObject("route") else JSONObject()
        val rules = JSONArray()
        rules.put(JSONObject().put("action", "sniff"))
        rules.put(JSONObject().put("protocol", "dns").put("action", "hijack-dns"))
        rules.put(
            JSONObject()
                .put("ip_is_private", true)
                .put("action", "route")
                .put("outbound", "direct")
        )
        route.put("rules", rules)
        if (!route.has("final")) {
            val obs = root.getJSONArray("outbounds")
            var first = ""
            for (i in 0 until obs.length()) {
                val t = obs.getJSONObject(i).optString("tag", "")
                if (t.isNotEmpty() && t != "direct") { first = t; break }
            }
            route.put("final", first)
        }
        root.put("route", route)

        // Route DNS queries through the proxy outbound (anti DNS-leak)
        try {
            val dns = root.getJSONObject("dns")
            val dnsFinal = dns.optString("final", "")
            val servers = dns.optJSONArray("servers") ?: JSONArray()
            for (i in 0 until servers.length()) {
                val s = servers.getJSONObject(i)
                if (s.optString("tag", "") == dnsFinal && !s.has("detour")) {
                    val proxyTags = root.getJSONArray("outbounds")
                    var pt = ""
                    for (j in 0 until proxyTags.length()) {
                        val tag = proxyTags.getJSONObject(j).optString("tag", "")
                        if (tag != "direct" && tag != "dns-out" && tag.isNotEmpty()) { pt = tag; break }
                    }
                    if (pt.isNotEmpty()) s.put("detour", pt)
                }
            }
        } catch (_: Exception) {
        }
    }

    /** Builds the outbound (or endpoint) JSON object for a profile. */
    private fun addOutbound(
        p: Profile,
        tag: String,
        outbounds: JSONArray,
        endpoints: JSONArray,
        detour: String
    ) {
        val o = JSONObject()
        when (p.type) {
            ProfileTypes.WIREGUARD -> {
                o.put("type", "wireguard")
                o.put("tag", tag)
                val addr = JSONArray()
                p.wgAddress.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { addr.put(it) }
                if (addr.length() == 0) addr.put(Constants.WARP_ADDRESS)
                o.put("address", addr)
                o.put("private_key", p.wgPrivateKey)
                o.put("mtu", if (p.wgMtu > 0) p.wgMtu else Constants.WARP_MTU)
                val peers = JSONArray()
                val peer = JSONObject()
                peer.put("address", p.server.ifBlank { Constants.WARP_HOST })
                peer.put("port", if (p.serverPort > 0) p.serverPort else Constants.WARP_PORT)
                peer.put("public_key", p.wgPeerPublicKey.ifBlank { Constants.WARP_PEER_PUBKEY })
                val allowed = JSONArray()
                allowed.put("0.0.0.0/0")
                allowed.put("::/0")
                peer.put("allowed_ips", allowed)
                val reserved = parseReserved(p.wgReserved)
                if (reserved != null) peer.put("reserved", reserved)
                peers.put(peer)
                o.put("peers", peers)
                if (detour.isNotBlank()) o.put("detour", detour)
                endpoints.put(o)
                return
            }

            ProfileTypes.VMESS -> {
                o.put("type", "vmess")
                o.put("server", p.server)
                o.put("server_port", p.serverPort)
                o.put("uuid", p.uuid)
                o.put("security", p.security.ifBlank { "auto" })
                o.put("alter_id", p.alterId)
            }

            ProfileTypes.VLESS -> {
                o.put("type", "vless")
                o.put("server", p.server)
                o.put("server_port", p.serverPort)
                o.put("uuid", p.uuid)
                if (p.flow.isNotBlank()) o.put("flow", p.flow)
            }

            ProfileTypes.TROJAN -> {
                o.put("type", "trojan")
                o.put("server", p.server)
                o.put("server_port", p.serverPort)
                o.put("password", p.password)
            }

            ProfileTypes.SHADOWSOCKS -> {
                o.put("type", "shadowsocks")
                o.put("server", p.server)
                o.put("server_port", p.serverPort)
                o.put("method", p.method)
                o.put("password", p.password)
                if (p.plugin.isNotBlank()) {
                    o.put("plugin", p.plugin)
                    if (p.pluginOpts.isNotBlank()) o.put("plugin_opts", p.pluginOpts)
                }
            }

            ProfileTypes.HYSTERIA2 -> {
                o.put("type", "hysteria2")
                o.put("server", p.server)
                o.put("server_port", p.serverPort)
                if (p.password.isNotBlank()) o.put("password", p.password)
            }

            ProfileTypes.TUIC -> {
                o.put("type", "tuic")
                o.put("server", p.server)
                o.put("server_port", p.serverPort)
                o.put("uuid", p.uuid)
                if (p.password.isNotBlank()) o.put("password", p.password)
                o.put("congestion_control", "bbr")
                o.put("udp_relay_mode", "native")
            }

            ProfileTypes.SOCKS -> {
                o.put("type", "socks")
                o.put("version", "5")
                o.put("server", p.server)
                o.put("server_port", p.serverPort)
                if (p.username.isNotBlank()) {
                    o.put("username", p.username)
                    o.put("password", p.password)
                }
            }

            ProfileTypes.HTTP -> {
                o.put("type", "http")
                o.put("server", p.server)
                o.put("server_port", p.serverPort)
                if (p.username.isNotBlank()) {
                    o.put("username", p.username)
                    o.put("password", p.password)
                }
            }

            ProfileTypes.TORSOCKS -> {
                // Local Tor daemon (Orbot) SOCKS port
                o.put("type", "socks")
                o.put("version", "5")
                o.put("server", Constants.SOCKS_HOST)
                o.put("server_port", p.orbotPort)
            }

            else -> return
        }

        o.put("tag", tag)

        // TLS (trojan always uses TLS)
        val sec = if (p.type == ProfileTypes.TROJAN && p.tlsSecurity == "none") "tls" else p.tlsSecurity
        if (sec == "tls" || sec == "reality") {
            val tls = JSONObject()
            tls.put("enabled", true)
            tls.put("server_name", p.sni.ifBlank { p.server })
            tls.put("insecure", p.allowInsecure)
            val alpn = JSONArray()
            p.alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { alpn.put(it) }
            if (alpn.length() > 0) tls.put("alpn", alpn)
            if (p.utlsFingerprint.isNotBlank()) {
                tls.put(
                    "utls",
                    JSONObject()
                        .put("enabled", true)
                        .put("fingerprint", p.utlsFingerprint)
                )
            }
            if (sec == "reality") {
                tls.put(
                    "reality",
                    JSONObject()
                        .put("enabled", true)
                        .put("public_key", p.realityPublicKey)
                        .put("short_id", p.realityShortId)
                )
            }
            o.put("tls", tls)
        }

        // Transport
        val t = when (p.transport) {
            "ws" -> JSONObject()
                .put("type", "ws")
                .put("path", p.wsPath.ifBlank { "/" })
                .put(
                    "headers",
                    if (p.wsHost.isNotBlank())
                        JSONObject().put("Host", JSONArray().put(p.wsHost))
                    else JSONObject()
                )
            "http" -> {
                val http = JSONObject().put("type", "http").put("path", p.wsPath)
                val hosts = JSONArray()
                p.httpHost.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { hosts.put(it) }
                if (hosts.length() > 0) http.put("host", hosts)
                http
            }
            "grpc" -> JSONObject()
                .put("type", "grpc")
                .put("service_name", p.grpcServiceName)
            "httpupgrade" -> JSONObject()
                .put("type", "httpupgrade")
                .put("path", p.wsPath.ifBlank { "/" })
                .put("host", p.wsHost)
            else -> null
        }
        if (t != null) o.put("transport", t)

        if (detour.isNotBlank()) o.put("detour", detour)
        outbounds.put(o)
    }

    /**
     * Raw sing-box JSON profile: accepts either a full config (has "outbounds")
     * or a single outbound object (has "type").
     */
    private fun mergeRawConfig(
        p: Profile,
        root: JSONObject,
        outbounds: JSONArray,
        endpoints: JSONArray
    ) {
        val raw = p.rawSingboxJson.trim()
        val parsed = try { JSONObject(raw) } catch (_: Exception) { null }
        if (parsed == null) throw IllegalArgumentException("Invalid sing-box JSON")
        if (parsed.has("outbounds")) {
            // Full config: take outbounds/dns as-is, drop user inbounds
            val obs = parsed.getJSONArray("outbounds")
            for (i in 0 until obs.length()) {
                val item = obs.getJSONObject(i)
                if (item.optString("type") == "wireguard") endpoints.put(item) else outbounds.put(item)
            }
            if (parsed.has("dns")) root.put("dns", parsed.getJSONObject("dns"))
        } else if (parsed.has("type")) {
            if (parsed.optString("type") == "wireguard") endpoints.put(parsed) else outbounds.put(parsed)
        } else {
            throw IllegalArgumentException("JSON must contain outbounds[] or be an outbound object")
        }
    }

    private fun parseReserved(v: String): JSONArray? {
        val s = v.trim()
        if (s.isEmpty()) return null
        return try {
            if (s.contains(",")) {
                val arr = JSONArray()
                s.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { arr.put(it.toInt()) }
                arr
            } else {
                val decoded = android.util.Base64.decode(s, android.util.Base64.DEFAULT or android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)
                val arr = JSONArray()
                decoded.take(3).forEach { arr.put(it.toInt() and 0xFF) }
                arr
            }
        } catch (_: Exception) {
            null
        }
    }

    /** hev-socks5-tunnel YAML config (passed to TProxyStartService with the tun fd). */
    fun buildHevYaml(port: Int, icmpReply: Boolean, logPath: String?): String {
        val sb = StringBuilder()
        sb.append("tunnel:\n")
        sb.append("  mtu: ${Constants.VPN_MTU}\n")
        sb.append("  icmp: '").append(if (icmpReply) "reply" else "off").append("'\n")
        sb.append("socks5:\n")
        sb.append("  port: $port\n")
        sb.append("  address: '${Constants.SOCKS_HOST}'\n")
        sb.append("  udp: 'udp'\n")
        sb.append("misc:\n")
        sb.append("  log-level: warn\n")
        if (logPath != null) sb.append("  log-file: '$logPath'\n")
        return sb.toString()
    }
}
