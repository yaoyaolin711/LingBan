package me.rerere.rikkahub.data.device

import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

private const val TAG = "DeviceShell"

/**
 * 高级设备 shell: 仅通过 Shizuku 提权执行白名单命令.
 * 未安装/未授权 Shizuku 时返回明确错误, 不回退到普通 Runtime.exec
 * (普通 App UID 无法 force-stop / input 等).
 */
object DeviceShellExecutor {

    private val ALLOWED_PREFIXES = listOf(
        "am start ",
        "am force-stop ",
        "am broadcast ",
        "input keyevent ",
        "input tap ",
        "input swipe ",
        "input text ",
        "cmd statusbar ",
        "dumpsys window ",
        "dumpsys activity activities",
        "pm list packages",
        "settings get ",
    )

    data class Result(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val error: String? = null,
    )

    fun isShizukuAvailable(): Boolean = runCatching {
        Shizuku.pingBinder()
    }.getOrDefault(false)

    fun hasShizukuPermission(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun isCommandAllowed(command: String): Boolean {
        val trimmed = command.trim()
        if (trimmed.isEmpty() || trimmed.contains('\n') || trimmed.contains(';') ||
            trimmed.contains('|') || trimmed.contains('&') || trimmed.contains('`') ||
            trimmed.contains('$')
        ) {
            return false
        }
        return ALLOWED_PREFIXES.any { trimmed.startsWith(it) || trimmed == it.trimEnd() }
    }

    fun execute(command: String, timeoutSeconds: Long = 15): Result {
        if (!isCommandAllowed(command)) {
            return Result(
                exitCode = -1,
                stdout = "",
                stderr = "",
                error = "Command not in whitelist. Allowed prefixes: ${ALLOWED_PREFIXES.joinToString()}",
            )
        }
        if (!isShizukuAvailable()) {
            return Result(
                exitCode = -1,
                stdout = "",
                stderr = "",
                error = "Shizuku is not running. Install and start Shizuku, then grant permission to Solace.",
            )
        }
        if (!hasShizukuPermission()) {
            return Result(
                exitCode = -1,
                stdout = "",
                stderr = "",
                error = "Shizuku permission not granted. Open Shizuku and authorize Solace.",
            )
        }

        return runCatching {
            val process = newShizukuProcess(arrayOf("sh", "-c", command.trim()))
                ?: return Result(-1, "", "", "Failed to create Shizuku process (newProcess unavailable).")
            val stdout = StringBuilder()
            val stderr = StringBuilder()
            val outThread = Thread {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        if (stdout.length < 32_768) stdout.appendLine(line)
                    }
                }
            }
            val errThread = Thread {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        if (stderr.length < 16_384) stderr.appendLine(line)
                    }
                }
            }
            outThread.start()
            errThread.start()
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                outThread.join(1000)
                errThread.join(1000)
                return@runCatching Result(-1, stdout.toString(), stderr.toString(), "Timed out")
            }
            outThread.join(1000)
            errThread.join(1000)
            Result(process.exitValue(), stdout.toString().trimEnd(), stderr.toString().trimEnd())
        }.getOrElse {
            Log.e(TAG, "execute failed", it)
            Result(-1, "", "", it.message ?: "Unknown error")
        }
    }

    /**
     * Shizuku 13 起 newProcess 非公开 API, 通过反射调用.
     */
    private fun newShizukuProcess(cmd: Array<String>): Process? {
        return runCatching {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
            method.isAccessible = true
            method.invoke(null, cmd, null, null) as Process
        }.onFailure {
            Log.e(TAG, "Shizuku.newProcess reflection failed", it)
        }.getOrNull()
    }
}
