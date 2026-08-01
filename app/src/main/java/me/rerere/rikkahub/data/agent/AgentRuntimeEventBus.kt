package me.rerere.rikkahub.data.agent

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event bus for AgentRuntime task lifecycle (TaskBall + Chat status).
 */
class AgentRuntimeEventBus {
    private val _events = MutableSharedFlow<AgentRuntimeEvent>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    val events: SharedFlow<AgentRuntimeEvent> = _events.asSharedFlow()

    suspend fun emit(event: AgentRuntimeEvent) {
        _events.emit(event)
    }

    fun tryEmit(event: AgentRuntimeEvent): Boolean = _events.tryEmit(event)
}
