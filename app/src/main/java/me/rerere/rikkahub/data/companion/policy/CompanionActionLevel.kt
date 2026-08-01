package me.rerere.rikkahub.data.companion.policy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 陪伴模式可主动采取的动作上限（不在前台服务里跑完整 LLM tool-loop）。
 *
 * - [MESSAGE_ONLY]：通知文案 / 主动消息（政策侧多走 SoftNotify）
 * - [SOFT_TOOLS]：另可选 `notify_user` / `open_solace`，读前台 App 作决策输入
 * - [DEVICE_TOOLS]：重度使用关怀时可 RULE 回桌面，再 open_solace（需手机控制+无障碍）
 */
@Serializable
enum class CompanionActionLevel {
    /** 仅主动消息 / 通知文案 */
    @SerialName("message_only")
    MESSAGE_ONLY,

    /** 另允许轻量动作：通知、打开 Solace、读前台 App */
    @SerialName("soft_tools")
    SOFT_TOOLS,

    /** 允许白名单设备动作（回到桌面），仍不跑 LLM tool-loop */
    @SerialName("device_tools")
    DEVICE_TOOLS,
}
