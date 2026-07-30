package me.rerere.rikkahub.data.companion.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.uuid.Uuid

sealed interface ConversationEvent {
    data class UserInactive(
        val idleMillis: Long,
    ) : ConversationEvent

    data object UserSharedHappyMoment : ConversationEvent

    data object UserSharedExcitingMoment : ConversationEvent

    data object UserAskedForCare : ConversationEvent

    data object UserSharedSadMoment : ConversationEvent

    data object UserExpressedStress : ConversationEvent

    data object UserRepliedNormally : ConversationEvent
}

class EmotionProcessor(
    private val companionStateManager: CompanionStateManager,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    fun processAsync(
        conversationId: Uuid,
        event: ConversationEvent,
    ): Job {
        return scope.launch(Dispatchers.Default) {
            companionStateManager.updateState(conversationId) { state ->
                val nextEmotion = applyRule(
                    current = state.emotion,
                    event = event,
                )
                state.copy(
                    emotion = nextEmotion,
                    // Keep legacy string field aligned for compatibility.
                    emotionState = nextEmotion.emotionType.name.lowercase(),
                )
            }
        }
    }

    fun previewTransition(
        current: EmotionState,
        event: ConversationEvent,
    ): EmotionState {
        return applyRule(current, event)
    }

    private fun applyRule(
        current: EmotionState,
        event: ConversationEvent,
    ): EmotionState {
        val updatedAt = clock()
        return when (event) {
            is ConversationEvent.UserInactive -> {
                if (event.idleMillis >= LONELY_IDLE_THRESHOLD_MILLIS) {
                    evolve(
                        current = current,
                        targetType = EmotionType.LONELY,
                        delta = 0.25f,
                        updatedAt = updatedAt,
                    )
                } else {
                    decay(current, updatedAt)
                }
            }

            ConversationEvent.UserSharedHappyMoment -> evolve(
                current = current,
                targetType = EmotionType.HAPPY,
                delta = 0.20f,
                updatedAt = updatedAt,
            )

            ConversationEvent.UserSharedExcitingMoment -> evolve(
                current = current,
                targetType = EmotionType.EXCITED,
                delta = 0.30f,
                updatedAt = updatedAt,
            )

            ConversationEvent.UserAskedForCare -> evolve(
                current = current,
                targetType = EmotionType.CARING,
                delta = 0.25f,
                updatedAt = updatedAt,
            )

            ConversationEvent.UserSharedSadMoment -> evolve(
                current = current,
                targetType = EmotionType.SAD,
                delta = 0.30f,
                updatedAt = updatedAt,
            )

            ConversationEvent.UserExpressedStress -> evolve(
                current = current,
                targetType = EmotionType.CONCERNED,
                delta = 0.35f,
                updatedAt = updatedAt,
            )

            ConversationEvent.UserRepliedNormally -> decay(current, updatedAt)
        }
    }

    private fun evolve(
        current: EmotionState,
        targetType: EmotionType,
        delta: Float,
        updatedAt: Long,
    ): EmotionState {
        val nextIntensity = if (current.emotionType == targetType) {
            clamp(current.emotionIntensity + delta)
        } else {
            clamp(max(BASE_SWITCH_INTENSITY, current.emotionIntensity * SWITCH_DECAY_FACTOR + delta * 0.5f))
        }
        return EmotionState(
            emotionType = targetType,
            emotionIntensity = nextIntensity,
            lastUpdatedTime = updatedAt,
        )
    }

    private fun decay(
        current: EmotionState,
        updatedAt: Long,
    ): EmotionState {
        val nextIntensity = clamp(current.emotionIntensity - NATURAL_DECAY_STEP)
        val nextType = if (nextIntensity <= NEUTRAL_INTENSITY_THRESHOLD) {
            EmotionType.NEUTRAL
        } else {
            current.emotionType
        }
        return EmotionState(
            emotionType = nextType,
            emotionIntensity = nextIntensity,
            lastUpdatedTime = updatedAt,
        )
    }

    private fun clamp(value: Float): Float = min(1f, max(0f, value))

    private companion object {
        const val LONELY_IDLE_THRESHOLD_MILLIS = 30 * 60 * 1000L
        const val BASE_SWITCH_INTENSITY = 0.18f
        const val SWITCH_DECAY_FACTOR = 0.45f
        const val NATURAL_DECAY_STEP = 0.08f
        const val NEUTRAL_INTENSITY_THRESHOLD = 0.10f
    }
}
