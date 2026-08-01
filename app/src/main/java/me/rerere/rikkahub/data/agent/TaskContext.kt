package me.rerere.rikkahub.data.agent

import me.rerere.rikkahub.data.accessibility.UnifiedObservation

/**
 * Input for [TaskPlanner] — kept intentionally small for fast local planning.
 * Avoid embedding full UI trees when rules/cache can decide alone.
 */
data class TaskContext(
    val goal: String,
    /** Compact page fingerprint, e.g. `package|activity|step`. */
    val currentState: String,
    val availableActions: List<String> = DEFAULT_ACTIONS,
    val observation: UnifiedObservation? = null,
    val taskState: TaskState? = null,
    /** When false, never invoke LLM (local rules/cache/history only). */
    val allowLlm: Boolean = true,
) {
    companion object {
        val DEFAULT_ACTIONS: List<String> = listOf(
            AgentAction.OPEN_APP,
            AgentAction.CLICK_NODE,
            AgentAction.CLICK_XY,
            AgentAction.TYPE_TEXT,
            AgentAction.SWIPE,
            AgentAction.GLOBAL,
            AgentAction.WAIT_FOR_TEXT,
            AgentAction.WAIT_FOR_PAGE,
            AgentAction.SEE_SCREEN,
            AgentAction.DUMP_UI,
            AgentAction.DONE,
            AgentAction.FAIL,
        )

        fun pageKey(packageName: String, page: String): String =
            "${packageName.trim()}|${page.trim()}"

        fun of(
            goal: String,
            state: TaskState,
            observation: UnifiedObservation? = null,
            allowLlm: Boolean = true,
        ): TaskContext {
            val pkg = observation?.packageName?.ifBlank { state.packageName } ?: state.packageName
            val page = observation?.page?.ifBlank { state.currentPage } ?: state.currentPage
            return TaskContext(
                goal = goal,
                currentState = "${pageKey(pkg, page)}|step=${state.currentStep}",
                observation = observation,
                taskState = state,
                allowLlm = allowLlm,
            )
        }
    }
}

/**
 * Lightweight task planner: local rules → history → (optional) LLM.
 * Must run off the main thread when LLM is involved.
 */
interface TaskPlanner {
    suspend fun plan(context: TaskContext): ActionPlan
}

/**
 * Optional LLM backend for complex goals only.
 * Implementations MUST use background dispatchers (IO/Default), never Main.
 */
interface LlmTaskPlanner {
    val isAvailable: Boolean

    /**
     * Heavy planning. Caller must invoke from a non-Main context.
     */
    suspend fun planComplex(context: TaskContext): ActionPlan
}

/** Placeholder until GPT/Claude/DeepSeek is wired. */
class NoOpLlmTaskPlanner : LlmTaskPlanner {
    override val isAvailable: Boolean = false
    override suspend fun planComplex(context: TaskContext): ActionPlan = ActionPlan(
        actions = emptyList(),
        reasoning = "LLM planner not configured",
        done = false,
    )
}
