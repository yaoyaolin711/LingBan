package com.agent.chat.data.ai.tool.impl

import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat
import com.agent.chat.data.ai.tool.AgentTool
import com.agent.chat.data.ai.tool.ToolExecutionContext
import com.agent.chat.data.ai.tool.ToolResult
import com.agent.chat.data.ai.tool.objectSchema
import com.agent.chat.data.ai.tool.stringProp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Singleton
class MusicTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : AgentTool {
    override val name = "music_control"
    override val description = "查看当前播放的音乐信息，或控制播放/暂停/下一首/上一首。需要通知访问权限。"
    override val parametersSchema: Map<String, Any> = objectSchema(
        properties = mapOf(
            "action" to stringProp("动作：status（查看当前播放）、play、pause、next、previous"),
        ),
    )

    override suspend fun execute(argsJson: String, execContext: ToolExecutionContext): ToolResult =
        withContext(Dispatchers.IO) {
            val args = runCatching { JSONObject(argsJson.ifBlank { "{}" }) }.getOrDefault(JSONObject())
            val action = args.optString("action", "status")

            if (!hasNotificationAccess()) {
                return@withContext ToolResult(
                    false,
                    "需要通知访问权限才能感知和控制音乐。请在系统设置中为本应用开启通知访问。",
                )
            }

            val controller = getActiveController()

            when (action) {
                "status" -> {
                    if (controller == null) {
                        return@withContext ToolResult(true, "当前没有正在播放的音乐")
                    }
                    val metadata = controller.metadata
                    val state = controller.playbackState
                    val title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "未知"
                    val artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: "未知"
                    val album = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM)
                    val isPlaying = state?.state == android.media.session.PlaybackState.STATE_PLAYING

                    val data = JSONObject()
                        .put("title", title)
                        .put("artist", artist)
                        .put("isPlaying", isPlaying)
                    if (!album.isNullOrBlank()) data.put("album", album)

                    ToolResult(true, if (isPlaying) "正在播放：$artist - $title" else "已暂停：$artist - $title", data)
                }
                "play" -> {
                    if (controller != null) {
                        controller.transportControls.play()
                        ToolResult(true, "已继续播放")
                    } else {
                        sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                        ToolResult(true, "已发送播放指令")
                    }
                }
                "pause" -> {
                    if (controller != null) {
                        controller.transportControls.pause()
                        ToolResult(true, "已暂停")
                    } else {
                        sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
                        ToolResult(true, "已发送暂停指令")
                    }
                }
                "next" -> {
                    if (controller != null) {
                        controller.transportControls.skipToNext()
                    } else {
                        sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                    }
                    ToolResult(true, "已切换到下一首")
                }
                "previous" -> {
                    if (controller != null) {
                        controller.transportControls.skipToPrevious()
                    } else {
                        sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                    }
                    ToolResult(true, "已切换到上一首")
                }
                else -> ToolResult(false, "未知动作: $action。支持: status, play, pause, next, previous")
            }
        }

    private fun getActiveController(): MediaController? {
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            ?: return null
        val componentName = android.content.ComponentName(
            context,
            com.agent.chat.data.notification.AgentNotificationListenerService::class.java,
        )
        val controllers = runCatching {
            msm.getActiveSessions(componentName)
        }.getOrNull() ?: return null
        return controllers.firstOrNull { ctrl ->
            ctrl.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull()
    }

    private fun sendMediaKey(keyCode: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val downTime = SystemClock.uptimeMillis()
        val down = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0)
        val up = KeyEvent(downTime, downTime, KeyEvent.ACTION_UP, keyCode, 0)
        am.dispatchMediaKeyEvent(down)
        am.dispatchMediaKeyEvent(up)
    }

    private fun hasNotificationAccess(): Boolean {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabled.contains(context.packageName)
    }
}
