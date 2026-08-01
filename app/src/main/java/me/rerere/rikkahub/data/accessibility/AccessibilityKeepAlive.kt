package me.rerere.rikkahub.data.accessibility

import android.Manifest
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import me.rerere.rikkahub.ACCESSIBILITY_GUARD_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.datastore.Settings as AppSettings
import me.rerere.rikkahub.utils.isSolaceAccessibilityEnabledInSystemSettings
import me.rerere.rikkahub.utils.openAccessibilitySettings

private const val TAG = "A11yKeepAlive"
private const val NOTIFICATION_ID = 4101

/**
 * Mitigations for OEM / battery managers that force-stop the app and thereby
 * strip Solace from [AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES].
 *
 * Android does **not** allow an app to keep that toggle permanently on by itself;
 * we can only reduce force-stops and promptly ask the user to re-enable.
 */
object AccessibilityKeepAlive {

    fun wantsPhoneControlAccessibility(settings: AppSettings): Boolean =
        settings.assistants.any { LocalToolOption.PhoneControl in it.localTools }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Opens the system dialog to allow unrestricted background battery use. */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (isIgnoringBatteryOptimizations(context)) return
        runCatching {
            context.startActivity(
                Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure {
            Log.w(TAG, "requestIgnoreBatteryOptimizations failed, fallback to settings", it)
            openAppBatterySettings(context)
        }
    }

    fun openAppBatterySettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure {
            openAppDetailsSettings(context)
        }
    }

    fun openAppDetailsSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure { Log.e(TAG, "openAppDetailsSettings failed", it) }
    }

    /**
     * Best-effort OEM auto-start / startup manager screens (Xiaomi / Huawei / Oppo / Vivo…).
     * Returns true if an activity was launched.
     */
    fun openOemAutostartSettings(context: Context): Boolean {
        val candidates = listOf(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.powercenter.PowerSettings",
            ),
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            ),
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
            ),
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            ),
            ComponentName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity",
            ),
            ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            ),
            ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
            ),
            ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity",
            ),
        )
        for (component in candidates) {
            val intent = Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                return runCatching {
                    context.startActivity(intent)
                    true
                }.getOrDefault(false)
            }
        }
        openAppDetailsSettings(context)
        return false
    }

    fun keepAliveGuideText(): String = buildString {
        appendLine("系统不允许 App 自己把无障碍开关「焊死」。")
        appendLine("很多手机（小米/华为/OPPO/vivo 等）在清理后台或省电时会强制停止 App，")
        appendLine("强制停止后系统会自动把无障碍权限关掉。")
        appendLine()
        appendLine("请按下面做完，才能尽量一直保持开启：")
        appendLine("1. 电池：对本 App 设为「无限制 / 不优化」")
        appendLine("2. 自启动 / 后台运行：允许本 App")
        appendLine("3. 最近任务：把本 App 上锁，避免被一键清理")
        appendLine("4. 不要在系统设置里对本 App 点「强制停止」")
        appendLine()
        appendLine("若仍被关掉，会发通知提醒你一键重新打开无障碍。")
    }

    /**
     * If the user enabled Phone Control but the system toggle is off, post a notification.
     */
    fun notifyIfAccessibilityStripped(context: Context, settings: AppSettings) {
        if (!wantsPhoneControlAccessibility(settings)) return
        if (context.isSolaceAccessibilityEnabledInSystemSettings()) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return
        }
        notifyReenable(context)
    }

    fun notifyReenable(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w(TAG, "skip a11y reenable notification: POST_NOTIFICATIONS denied")
                return
            }
        }
        val openA11y = PendingIntent.getActivity(
            context,
            0,
            Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val openApp = PendingIntent.getActivity(
            context,
            1,
            Intent(context, RouteActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, ACCESSIBILITY_GUARD_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle("无障碍权限被系统关闭")
            .setContentText("点此重新开启 Solace，否则无法操控手机界面")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "系统或省电清理关掉了 Solace 无障碍。请点通知重新开启，并设置电池「无限制」、允许自启动。"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(openA11y)
            .addAction(0, "打开无障碍设置", openA11y)
            .addAction(0, "打开 App", openApp)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }.onFailure { Log.e(TAG, "notifyReenable failed", it) }
    }

    fun openAccessibilitySettings(context: Context) {
        context.openAccessibilitySettings()
    }
}
