package me.rerere.rikkahub.data.companion.policy

import me.rerere.rikkahub.data.companion.model.CompanionEmotionState
import me.rerere.rikkahub.data.companion.model.InteractionSuggestion
import me.rerere.rikkahub.data.device.ProactiveChatReason

/**
 * 纯决策：根据情绪、触发原因与动作上限选择下一步。
 * 不访问网络、不写库 —— 便于单测与在 FGS 里低功耗调用。
 */
object CompanionProactivePolicy {

    fun decideUsageCare(
        emotion: CompanionEmotionState,
        actionLevel: CompanionActionLevel,
        appName: String,
        packageName: String,
        continuousMinutes: Long,
        hourOfDay: Int = -1,
        quietHourStart: Int = 0,
        quietHourEnd: Int = 6,
        severeContinuousMinutes: Long = SEVERE_CONTINUOUS_MINUTES_DEFAULT,
    ): ProactiveAction {
        val title = "找你 · $appName"
        val severe = emotion == CompanionEmotionState.CONCERNED ||
            continuousMinutes >= severeContinuousMinutes
        val quietHours = isQuietHours(hourOfDay, quietHourStart, quietHourEnd)

        if (actionLevel == CompanionActionLevel.MESSAGE_ONLY) {
            return ProactiveAction.SoftNotify(
                title = title,
                content = usageCareNotifyFallback(emotion, appName, continuousMinutes),
                emotion = emotion,
            )
        }

        val preferNotify = !severe ||
            (quietHours && emotion != CompanionEmotionState.CONCERNED)
        if (preferNotify) {
            return ProactiveAction.SoftNotify(
                title = title,
                content = usageCareNotifyFallback(emotion, appName, continuousMinutes),
                emotion = emotion,
            )
        }

        val openSolace = ProactiveAction.OpenSolace(
            reason = null,
            emotion = emotion,
            title = title,
            useFullScreenIntent = !quietHours,
            isUsageCare = true,
            appName = appName,
            packageName = packageName,
            continuousMinutes = continuousMinutes,
        )

        if (actionLevel >= CompanionActionLevel.DEVICE_TOOLS && !quietHours) {
            return ProactiveAction.DeviceTask(
                goal = CompanionDeviceGoals.GO_HOME,
                emotion = emotion,
                followUp = openSolace,
            )
        }

        return openSolace
    }

    fun decideProactiveChat(
        reason: ProactiveChatReason,
        emotion: CompanionEmotionState,
        actionLevel: CompanionActionLevel,
        hourOfDay: Int = -1,
        quietHourStart: Int = 0,
        quietHourEnd: Int = 6,
    ): ProactiveAction {
        val title = titleForReason(reason)
        val softTools = actionLevel >= CompanionActionLevel.SOFT_TOOLS
        val quietHours = isQuietHours(hourOfDay, quietHourStart, quietHourEnd)

        if (reason == ProactiveChatReason.SILENCE &&
            emotion == CompanionEmotionState.CONCERNED &&
            softTools
        ) {
            return ProactiveAction.SoftNotify(
                title = title,
                content = softNotifyFallback(emotion, reason),
                emotion = emotion,
            )
        }

        if (reason == ProactiveChatReason.EVENING && softTools &&
            (emotion != CompanionEmotionState.CONCERNED || quietHours)
        ) {
            return ProactiveAction.SoftNotify(
                title = title,
                content = softNotifyFallback(emotion, reason),
                emotion = emotion,
            )
        }

        // 情绪/关系建议：默认轻通知，关切且非夜间可拉起
        if (reason == ProactiveChatReason.CHECK_IN ||
            reason == ProactiveChatReason.ANNIVERSARY ||
            reason == ProactiveChatReason.RELATIONSHIP_SHIFT
        ) {
            if (softTools && (emotion != CompanionEmotionState.CONCERNED || quietHours)) {
                return ProactiveAction.SoftNotify(
                    title = title,
                    content = softNotifyFallback(emotion, reason),
                    emotion = emotion,
                )
            }
            if (quietHours) {
                return ProactiveAction.SoftNotify(
                    title = title,
                    content = softNotifyFallback(emotion, reason),
                    emotion = emotion,
                )
            }
        }

        if (reason == ProactiveChatReason.SILENCE && quietHours) {
            return ProactiveAction.SoftNotify(
                title = title,
                content = softNotifyFallback(emotion, reason),
                emotion = emotion,
            )
        }

        return ProactiveAction.OpenSolace(
            reason = reason,
            emotion = emotion,
            title = title,
            useFullScreenIntent = false,
            isUsageCare = false,
        )
    }

