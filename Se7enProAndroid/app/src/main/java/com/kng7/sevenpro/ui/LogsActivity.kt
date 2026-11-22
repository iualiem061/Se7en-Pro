package com.kng7.sevenpro.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.kng7.sevenpro.R
import com.kng7.sevenpro.databinding.ActivityLogsBinding
import com.kng7.sevenpro.engine.SingBoxProcess
import java.io.File

class LogsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogsBinding
    private val handler = Handler(Looper.getMainLooper())
    private val autoRefresh = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.logs_title)

        binding.btnRefresh.setOnClickListener { refresh() }
        binding.btnShare.setOnClickListener { share() }
    }

    override fun onResume() {
        super.onResume()
        handler.post(autoRefresh)
    }

    override fun onPause() {
        handler.removeCallbacks(autoRefresh)
        super.onPause()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refresh() {
        val sb = StringBuilder()
        sb.append("---- sing-box engine ----\n")
        SingBoxProcess.logs().takeLast(200).forEach { sb.append(it).append('\n') }
        val hev = File(cacheDir, "hev.log")
        if (hev.exists() && hev.length() > 0) {
            sb.append("\n---- hev tunnel ----\n")
            try {
                hev.readText().lines().takeLast(60).forEach { sb.append(it).append('\n') }
            } catch (_: Exception) {}
        }
        binding.tvLogs.text = sb.toString()
        binding.scroll.post { binding.scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun share() {
        try {
            val logFile = File(cacheDir, "se7en-logs.txt")
            val sb = StringBuilder()
            SingBoxProcess.logs().forEach { sb.append(it).append('\n') }
            logFile.writeText(sb.toString())
            val uri = FileProvider.getUriForFile(
                this, packageName + ".logs", logFile
            )
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(i, null))
        } catch (_: Exception) {
        }
    }
}
