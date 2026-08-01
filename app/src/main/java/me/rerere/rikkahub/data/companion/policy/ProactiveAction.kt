package me.rerere.rikkahub.data.companion.policy

import me.rerere.rikkahub.data.companion.model.CompanionEmotionState
import me.rerere.rikkahub.data.device.ProactiveChatReason

/**
 * 陪伴政策引擎给出的下一步动作。
 *
 * 由 [me.rerere.rikkahub.data.companion.CompanionSoftActions] 执行：
 * - [SoftNotify] ↔ `notify_user`
 * - [OpenSolace] ↔ `open_solace`
 * - [DeviceTask] ↔ AgentManager RULE（白名单目标，如回到桌面）
 * 前台 App 读取是政策输入（UsageStats），不是输出动作。
 * 不在此层调 LLM tool-loop。
 */
sealed class ProactiveAction {
    data object None : ProactiveAction()

    /**
     * 写入会话并拉起 Solace（对应 DeviceAssist `open_solace`）。
     * 可选 LLM 只生成文案，不跑工具循环。
     */
    data class OpenSolace(
        val reason: ProactiveChatReason?,
        val emotion: CompanionEmotionState,
        val title: String,
        val useFullScreenIntent: Boolean,
        val isUsageCare: Boolean = false,
        val appName: String? = null,
        val packageName: String? = null,
        val continuousMinutes: Long = 0L,
    ) : ProactiveAction()

    /** 轻量通知，不强制打开会话（对应 DeviceAssist `notify_user`） */
    data class SoftNotify(
        val title: String,
        val content: String,
        val emotion: CompanionEmotionState,
    ) : ProactiveAction()

    /**
     * 设备控制（Phase D）：先执行白名单 RULE 目标，再执行 [followUp]。
     * 失败 / 门禁未过时降级为仅 [followUp]。
     */
    data class DeviceTask(
        val goal: String,
        val emotion: CompanionEmotionState,
        val followUp: ProactiveAction,
    ) : ProactiveAction()
}
