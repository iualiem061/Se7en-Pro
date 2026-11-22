# hev-socks5-tunnel JNI bridge
-keep class hev.htproxy.** { *; }

# Gson data models are built via reflection-free Gson
-keep class com.kng7.sevenpro.data.** { *; }

# Coroutines debug metadata removal (optional safety)
-dontwarn kotlinx.coroutines.debug.**
