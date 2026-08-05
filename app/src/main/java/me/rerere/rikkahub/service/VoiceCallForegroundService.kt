package me.rerere.rikkahub.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.VOICE_CALL_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.overlay.VoiceCallFloatHost
import me.rerere.rikkahub.ui.pages.voicecall.VoiceCallCoordinator
import org.koin.android.ext.android.inject
import kotlin.uuid.Uuid

private const val TAG = "VoiceCallFg"

/**
 * Lightweight foreground keep-alive while a voice call (possibly minimized) is active.
 */
class VoiceCallForegroundService : Service() {

    companion object {
        const val ACTION_START = "me.rerere.rikkahub.action.VOICE_CALL_START"
        const val ACTION_STOP = "me.rerere.rikkahub.action.VOICE_CALL_STOP"
        const val ACTION_HANGUP = "me.rerere.rikkahub.action.VOICE_CALL_HANGUP"
        const val EXTRA_CONVERSATION_ID = "conversationId"
        const val NOTIFICATION_ID = 3101

        fun start(context: Context, conversationId: Uuid) {
            val intent = Intent(context, VoiceCallForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONVERSATION_ID, conversationId.toString())
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, VoiceCallForegroundService::class.java))
            }.onFailure {
                Log.w(TAG, "stopService failed", it)
                runCatching {
                    context.startService(
                        Intent(context, VoiceCallForegroundService::class.java).apply {
                            action = ACTION_STOP
                        }
                    )
                }
            }
        }
    }

    private val coordinator: VoiceCallCoordinator by inject()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_HANGUP -> {
                coordinator.hangUp()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val conversationId = intent?.getStringExtra(EXTRA_CONVERSATION_ID)
                promoteForeground(conversationId)
            }
        }
        return START_STICKY
    }

    private fun promoteForeground(conversationId: String?) {
        val notification = buildNotification(conversationId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(conversationId: String?) =
        NotificationCompat.Builder(this, VOICE_CALL_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle("语音通话进行中")
            .setContentText("点击返回通话，或从通知挂断")
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, RouteActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        if (!conversationId.isNullOrBlank()) {
                            putExtra(VoiceCallFloatHost.EXTRA_VOICE_CALL_CONVERSATION_ID, conversationId)
                        }
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .addAction(
                0,
                "挂断",
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, VoiceCallForegroundService::class.java).apply { action = ACTION_HANGUP },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
}
