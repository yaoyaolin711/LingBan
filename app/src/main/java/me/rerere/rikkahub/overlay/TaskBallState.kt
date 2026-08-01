package me.rerere.rikkahub.overlay

import me.rerere.rikkahub.data.model.Avatar
import kotlin.uuid.Uuid

data class TaskBallState(
    val visible: Boolean = false,
    val expanded: Boolean = false,
    val conversationId: Uuid? = null,
    val assistantName: String = "Solace",
    val avatar: Avatar = Avatar.Dummy,
    val statusText: String = "任务进行中",
    val overlayPermissionNeeded: Boolean = false,
)
