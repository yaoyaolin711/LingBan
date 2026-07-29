package com.agent.chat.data.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AgentNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var historyStore: NotificationHistoryStore

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        val n = sbn.notification ?: return

        val extras = n.extras
        val title = (extras.getCharSequence(Notification.EXTRA_TITLE) ?: "").toString()
        if (title.isBlank()) return

        val rawText = (extras.getCharSequence(Notification.EXTRA_TEXT) ?: "").toString()
        val textPreview = rawText.takeIf { it.isNotBlank() }?.take(200)

        val item = NotificationHistoryItem(
            packageName = pkg,
            appName = historyStore.resolveAppName(pkg),
            title = title.take(120),
            textPreview = textPreview,
            postedAt = System.currentTimeMillis(),
        )
        historyStore.add(item)
    }
}

