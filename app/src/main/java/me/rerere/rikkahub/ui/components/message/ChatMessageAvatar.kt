package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.isEmptyUIMessage
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalSettings

@Composable
fun ChatMessageUserAvatar(
    message: UIMessage,
    avatar: Avatar,
    nickname: String,
    modifier: Modifier = Modifier,
) {
    val settings = LocalSettings.current
    if (message.role == MessageRole.USER && !message.parts.isEmptyUIMessage() && settings.displaySetting.showUserAvatar) {
        UIAvatar(
            name = nickname,
            modifier = modifier.size(36.dp),
            value = avatar,
            loading = false,
        )
    }
}

@Composable
fun ChatMessageAssistantAvatar(
    message: UIMessage,
    loading: Boolean,
    model: Model?,
    assistant: Assistant?,
    modifier: Modifier = Modifier,
) {
    val settings = LocalSettings.current
    val showIcon = settings.displaySetting.showModelIcon
    if (!showIcon) return
    if (message.role != MessageRole.ASSISTANT) return
    if (message.parts.isEmptyUIMessage()) return

    val useAssistantAvatar = assistant?.useAssistantAvatar == true
    when {
        useAssistantAvatar && assistant != null -> {
            UIAvatar(
                name = assistant.name,
                modifier = modifier.size(36.dp),
                value = assistant.avatar,
                loading = loading,
            )
        }
        model != null -> {
            AutoAIIcon(
                name = model.modelId,
                modifier = modifier.size(36.dp),
                loading = loading,
            )
        }
    }
}
