package me.rerere.rikkahub.data.agent

import java.util.LinkedHashMap

/**
 * In-memory LRU cache for ActionPlans.
 * Key = normalized goal + page state — avoid re-planning "打开微信" etc.
 */
class PlanCache(
    private val maxSize: Int = 64,
) {
    private val lock = Any()
    private val map = object : LinkedHashMap<String, CacheEntry>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean =
            size > maxSize
    }

    data class CacheEntry(
        val plan: ActionPlan,
        val createdAt: Long = System.currentTimeMillis(),
    )

    fun key(goal: String, currentState: String): String {
        val g = normalizeGoal(goal)
        // Drop step suffix so same page reuses plan across ticks.
        val page = currentState.substringBefore("|step=")
        return "$g::$page"
    }

    fun get(goal: String, currentState: String): ActionPlan? = synchronized(lock) {
        map[key(goal, currentState)]?.plan
    }

    fun put(goal: String, currentState: String, plan: ActionPlan) = synchronized(lock) {
        if (plan.actions.isEmpty() && !plan.done) return
        // Don't cache FAIL / empty LLM stubs
        if (plan.actions.any { it.action == AgentAction.FAIL }) return
        map[key(goal, currentState)] = CacheEntry(plan)
    }

    fun recordSuccessPlan(goal: String, currentState: String, actions: List<AgentAction>) {
        if (actions.isEmpty()) return
        put(
            goal,
            currentState,
            ActionPlan(
                actions = actions,
                reasoning = "cached_success",
            ),
        )
    }

    fun clear() = synchronized(lock) { map.clear() }

    fun size(): Int = synchronized(lock) { map.size }

    companion object {
        fun normalizeGoal(goal: String): String =
            goal.trim().lowercase()
                .replace(Regex("\\s+"), "")
                .replace("，", ",")
                .replace("。", "")
    }
}

/**
 * Remembers successful single actions for goal+page (replay before LLM).
 */
class HistoryActionStore(
    private val maxSize: Int = 128,
) {
    private val lock = Any()
    private val map = object : LinkedHashMap<String, AgentAction>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AgentAction>?): Boolean =
            size > maxSize
    }

    private fun key(goal: String, pageKey: String): String =
        "${PlanCache.normalizeGoal(goal)}::$pageKey"

    fun lookup(goal: String, pageKey: String): AgentAction? = synchronized(lock) {
        map[key(goal, pageKey)]
    }

    fun record(goal: String, pageKey: String, action: AgentAction) = synchronized(lock) {
        if (action.action == AgentAction.DONE || action.action == AgentAction.FAIL) return
        if (action.action == AgentAction.SEE_SCREEN || action.action == AgentAction.DUMP_UI) return
        map[key(goal, pageKey)] = action
    }
}
