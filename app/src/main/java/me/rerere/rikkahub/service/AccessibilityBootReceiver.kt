package me.rerere.rikkahub.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.accessibility.AccessibilityKeepAlive
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.needsCompanionForegroundService
import org.koin.core.context.GlobalContext

private const val TAG = "A11yBootReceiver"

/**
 * After reboot / package replace:
 * - remind if accessibility was stripped (Phone Control)
 * - restart companion monitor FGS when still enabled in settings
 */
class AccessibilityBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val pending = goAsync()
        Thread {
            try {
                val koin = GlobalContext.getOrNull() ?: return@Thread
                val settings = runBlocking {
                    koin.get<SettingsStore>().settingsFlowRaw.first()
                }
                AccessibilityKeepAlive.notifyIfAccessibilityStripped(context.applicationContext, settings)
                if (settings.needsCompanionForegroundService()) {
                    Log.i(TAG, "boot: restarting CompanionMonitorService")
                    CompanionMonitorService.start(context.applicationContext)
                }
            } catch (e: Exception) {
                Log.w(TAG, "boot companion/a11y check failed", e)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
