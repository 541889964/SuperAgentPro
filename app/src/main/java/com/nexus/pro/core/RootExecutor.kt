
package com.nexus.pro.core
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

data class ShellResult(val output: String, val exitCode: Int)
object RootExecutor {
    private var currentProcess: Process? = null
    suspend fun executeInTermux(command: String, timeoutMs: Long = 120000): ShellResult = withContext(Dispatchers.IO) {
        val marker = "DONE>>>" + UUID.randomUUID() + ">>>"
        val termuxEnvCmd = listOf(
            "export PATH=/data/data/com.termux/files/usr/bin:\$PATH",
            "export LD_PRELOAD=/data/data/com.termux/files/usr/lib/libtermux-exec.so",
            "export HOME=/data/data/com.termux/files/home",
            "cd \$HOME",
            command,
            "echo " + marker + "\$?"
        ).joinToString("\n")
        
        val process = ProcessBuilder("su", "-c", termuxEnvCmd).redirectErrorStream(true).start()
        currentProcess = process
        val output = StringBuilder(); var exitCode = -1
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val job = launch { reader.forEachLine { line -> val idx = line.indexOf(marker); if (idx != -1) exitCode = line.substring(idx + marker.length).toIntOrNull() ?: -1 else output.appendLine(line) } }
        try { withTimeout(timeoutMs) { job.join() } } catch (e: TimeoutCancellationException) { job.cancel(); process.destroyForcibly(); return@withContext ShellResult(output.toString(), -1) }
        currentProcess = null; ShellResult(output.toString(), exitCode)
    }
    fun interrupt() { currentProcess?.destroyForcibly(); currentProcess = null }
}
