package me.rerere.rikkahub.data.accessibility

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event bus between Accessibility perception and Agent Runtime.
 *
 * Flow: AccessibilityService → AccessibilityEventManager → AgentEventBus → Agent Runtime
 */
class AgentEventBus {
    private val _events = MutableSharedFlow<AgentEvent>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    suspend fun emit(event: AgentEvent) {
        _events.emit(event)
    }

    /**
     * Non-suspending emit for the AccessibilityService callback thread.
     * Drops when the buffer is full (high-frequency CONTENT_CHANGED).
     */
    fun tryEmit(event: AgentEvent): Boolean = _events.tryEmit(event)
}
