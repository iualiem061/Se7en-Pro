package com.kng7.sevenpro.data

import com.google.gson.annotations.SerializedName

/** Supported profile (outbound) types. */
object ProfileTypes {
    const val VMESS = "vmess"
    const val VLESS = "vless"
    const val TROJAN = "trojan"
    const val SHADOWSOCKS = "shadowsocks"
    const val HYSTERIA2 = "hysteria2"
    const val TUIC = "tuic"
    const val WIREGUARD = "wireguard"
    const val SOCKS = "socks"
    const val HTTP = "http"
    const val TORSOCKS = "torsocks"   // local Orbot SOCKS -> Tor
    const val SINGBOX_JSON = "singbox-json" // raw sing-box outbound / full config

    val ALL = listOf(
        VLESS, VMESS, TROJAN, SHADOWSOCKS, HYSTERIA2, TUIC,
        WIREGUARD, SOCKS, HTTP, TORSOCKS, SINGBOX_JSON
    )
}

/** A single proxy / tunnel profile. Serialized to JSON by Gson. */
data class Profile(
    @SerializedName("id") var id: String = "",
    @SerializedName("name") var name: String = "",
    @SerializedName("type") var type: String = ProfileTypes.VLESS,
    @SerializedName("server") var server: String = "",
    @SerializedName("serverPort") var serverPort: Int = 443,

    // Common credentials
    @SerializedName("uuid") var uuid: String = "",
    @SerializedName("password") var password: String = "",
    @SerializedName("method") var method: String = "",          // shadowsocks
    @SerializedName("alterId") var alterId: Int = 0,            // vmess
    @SerializedName("security") var security: String = "auto",  // vmess encryption

    // Transport
    @SerializedName("transport") var transport: String = "tcp", // tcp|ws|http|grpc|httpupgrade
    @SerializedName("wsPath") var wsPath: String = "",
    @SerializedName("wsHost") var wsHost: String = "",
    @SerializedName("grpcServiceName") var grpcServiceName: String = "",
    @SerializedName("httpHost") var httpHost: String = "",

    // TLS
    @SerializedName("tlsSecurity") var tlsSecurity: String = "none", // none|tls|reality
    @SerializedName("sni") var sni: String = "",
    @SerializedName("allowInsecure") var allowInsecure: Boolean = false,
    @SerializedName("utlsFingerprint") var utlsFingerprint: String = "chrome",
    @SerializedName("realityPublicKey") var realityPublicKey: String = "",
    @SerializedName("realityShortId") var realityShortId: String = "",
    @SerializedName("alpn") var alpn: String = "",
    @SerializedName("flow") var flow: String = "",              // vless

    // Shadowsocks plugin
    @SerializedName("plugin") var plugin: String = "",
    @SerializedName("pluginOpts") var pluginOpts: String = "",

    // WireGuard / WARP
    @SerializedName("wgPrivateKey") var wgPrivateKey: String = "",
    @SerializedName("wgPeerPublicKey") var wgPeerPublicKey: String = "",
    @SerializedName("wgAddress") var wgAddress: String = "172.16.0.2/32",
    @SerializedName("wgReserved") var wgReserved: String = "",   // base64 of 3 bytes or "1,2,3"
    @SerializedName("wgMtu") var wgMtu: Int = 1280,

    // SOCKS / HTTP
    @SerializedName("username") var username: String = "",

    // Tor via Orbot
    @SerializedName("orbotPort") var orbotPort: Int = 9050,

    // Advanced
    @SerializedName("rawSingboxJson") var rawSingboxJson: String = "",
    @SerializedName("chainTag") var chainTag: String = "",       // detour: route this outbound through another profile
    @SerializedName("subscriptionId") var subscriptionId: String = "",

    // Runtime info
    @SerializedName("pingMs") var pingMs: Int = -1
) {
    fun signature(): String = "$type|$server|$serverPort|$uuid|$password|$method|$transport|$wsPath|$sni"

    fun summary(): String = "$server:$serverPort"
}

/** A remote subscription source. */
data class Subscription(
    @SerializedName("id") var id: String = "",
    @SerializedName("name") var name: String = "",
    @SerializedName("url") var url: String = "",
    @SerializedName("lastUpdate") var lastUpdate: Long = 0,
    @SerializedName("profileCount") var profileCount: Int = 0
)
