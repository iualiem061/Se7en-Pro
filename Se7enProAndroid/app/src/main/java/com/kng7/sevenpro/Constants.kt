package com.kng7.sevenpro

object Constants {
    const val LOG_TAG = "Se7enPro"

    /** Local SOCKS5 server exposed by the sing-box engine process. */
    const val SOCKS_HOST = "127.0.0.1"
    const val SOCKS_PORT_DEFAULT = 1080

    /** Virtual VPN interface addresses (matched inside the tunnel config). */
    const val VPN_ADDR_V4 = "198.18.0.1"
    const val VPN_DNS_V4 = "198.18.0.10"
    const val VPN_ADDR_V6 = "fc00::1"
    const val VPN_DNS_V6 = "fc00::10"
    const val VPN_MTU = 8500

    /** Actions understood by [com.kng7.sevenpro.service.SevenVpnService]. */
    const val ACTION_CONNECT = "com.kng7.sevenpro.CONNECT"
    const val ACTION_DISCONNECT = "com.kng7.sevenpro.DISCONNECT"

    /** Notification channel / id for the foreground VPN service. */
    const val CHANNEL_VPN = "vpn_channel"
    const val NOTIFICATION_ID = 1

    /** Cloudflare WARP defaults. */
    const val WARP_HOST = "engage.cloudflareclient.com"
    const val WARP_PORT = 2408
    const val WARP_PEER_PUBKEY = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="
    const val WARP_ADDRESS = "172.16.0.2/32"
    const val WARP_MTU = 1280

    /** sing-box engine binary, shipped in jniLibs as libsingbox.so (fetched by scripts/fetch-engine.sh). */
    const val ENGINE_LIB_NAME = "libsingbox.so"

    /** Orbot local SOCKS port used by the "Tor (via Orbot)" profile type. */
    const val ORBOT_SOCKS_PORT_DEFAULT = 9050
}
