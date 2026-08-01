package me.rerere.rikkahub.data.agent

import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.accessibility.UISnapshot

/**
 * Outcome of post-action verification.
 *
 * ActionExecutor → execute → ActionVerifier → SUCCESS | FAILED | RETRY
 */
@Serializable
enum class VerificationStatus {
    /** Action side-effects look correct — continue the task loop. */
    SUCCESS,
    /** Action failed verification and should not be retried. */
    FAILED,
    /** Transient miss — Runtime may re-execute the same action. */
    RETRY,
}

@Serializable
data class CheckResult(
    val name: String,
    val passed: Boolean,
    val detail: String = "",
)

@Serializable
data class ActionVerification(
    val status: VerificationStatus,
    val message: String = "",
    val checks: List<CheckResult> = emptyList(),
    val attempt: Int = 1,
) {
    val ok: Boolean get() = status == VerificationStatus.SUCCESS
}

/**
 * Inputs for [ActionVerifier]: before/after snapshots + execute payload.
 */
data class VerifyContext(
    val goal: String,
    val action: AgentAction,
    val executeResult: ActionExecuteResult,
    val before: UISnapshot,
    val after: UISnapshot,
    val attempt: Int,
    val maxRetries: Int,
)

/**
 * Post-action verification — plugged into [AgentRuntime], not into the executor.
 */
interface ActionVerifier {
    suspend fun verify(ctx: VerifyContext): ActionVerification

    /** Per-action retry budget (Runtime enforces). Default 2. */
    fun maxRetriesFor(action: AgentAction): Int {
        val fromParams = action.params["max_retries"]?.toIntOrNull()
        return (fromParams ?: DEFAULT_MAX_RETRIES).coerceIn(0, 5)
    }

    companion object {
        const val DEFAULT_MAX_RETRIES = 2
    }
}
