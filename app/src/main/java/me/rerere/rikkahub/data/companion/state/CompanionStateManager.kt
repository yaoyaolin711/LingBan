package me.rerere.rikkahub.data.companion.state

import kotlin.uuid.Uuid

class CompanionStateManager(
    private val repository: StateRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun getState(conversationId: Uuid): CompanionState {
        return repository.get(conversationId)
    }

    suspend fun setState(
        conversationId: Uuid,
        state: CompanionState,
    ): CompanionState {
        val currentState = repository.get(conversationId)
        val nextState = normalizeForWrite(
            candidate = state,
            currentVersion = currentState.version,
        )
        repository.save(conversationId, nextState)
        return nextState
    }

    suspend fun updateState(
        conversationId: Uuid,
        transform: (CompanionState) -> CompanionState,
    ): CompanionState {
        val currentState = repository.get(conversationId)
        val transformed = transform(currentState)
        val nextState = normalizeForWrite(
            candidate = transformed,
            currentVersion = currentState.version,
        )
        repository.save(conversationId, nextState)
        return nextState
    }

    suspend fun resetState(conversationId: Uuid) {
        repository.delete(conversationId)
    }

    private fun normalizeForWrite(
        candidate: CompanionState,
        currentVersion: Int,
    ): CompanionState {
        val currentMillis = clock()
        val nextVersion = (currentVersion + 1).coerceAtLeast(candidate.version + 1)
        return candidate.copy(
            version = nextVersion,
            updatedAtEpochMillis = currentMillis,
        )
    }
}
