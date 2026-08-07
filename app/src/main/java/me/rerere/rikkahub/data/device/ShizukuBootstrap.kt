package me.rerere.rikkahub.data.device

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

private const val TAG = "ShizukuBootstrap"

/**
 * 尽量「一键」拉起 Shizuku：
 * - 已在运行 → 请求对本 App 授权
 * - 有 Root → 执行 Shizuku 的 start.sh
 * - 已安装但无 Root → 打开 Shizuku（系统不允许第三方静默拉起 ADB 服务）
 * - 未安装 → 打开商店 / 官网下载页
 */
object ShizukuBootstrap {
    const val PACKAGE_NAME = "moe.shizuku.privileged.api"
    /** Play 版长期未更新，新机型常显示不适配；优先引导 GitHub 直链安装 */
    const val GITHUB_RELEASES_URL = "https://github.com/RikkaApps/Shizuku/releases/latest"
    const val GITHUB_APK_URL =
        "https://github.com/RikkaApps/Shizuku/releases/download/v13.6.0/shizuku-v13.6.0.r1086.2650830c-release.apk"
    private const val PERMISSION_REQUEST_CODE = 1001

    private val START_SCRIPT_CANDIDATES = listOf(
        "/sdcard/Android/data/$PACKAGE_NAME/start.sh",
        "/storage/emulated/0/Android/data/$PACKAGE_NAME/start.sh",
    )

    enum class Outcome {
        AlreadyReady,
        Started,
        NeedPermission,
        OpenedManager,
        NeedInstall,
        RootStartFailed,
    }

    fun isInstalled(context: Context): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
            true
        }.getOrDefault(false)
    }

    fun openManager(context: Context): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
            ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(launch)
            true
        }.getOrDefault(false)
    }

    fun openInstallPage(context: Context) {
        val apk = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_APK_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val releases = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val official = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(apk) }
            .recoverCatching { context.startActivity(releases) }
            .recoverCatching { context.startActivity(official) }
            .onFailure { Log.e(TAG, "openInstallPage failed", it) }
    }

    fun requestPermissionIfNeeded() {
        runCatching {
            if (DeviceShellExecutor.isShizukuAvailable() &&
                !DeviceShellExecutor.hasShizukuPermission()
            ) {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            }
        }
    }

    /**
     * 尝试一键就绪：优先 Root 静默启动，否则引导用户打开/安装 Shizuku。
     */
    suspend fun oneClickPrepare(context: Context): Outcome = withContext(Dispatchers.IO) {
        if (DeviceShellExecutor.isShizukuAvailable()) {
            return@withContext if (DeviceShellExecutor.hasShizukuPermission()) {
                Outcome.AlreadyReady
            } else {
                withContext(Dispatchers.Main) { requestPermissionIfNeeded() }
                Outcome.NeedPermission
            }
        }

        if (!isInstalled(context)) {
            withContext(Dispatchers.Main) { openInstallPage(context) }
            return@withContext Outcome.NeedInstall
        }

        if (tryStartViaRoot()) {
            if (waitUntilAvailable(timeoutMs = 8_000)) {
                withContext(Dispatchers.Main) { requestPermissionIfNeeded() }
                return@withContext if (DeviceShellExecutor.hasShizukuPermission()) {
                    Outcome.Started
                } else {
                    Outcome.NeedPermission
                }
            }
            withContext(Dispatchers.Main) { openManager(context) }
            return@withContext Outcome.RootStartFailed
        }

        withContext(Dispatchers.Main) { openManager(context) }
        Outcome.OpenedManager
    }

    private fun tryStartViaRoot(): Boolean {
        if (!canRunSu()) return false
        for (script in START_SCRIPT_CANDIDATES) {
            val started = runSuCommand("sh $script")
            if (started) {
                Log.i(TAG, "root start ok via $script")
                return true
            }
        }
        Log.w(TAG, "root start failed (start.sh missing or su denied)")
        return false
    }

    private fun canRunSu(): Boolean = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val finished = process.waitFor(3, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return false
        }
        process.exitValue() == 0
    }.getOrDefault(false)

    private fun runSuCommand(command: String): Boolean = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val finished = process.waitFor(15, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return false
        }
        process.exitValue() == 0
    }.getOrDefault(false)

    private suspend fun waitUntilAvailable(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (DeviceShellExecutor.isShizukuAvailable()) return true
            delay(250)
        }
        return DeviceShellExecutor.isShizukuAvailable()
    }
}
