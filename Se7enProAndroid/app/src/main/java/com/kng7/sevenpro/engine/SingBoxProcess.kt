package com.kng7.sevenpro.engine

import android.content.Context
import com.kng7.sevenpro.Constants
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages the sing-box engine process.
 * The binary ships as jniLibs/<abi>/libsingbox.so and is executable in place
 * (nativeLibraryDir contents are executable on Android).
 */
object SingBoxProcess {

    private val processRef = AtomicReference<Process?>(null)
    private val logBuffer = ArrayDeque<String>()
    private const val LOG_CAP = 400

    fun binaryPath(context: Context): String =
        context.applicationInfo.nativeLibraryDir + File.separator + Constants.ENGINE_LIB_NAME

    fun engineVersion(context: Context): String = try {
        val p = ProcessBuilder(binaryPath(context), "version")
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim().lines().firstOrNull() ?: ""
        p.waitFor()
        out
    } catch (_: Exception) {
        ""
    }

    @Synchronized
    fun start(context: Context, configPath: String): Boolean {
        stop()
        val logFile = File(context.filesDir, "singbox.log")
        try {
            val proc = ProcessBuilder(
                binaryPath(context), "run", "-c", configPath, "-D", context.cacheDir.absolutePath
            ).redirectErrorStream(true).start()

            processRef.set(proc)

            // Drain stdout/stderr into ring buffer + log file
            Thread {
                try {
                    proc.inputStream.bufferedReader().use { reader ->
                        logFile.bufferedWriter().use { writer ->
                            while (true) {
                                val line = reader.readLine() ?: break
                                synchronized(logBuffer) {
                                    if (logBuffer.size >= LOG_CAP) logBuffer.removeFirst()
                                    logBuffer.addLast(line)
                                }
                                try { writer.write(line); writer.newLine(); writer.flush() } catch (_: Exception) {}
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }.start()

            return true
        } catch (e: Exception) {
            appendLog("failed to start engine: ${e.message}")
            return false
        }
    }

    /** Waits until the local socks port accepts connections. */
    fun waitReady(port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isRunning() && portOpen(port)) return true
            if (!isRunning()) return false
            try { Thread.sleep(250) } catch (_: InterruptedException) { return false }
        }
        return portOpen(port)
    }

    fun portOpen(port: Int): Boolean = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(Constants.SOCKS_HOST, port), 400)
            true
        }
    } catch (_: Exception) {
        false
    }

    fun isRunning(): Boolean {
        val p = processRef.get() ?: return false
        return p.isAlive
    }

    @Synchronized
    fun stop() {
        val p = processRef.getAndSet(null) ?: return
        try {
            p.destroy()
            p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
            if (p.isAlive) p.destroyForcibly()
        } catch (_: Exception) {
        }
        processRef.set(null)
    }

    private fun appendLog(line: String) {
        synchronized(logBuffer) {
            if (logBuffer.size >= LOG_CAP) logBuffer.removeFirst()
            logBuffer.addLast(line)
        }
    }

    fun logs(): List<String> = synchronized(logBuffer) { logBuffer.toList() }

    fun clearLogs() = synchronized(logBuffer) { logBuffer.clear() }
}
