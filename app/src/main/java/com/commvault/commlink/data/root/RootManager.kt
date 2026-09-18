package com.commvault.commlink.data.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader

data class RootCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}

object RootManager {

    private val allowlist = setOf(
        "id",
        "ls /config/usb_gadget",
        "ls /sys/class/udc",
        "ls -1 /sys/class/udc",
        "cat /sys/class/udc/*/function",
        "grep -q 'g_audio' /proc/modules",
        "zcat /proc/config.gz",
        "setprop sys.usb.config",
        "getprop sys.usb.config",
        "getprop sys.usb.state",
        // ConfigFS gadgets creation allowlist
        "mkdir", "echo", "ln", "rm", "rmdir"
    )

    private fun isCommandAllowed(command: String): Boolean {
        // Simple allowlist check based on command prefixes
        return allowlist.any { command.startsWith(it) } || 
               command.contains("/config/usb_gadget/") ||
               command.contains("/sys/kernel/config/usb_gadget/")
    }

    suspend fun checkRootAvailability(): Boolean = withContext(Dispatchers.IO) {
        val result = executeCommand("id", timeoutMs = 2000L)
        result != null && result.isSuccess && result.stdout.contains("uid=0(root)")
    }

    suspend fun executeCommand(command: String, timeoutMs: Long = 5000L): RootCommandResult? = withContext(Dispatchers.IO) {
        if (!isCommandAllowed(command)) {
            Log.e("RootManager", "Command not in allowlist: $command")
            return@withContext RootCommandResult(-1, "", "Command restricted by allowlist")
        }

        withTimeoutOrNull(timeoutMs) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                
                val stdoutBuilder = StringBuilder()
                val stderrBuilder = StringBuilder()
                
                val stdOutReader = BufferedReader(InputStreamReader(process.inputStream))
                val stdErrReader = BufferedReader(InputStreamReader(process.errorStream))
                
                var line: String?
                while (stdOutReader.readLine().also { line = it } != null) {
                    stdoutBuilder.append(line).append("\n")
                }
                while (stdErrReader.readLine().also { line = it } != null) {
                    stderrBuilder.append(line).append("\n")
                }
                
                val exitCode = process.waitFor()
                
                RootCommandResult(
                    exitCode = exitCode,
                    stdout = stdoutBuilder.toString().trim(),
                    stderr = stderrBuilder.toString().trim()
                )
            } catch (e: Exception) {
                Log.e("RootManager", "Root execution failed: ${e.message}", e)
                RootCommandResult(-1, "", e.message ?: "Unknown error")
            }
        } ?: RootCommandResult(-1, "", "Command timed out after $timeoutMs ms")
    }
}
