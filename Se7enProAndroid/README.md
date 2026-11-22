# Pro Vpn (Android)

<div dir="rtl">

کلاینت ضدسانسور چندپروتکلی برای اندروید — نسخه موبایل پروژه [Se7en Pro](https://github.com/KNG7-P/Se7en-Pro) ویندوز.

موتور این اپ **sing-box** است و تمام ترافیک تونل از طریق **hev-socks5-tunnel** (که در VpnService اندروید اجرا می‌شود) عبور می‌کند.

</div>

Pro Vpn — the Android client (rebranded fork) of the [Se7en Pro](https://github.com/KNG7-P/Se7en-Pro) Windows anti-censorship suite.
Powered by a sing-box engine process + hev-socks5-tunnel bridge inside Android's VpnService.

## Features

- **Protocols**: VLESS (incl. Reality, Vision, uTLS), VMess, Trojan, Shadowsocks (incl. obfs plugin), Hysteria2, TUIC, Cloudflare **WARP** (WireGuard endpoint), SOCKS5, HTTP, **Tor via Orbot**, raw sing-box JSON
- **Multi-hop chaining**: route any outbound through another profile (e.g. VLESS over WARP) via sing-box `detour`
- **Import**: share links from any QR scanner / browser (vmess://, vless://, trojan://, ss://, hysteria2://, tuic://, warp://, socks://, http://), paste multiple links, base64 subscription URLs
- **Subscriptions**: add/edit/delete remote subscription lists, one-tap update, automatic dedupe and merge
- **Ping test** per profile (TCP latency)
- **Traffic stats**: live up/down speeds with an on-screen chart and session totals
- **Split tunneling**: per-app bypass list
- **Kill switch**: system "Always-on VPN + Block connections without VPN" guide
- **Quick tile**, **auto-connect on boot**, foreground notification with live speed and duration
- **UI**: Material 3, light/dark theme, **English / فارسی / Русский**, full RTL support

## Architecture

```
 apps ─▶ VpnService TUN (fd) ─▶ hev-socks5-tunnel (JNI .so) ─▶ socks5 127.0.0.1:port
                                                                     │
                                                       sing-box engine process (libsingbox.so)
                                                                     │
                                                          selected protocol outbound
```

- The sing-box binary ships as `jniLibs/<abi>/libsingbox.so` and runs directly from `nativeLibraryDir`.
- The app's own UID is excluded from the VPN (`addDisallowedApplication`) so engine/bridge sockets bypass the tunnel (no routing loops).
- DNS is hijacked inside sing-box (`sniff` + `hijack-dns` rule actions) and resolved through the proxy to prevent leaks.

## Building

Requirements: **Android Studio** (or Android SDK 34 + JDK 17), plus the sing-box engine binaries:

```bash
# 1. fetch official engine binaries (one time)
bash scripts/fetch-engine.sh

# 2. build
./gradlew assembleDebug        # or open in Android Studio and press Run
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

> The hev-socks5-tunnel AAR is bundled in `app/libs/` (official prebuilt, all ABIs).
> sing-box binaries are not committed to git — use the script above.

### Generating WARP credentials

The app does not register WARP accounts itself. Get a `warp://` link from any WARP key generator (e.g. wgcf) or fill the WireGuard fields manually (peer public key default: `bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=`).

### Tor support

Add a "Tor (Orbot)" profile while [Orbot](https://orbot.app) is running; traffic is chained through Orbot's local SOCKS port (9050).

## Third-party components

| Component | License |
|---|---|
| [sing-box](https://github.com/SagerNet/sing-box) engine | GPLv3 |
| [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) | MIT |

App source code: MIT, same as the Se7en Pro Windows project.