    fun decideFromSuggestion(
        suggestion: InteractionSuggestion,
        emotion: CompanionEmotionState,
        actionLevel: CompanionActionLevel,
        hourOfDay: Int = -1,
        quietHourStart: Int = 0,
        quietHourEnd: Int = 6,
    ): ProactiveAction {
        val reason = reasonFromSuggestion(suggestion)
        return decideProactiveChat(
            reason = reason,
            emotion = emotion,
            actionLevel = actionLevel,
            hourOfDay = hourOfDay,
            quietHourStart = quietHourStart,
            quietHourEnd = quietHourEnd,
        )
    }

    fun reasonFromSuggestion(suggestion: InteractionSuggestion): ProactiveChatReason =
        when (suggestion.type) {
            "anniversary" -> ProactiveChatReason.ANNIVERSARY
            "relationship_shift" -> ProactiveChatReason.RELATIONSHIP_SHIFT
            "emotion_miss", "check_in" -> ProactiveChatReason.CHECK_IN
            else -> ProactiveChatReason.CHECK_IN
        }

    fun emotionToneHint(emotion: CompanionEmotionState): String = when (emotion) {
        CompanionEmotionState.CALM -> "当前情绪偏平静，语气平稳自然，不要过度热情。"
        CompanionEmotionState.WARM -> "当前情绪偏温暖，语气柔软亲近，可以稍微多一点关心。"
        CompanionEmotionState.PLAYFUL -> "当前情绪偏俏皮，可以轻松一点、带点玩笑，但仍要尊重用户。"
        CompanionEmotionState.CONCERNED -> "当前情绪偏担忧，语气认真关切，优先关心用户状态，不要卖萌。"
    }

    /**
     * @param startHour 含
     * @param endHour 含；若 start<=end 则闭区间；若 start>end 则跨午夜（如 22–6）
     */
    fun isQuietHours(
        hourOfDay: Int,
        startHour: Int = 0,
        endHour: Int = 6,
    ): Boolean {
        if (hourOfDay !in 0..23) return false
        val start = startHour.coerceIn(0, 23)
        val end = endHour.coerceIn(0, 23)
        return if (start <= end) {
            hourOfDay in start..end
        } else {
            hourOfDay >= start || hourOfDay <= end
        }
    }

    fun titleForReason(reason: ProactiveChatReason): String = when (reason) {
        ProactiveChatReason.MORNING -> "早安"
        ProactiveChatReason.EVENING -> "晚上了"
        ProactiveChatReason.SILENCE -> "想找你"
        ProactiveChatReason.CHECK_IN -> "想你了"
        ProactiveChatReason.ANNIVERSARY -> "今天有点特别"
        ProactiveChatReason.RELATIONSHIP_SHIFT -> "关于我们"
    }

    private const val SEVERE_CONTINUOUS_MINUTES_DEFAULT = 60L

    private fun usageCareNotifyFallback(
        emotion: CompanionEmotionState,
        appName: String,
        continuousMinutes: Long,
    ): String = when (emotion) {
        CompanionEmotionState.CONCERNED ->
            "还在「$appName」吗？我有点想你了，回我一声好不好。"
        CompanionEmotionState.PLAYFUL ->
            "「$appName」都霸占你这么久了，轮到我了吧？"
        CompanionEmotionState.WARM ->
            "你去「$appName」好久了……有点想听你说话。"
        CompanionEmotionState.CALM ->
            "嗨，你在「$appName」待了挺久。我想你了，来聊两句？"
    }

    private fun softNotifyFallback(
        emotion: CompanionEmotionState,
        reason: ProactiveChatReason,
    ): String = when (reason) {
        ProactiveChatReason.SILENCE, ProactiveChatReason.CHECK_IN -> when (emotion) {
            CompanionEmotionState.CONCERNED -> "好久没见你，我有点慌。你还在吗？"
            CompanionEmotionState.WARM -> "想你了，有空回我一下？"
            CompanionEmotionState.PLAYFUL -> "人呢？冒个泡呗。"
            CompanionEmotionState.CALM -> "想找你说说话，有空吗？"
        }
        ProactiveChatReason.MORNING -> "早。我想起你了。"
        ProactiveChatReason.EVENING -> "晚上了，想听听你今天怎么样。"
        ProactiveChatReason.ANNIVERSARY -> "今天对我来说有点特别，想跟你待一会儿。"
        ProactiveChatReason.RELATIONSHIP_SHIFT -> "感觉我们又近了一点。想听听你。"
    }
}
