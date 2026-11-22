package com.kng7.sevenpro.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.kng7.sevenpro.R
import com.kng7.sevenpro.data.Profile
import com.kng7.sevenpro.data.ProfileStore
import com.kng7.sevenpro.data.ProfileTypes
import com.kng7.sevenpro.databinding.ActivityProfileEditorBinding
import com.kng7.sevenpro.parse.LinkParser

/**
 * Full profile editor. Shows only the fields relevant to the selected protocol.
 */
class ProfileEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileEditorBinding
    private var editing: Profile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val types = ProfileTypes.ALL
        binding.spType.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, typeLabels())
        )

        val editId = intent.getStringExtra("profile_id")
        if (editId != null) {
            ProfileStore.getProfile(editId)?.let {
                editing = it
                fillForm(it)
                supportActionBar?.title = getString(R.string.editor_title_edit)
            }
        } else {
            supportActionBar?.title = getString(R.string.editor_title_new)
        }

        binding.spType.setOnItemClickListener { _, _, _, _ -> updateVisibility() }
        binding.btnSave.setOnClickListener { save() }

        val initialType = editing?.type ?: ProfileTypes.VLESS
        binding.spType.setText(typeLabels()[types.indexOf(initialType).coerceAtLeast(0)], false)
        updateVisibility()
        setupChainSpinner()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun typeLabels(): List<String> = listOf(
        "VLESS", "VMess", "Trojan", "Shadowsocks", "Hysteria2", "TUIC",
        "WARP / WireGuard", "SOCKS5", "HTTP", "Tor (Orbot)", "sing-box JSON"
    )

    private fun currentType(): String {
        val idx = typeLabels().indexOf(binding.spType.text.toString())
        return ProfileTypes.ALL.getOrElse(idx) { ProfileTypes.VLESS }
    }

    private fun setupChainSpinner() {
        val others = ProfileStore.allProfiles()
            .filter { it.id != editing?.id }
            .map { it.name.ifBlank { it.summary() } to it.id }
        val options = listOf(getString(R.string.chain_none)) + others.map { it.first }
        binding.spChain.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, options))
        binding.spChain.setText(
            options[
                others.indexOfFirst { it.second == editing?.chainTag }.plus(1).coerceAtLeast(0)
            ], false
        )
    }

    private fun fillForm(p: Profile) {
        binding.etName.setText(p.name)
        binding.etServer.setText(p.server)
        binding.etPort.setText(p.serverPort.toString())
        binding.etUuid.setText(p.uuid)
        binding.etPassword.setText(p.password)
        binding.etUsername.setText(p.username)
        binding.etAid.setText(p.alterId.toString())

        val methods = listOf("aes-128-gcm", "aes-256-gcm", "chacha20-poly1305", "chacha20-ietf-poly1305", "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305", "none")
        binding.spMethod.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, methods))
        binding.spMethod.setText(methods.firstOrNull { it == p.method } ?: "aes-256-gcm", false)

        binding.etOrbot.setText(p.orbotPort.toString())

        val transports = listOf("tcp", "ws", "http", "grpc", "httpupgrade")
        binding.spTransport.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, transports))
        binding.spTransport.setText(transports.firstOrNull { it == p.transport } ?: "tcp", false)
        binding.etWsPath.setText(p.wsPath)
        binding.etWsHost.setText(p.wsHost)
        binding.etGrpc.setText(p.grpcServiceName)
        binding.etHttpHost.setText(p.httpHost)

        val secs = listOf("none", "tls", "reality")
        binding.spSecurity.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, secs))
        binding.spSecurity.setText(secs.firstOrNull { it == p.tlsSecurity } ?: "none", false)
        binding.etSni.setText(p.sni)
        binding.swInsecure.isChecked = p.allowInsecure
        val fps = listOf("chrome", "firefox", "safari", "ios", "android", "edge", "360", "qq", "random")
        binding.spFp.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, fps))
        binding.spFp.setText(fps.firstOrNull { it == p.utlsFingerprint } ?: "chrome", false)
        binding.etPbk.setText(p.realityPublicKey)
        binding.etSid.setText(p.realityShortId)
        binding.etAlpn.setText(p.alpn)
        val flows = listOf("", "xtls-rprx-vision")
        binding.spFlow.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, flows))
        binding.spFlow.setText(p.flow, false)
        binding.etObfs.setText(if (p.plugin == "salamander") p.pluginOpts else "")

        binding.etWgPriv.setText(p.wgPrivateKey)
        binding.etWgPeer.setText(p.wgPeerPublicKey)
        binding.etWgAddr.setText(p.wgAddress)
        binding.etWgReserved.setText(p.wgReserved)
        binding.etWgMtu.setText(p.wgMtu.toString())
        binding.etRawJson.setText(p.rawSingboxJson)
    }

    private fun updateVisibility() {
        val t = currentType()
        val json = t == ProfileTypes.SINGBOX_JSON
        val wg = t == ProfileTypes.WIREGUARD
        val proxyish = !json && !wg && t != ProfileTypes.TORSOCKS

        binding.rowJson.visibility = if (json) View.VISIBLE else View.GONE
        binding.rowServer.visibility = if (proxyish || wg) View.VISIBLE else View.GONE
        binding.rowUuid.visibility = if (t in listOf(ProfileTypes.VMESS, ProfileTypes.VLESS, ProfileTypes.TUIC)) View.VISIBLE else View.GONE
        binding.rowAid.visibility = if (t == ProfileTypes.VMESS) View.VISIBLE else View.GONE
        binding.rowPassword.visibility = if (t in listOf(ProfileTypes.TROJAN, ProfileTypes.SHADOWSOCKS, ProfileTypes.HYSTERIA2, ProfileTypes.TUIC, ProfileTypes.SOCKS, ProfileTypes.HTTP)) View.VISIBLE else View.GONE
        binding.rowMethod.visibility = if (t == ProfileTypes.SHADOWSOCKS) View.VISIBLE else View.GONE
        binding.rowUsername.visibility = if (t in listOf(ProfileTypes.SOCKS, ProfileTypes.HTTP)) View.VISIBLE else View.GONE
        binding.rowOrbot.visibility = if (t == ProfileTypes.TORSOCKS) View.VISIBLE else View.GONE

        val hasTransport = t in listOf(ProfileTypes.VMESS, ProfileTypes.VLESS, ProfileTypes.TROJAN)
        binding.rowTransport.visibility = if (hasTransport) View.VISIBLE else View.GONE
        binding.rowWsPath.visibility = if (hasTransport && currentTransportIn(listOf("ws", "http", "httpupgrade"))) View.VISIBLE else View.GONE
        binding.rowWsHost.visibility = if (hasTransport && currentTransportIn(listOf("ws", "httpupgrade"))) View.VISIBLE else View.GONE
        binding.rowGrpc.visibility = if (hasTransport && currentTransportIn(listOf("grpc"))) View.VISIBLE else View.GONE
        binding.rowHttpHost.visibility = if (hasTransport && currentTransportIn(listOf("http"))) View.VISIBLE else View.GONE

        val hasTls = t in listOf(ProfileTypes.VMESS, ProfileTypes.VLESS, ProfileTypes.TROJAN, ProfileTypes.HYSTERIA2, ProfileTypes.TUIC)
        binding.rowSecurity.visibility = if (hasTls && t != ProfileTypes.TROJAN) View.VISIBLE else View.GONE
        binding.rowTlsDetails.visibility = if (hasTls && binding.spSecurity.text.toString() != "none") View.VISIBLE else View.GONE
        binding.rowReality.visibility = if (hasTls && binding.spSecurity.text.toString() == "reality") View.VISIBLE else View.GONE

        binding.rowObfs.visibility = if (t == ProfileTypes.HYSTERIA2) View.VISIBLE else View.GONE
        binding.rowFlow.visibility = if (t == ProfileTypes.VLESS) View.VISIBLE else View.GONE

        binding.rowWg.visibility = if (wg) View.VISIBLE else View.GONE
        binding.rowChain.visibility = if (proxyish) View.VISIBLE else View.GONE

        binding.spTransport.setOnItemClickListener { _, _, _, _ ->
            binding.rowWsPath.visibility = if (hasTransport && currentTransportIn(listOf("ws", "http", "httpupgrade"))) View.VISIBLE else View.GONE
            binding.rowWsHost.visibility = if (hasTransport && currentTransportIn(listOf("ws", "httpupgrade"))) View.VISIBLE else View.GONE
            binding.rowGrpc.visibility = if (hasTransport && currentTransportIn(listOf("grpc"))) View.VISIBLE else View.GONE
            binding.rowHttpHost.visibility = if (hasTransport && currentTransportIn(listOf("http"))) View.VISIBLE else View.GONE
        }
        binding.spSecurity.setOnItemClickListener { _, _, _, _ ->
            val sec = binding.spSecurity.text.toString()
            binding.rowTlsDetails.visibility = if (hasTls && sec != "none") View.VISIBLE else View.GONE
            binding.rowReality.visibility = if (hasTls && sec == "reality") View.VISIBLE else View.GONE
        }
    }

    private fun currentTransportIn(list: List<String>): Boolean =
        binding.spTransport.text.toString() in list

    private fun save() {
        val t = currentType()
        val p = editing ?: Profile(id = "")
        p.type = t
        p.name = binding.etName.text?.toString()?.trim() ?: ""
        p.server = binding.etServer.text?.toString()?.trim() ?: ""
        p.serverPort = binding.etPort.text?.toString()?.trim()?.toIntOrNull() ?: 443

        if (t == ProfileTypes.SINGBOX_JSON) {
            val raw = binding.etRawJson.text?.toString()?.trim() ?: ""
            if (raw.isEmpty()) {
                binding.etRawJson.error = getString(R.string.err_required)
                return
            }
            // Validate JSON shape through the parser
            val parsed = LinkParser.parseOne(raw)
            if (parsed == null || parsed.type != ProfileTypes.SINGBOX_JSON) {
                binding.etRawJson.error = getString(R.string.err_invalid_json)
                return
            }
            p.rawSingboxJson = raw
            if (p.name.isBlank()) p.name = parsed.name
        } else if (t == ProfileTypes.TORSOCKS) {
            p.orbotPort = binding.etOrbot.text?.toString()?.trim()?.toIntOrNull() ?: 9050
        } else {
            if (p.server.isBlank()) {
                binding.etServer.error = getString(R.string.err_required)
                return
            }
            p.uuid = binding.etUuid.text?.toString()?.trim() ?: ""
            p.password = binding.etPassword.text?.toString() ?: ""
            p.username = binding.etUsername.text?.toString()?.trim() ?: ""
            p.alterId = binding.etAid.text?.toString()?.trim()?.toIntOrNull() ?: 0
            p.method = if (t == ProfileTypes.SHADOWSOCKS) binding.spMethod.text.toString() else ""
            p.transport = if (t in listOf(ProfileTypes.VMESS, ProfileTypes.VLESS, ProfileTypes.TROJAN))
                binding.spTransport.text.toString() else "tcp"
            p.wsPath = binding.etWsPath.text?.toString()?.trim() ?: ""
            p.wsHost = binding.etWsHost.text?.toString()?.trim() ?: ""
            p.grpcServiceName = binding.etGrpc.text?.toString()?.trim() ?: ""
            p.httpHost = binding.etHttpHost.text?.toString()?.trim() ?: ""
            p.tlsSecurity = when {
                t == ProfileTypes.TROJAN -> "tls"
                t in listOf(ProfileTypes.VMESS, ProfileTypes.VLESS, ProfileTypes.HYSTERIA2, ProfileTypes.TUIC) ->
                    binding.spSecurity.text.toString()
                else -> "none"
            }
            p.sni = binding.etSni.text?.toString()?.trim() ?: ""
            p.allowInsecure = binding.swInsecure.isChecked
            p.utlsFingerprint = binding.spFp.text.toString()
            p.realityPublicKey = binding.etPbk.text?.toString()?.trim() ?: ""
            p.realityShortId = binding.etSid.text?.toString()?.trim() ?: ""
            p.alpn = binding.etAlpn.text?.toString()?.trim() ?: ""
            p.flow = if (t == ProfileTypes.VLESS && t == ProfileTypes.VLESS) binding.spFlow.text.toString().trim() else ""
            val obfs = binding.etObfs.text?.toString()?.trim() ?: ""
            if (t == ProfileTypes.HYSTERIA2 && obfs.isNotBlank()) {
                p.plugin = "salamander"
                p.pluginOpts = obfs
            } else {
                p.plugin = ""
                p.pluginOpts = ""
            }
            if (t == ProfileTypes.WIREGUARD) {
                p.wgPrivateKey = binding.etWgPriv.text?.toString()?.trim() ?: ""
                p.wgPeerPublicKey = binding.etWgPeer.text?.toString()?.trim() ?: ""
                p.wgAddress = binding.etWgAddr.text?.toString()?.trim().takeUnless { it.isNullOrBlank() } ?: "172.16.0.2/32"
                p.wgReserved = binding.etWgReserved.text?.toString()?.trim() ?: ""
                p.wgMtu = binding.etWgMtu.text?.toString()?.trim()?.toIntOrNull() ?: 1280
                if (p.wgPrivateKey.isBlank()) {
                    binding.etWgPriv.error = getString(R.string.err_required)
                    return
                }
            }
            // chain
            val chainIdx = binding.spChain.text.toString()
            val others = ProfileStore.allProfiles().filter { it.id != editing?.id }
            if (chainIdx.isNotBlank() && chainIdx != getString(R.string.chain_none) && others.isNotEmpty()) {
                val match = others.firstOrNull { (it.name.ifBlank { it.summary() }) == chainIdx }
                p.chainTag = match?.id ?: ""
            } else {
                p.chainTag = ""
            }
        }

        if (editing == null) ProfileStore.addProfile(p) else ProfileStore.updateProfile(p)
        finish()
    }
}
