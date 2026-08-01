package me.rerere.rikkahub.data.companion

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.companion.model.CompanionEmotionState
import me.rerere.rikkahub.data.companion.model.CompanionRelationshipStage
import me.rerere.rikkahub.data.companion.model.CompanionState
import me.rerere.rikkahub.data.companion.model.InteractionSuggestion
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.needsCompanionForegroundService
import me.rerere.rikkahub.data.model.Conversation
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.time.Clock
import kotlin.uuid.Uuid

data class ProactiveSession(
    val conversation: Conversation,
    val state: CompanionState,
)

class ProactiveTriggerManager(
    private val clock: Clock = Clock.System,
) {
    private val lastSuggestedAtMillis = ConcurrentHashMap<String, Long>()
    private val lastAnniversaryDay = ConcurrentHashMap<String, Long>()
    private val lastStageCache = ConcurrentHashMap<String, CompanionRelationshipStage>()

    suspend fun evaluateSuggestion(
        settings: Settings,
        conversation: Conversation,
        state: CompanionState,
    ): InteractionSuggestion? {
        if (!settings.needsCompanionForegroundService()) return null
        // Per-conversation suggestions still honor the conversation's assistant flag via caller;
        // here we only gate on "any proactive path enabled".
        if (!settings.companionAssist.proactiveChatEnabled &&
            settings.assistants.none { it.proactiveChatEnabled }
        ) {
            return null
        }

        val conversationKey = conversation.id.toString()
        val now = clock.now().toEpochMilliseconds()
        val cooldownMs = settings.companionAssist.proactiveCooldownMinutes
            .coerceAtLeast(30) * 60_000L
        val lastAt = lastSuggestedAtMillis[conversationKey] ?: 0L
        if (now - lastAt < cooldownMs) {
            rememberStage(conversationKey, state.relationshipState.relationshipStage)
            return null
        }

        val candidates = buildList {
            inactivitySuggestion(settings, conversation, now)?.let(::add)
            anniversarySuggestion(conversation, now)?.let(::add)
            relationshipShiftSuggestion(conversationKey, state, now)?.let(::add)
            emotionMissSuggestion(settings, conversation, state, now)?.let(::add)
        }
        if (candidates.isEmpty()) return null

        val selected = candidates.maxByOrNull { it.priority } ?: return null
        lastSuggestedAtMillis[conversationKey] = now
        rememberStage(conversationKey, state.relationshipState.relationshipStage)
        return selected
    }

    fun evaluateSuggestionAsync(
        scope: CoroutineScope,
        settings: Settings,
        conversation: Conversation,
        state: CompanionState,
        onSuggestion: (InteractionSuggestion) -> Unit,
    ): Job {
        return scope.launch(Dispatchers.Default) {
            evaluateSuggestion(settings, conversation, state)?.let(onSuggestion)
        }
    }

    fun startLowFrequencyTask(
        scope: CoroutineScope,
        settingsProvider: suspend () -> Settings,
        sessionProvider: suspend () -> List<ProactiveSession>,
        onSuggestion: suspend (conversationId: Uuid, suggestion: InteractionSuggestion) -> Unit,
        loopIntervalMinutes: Int = DEFAULT_LOOP_INTERVAL_MINUTES,
    ): Job {
        val intervalMs = max(loopIntervalMinutes, MIN_LOOP_INTERVAL_MINUTES) * 60_000L
        return scope.launch(Dispatchers.Default) {
            while (isActive) {
                runCatching {
                    val settings = settingsProvider()
                    if (!settings.needsCompanionForegroundService()) return@runCatching
                    if (!settings.companionAssist.proactiveChatEnabled &&
                        settings.assistants.none { it.proactiveChatEnabled }
                    ) {
                        return@runCatching
                    }
                    val sessions = sessionProvider()
                    sessions.forEach { session ->
                        val suggestion = evaluateSuggestion(
                            settings = settings,
                            conversation = session.conversation,
                            state = session.state,
                        )
                        if (suggestion != null) {
                            onSuggestion(session.conversation.id, suggestion)
                        }
                    }
                }
                delay(intervalMs)
            }
        }
    }

    private fun inactivitySuggestion(
        settings: Settings,
        conversation: Conversation,
        now: Long,
    ): InteractionSuggestion? {
        val silenceThresholdMs = settings.companionAssist.effectiveSilenceThresholdMs()
        val lastActiveAt = conversation.updateAt.toEpochMilli()
        if (now - lastActiveAt < silenceThresholdMs) return null
        return InteractionSuggestion(
            type = "check_in",
            priority = 0.50f,
            messageContext = "想关心用户，轻声问候近况",
            reason = "user_inactive",
            createdAtEpochMillis = now,
        )
    }

    private fun anniversarySuggestion(
        conversation: Conversation,
        now: Long,
    ): InteractionSuggestion? {
        val ageDays = ((now - conversation.createAt.toEpochMilli()) / DAY_MS).coerceAtLeast(0L)
        if (ageDays !in IMPORTANT_DAYS) return null
        val key = conversation.id.toString()
        if (lastAnniversaryDay[key] == ageDays) return null
        lastAnniversaryDay[key] = ageDays
        return InteractionSuggestion(
            type = "anniversary",
            priority = 0.62f,
            messageContext = "适合纪念节点的温和问候",
            reason = "important_milestone_day_$ageDays",
            createdAtEpochMillis = now,
        )
    }

    private fun relationshipShiftSuggestion(
        conversationKey: String,
        state: CompanionState,
        now: Long,
    ): InteractionSuggestion? {
        val currentStage = state.relationshipState.relationshipStage
        val previousStage = lastStageCache[conversationKey]
        if (previousStage == null || previousStage == currentStage) {
            return null
        }
        return InteractionSuggestion(
            type = "relationship_shift",
            priority = 0.57f,
            messageContext = "关系阶段发生变化，可做一次自然的情感确认",
            reason = "relationship_${previousStage.name.lowercase()}_to_${currentStage.name.lowercase()}",
            createdAtEpochMillis = now,
        )
    }

    /**
     * 情绪想念：WARM / CONCERNED 且沉默过半阈值时，优先于普通 inactivity。
     */
    private fun emotionMissSuggestion(
        settings: Settings,
        conversation: Conversation,
        state: CompanionState,
        now: Long,
    ): InteractionSuggestion? {
        val emotion = state.relationshipState.emotionState
        if (emotion != CompanionEmotionState.WARM && emotion != CompanionEmotionState.CONCERNED) {
            return null
        }
        val assist = settings.companionAssist
        val halfSilenceMs = assist.effectiveSilenceThresholdMs() / 2
        val lastActiveAt = conversation.updateAt.toEpochMilli()
        if (now - lastActiveAt < halfSilenceMs) return null
        val priority = if (emotion == CompanionEmotionState.CONCERNED) 0.72f else 0.58f
        return InteractionSuggestion(
            type = "emotion_miss",
            priority = priority,
            messageContext = if (emotion == CompanionEmotionState.CONCERNED) {
                "情绪偏担忧，想确认用户是否安好"
            } else {
                "情绪偏温暖，有点想念用户"
            },
            reason = "emotion_${emotion.name.lowercase()}_miss",
            createdAtEpochMillis = now,
        )
    }

    private fun rememberStage(
        conversationKey: String,
        stage: CompanionRelationshipStage,
    ) {
        lastStageCache[conversationKey] = stage
    }

    private companion object {
        const val DEFAULT_LOOP_INTERVAL_MINUTES = 15
        const val MIN_LOOP_INTERVAL_MINUTES = 10
        const val DAY_MS = 24 * 60 * 60 * 1000L
        val IMPORTANT_DAYS = setOf(7L, 30L, 100L, 365L)
    }
}
