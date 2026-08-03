package me.rerere.rikkahub.overlay.pet

import me.rerere.rikkahub.data.companion.model.CompanionEmotionState
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.CompanionOverlayStyle
import me.rerere.rikkahub.data.model.CompanionPixelPetSkin
import me.rerere.rikkahub.data.model.resolvedCompanionOverlayStyle
import kotlin.uuid.Uuid

/**
 * 陪伴悬浮层状态（渲染器只读）。
 */
data class CompanionPetState(
    val visible: Boolean = false,
    val assistantName: String = "",
    val avatar: Avatar = Avatar.Dummy,
    val emotion: CompanionEmotionState = CompanionEmotionState.CALM,
    val statusText: String = "",
    /** 短暂气泡文案（主动找人时展示，已截断） */
    val bubbleText: String = "",
    val conversationId: Uuid? = null,
    val overlayStyle: CompanionOverlayStyle = CompanionOverlayStyle.AVATAR,
    val pixelPetSkin: CompanionPixelPetSkin = CompanionPixelPetSkin.PINK,
)

/** 角色卡 avatar > 助手 avatar */
fun resolvePetAvatar(assistant: Assistant): Avatar {
    val raw = assistant.companionCharacter?.avatar?.trim().orEmpty()
    if (raw.isNotEmpty()) {
        return when {
            raw.startsWith("http://") ||
                raw.startsWith("https://") ||
                raw.startsWith("file:") ||
                raw.startsWith("content:") ||
                raw.startsWith("/") -> Avatar.Image(raw)
            raw.length <= 8 && !raw.contains('/') && !raw.contains('.') -> Avatar.Emoji(raw)
            else -> Avatar.Image(raw)
        }
    }
    return assistant.avatar
}

fun resolveOverlayStyle(assistant: Assistant): CompanionOverlayStyle =
    assistant.resolvedCompanionOverlayStyle()

fun resolvePixelPetSkin(assistant: Assistant): CompanionPixelPetSkin =
    assistant.companionPixelPetSkin
